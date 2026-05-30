package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.MainActivity
import com.example.R
import com.example.accessibility.SpatialAccessibilityService
import com.example.gesture.GestureType
import com.example.gesture.HandGestureAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class BubbleService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var bubbleRoot: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelOpen = false

    private var cameraPreviewRoot: View? = null
    private var cameraPreviewParams: WindowManager.LayoutParams? = null
    private var cameraPreviewContainer: FrameLayout? = null
    private var cameraPreviewView: PreviewView? = null
    private var cameraPreviewStatus: TextView? = null
    private var cameraPreviewToggle: ImageView? = null
    private var previewExpanded = true

    private var statusText: TextView? = null
    private var bubbleIcon: ImageView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var gestureAnalyzer: HandGestureAnalyzer? = null
    private var analysisExecutor: ExecutorService? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        showBubble()
        showCameraPreview()
        bindCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        gestureAnalyzer?.close()
        gestureAnalyzer = null
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
        removeOverlays()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body))
            .setSmallIcon(R.drawable.ic_hand_bubble)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop_bubble), stopIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val bubble = inflater.inflate(R.layout.overlay_bubble, null)
        bubbleIcon = bubble.findViewById(R.id.bubble_icon)
        statusText = bubble.findViewById(R.id.bubble_status)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 220
        }

        var dragStartX = 0
        var dragStartY = 0
        var paramStartX = 0
        var paramStartY = 0
        var dragging = false

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    dragStartX = event.rawX.toInt()
                    dragStartY = event.rawY.toInt()
                    paramStartX = params.x
                    paramStartY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - dragStartX
                    val dy = event.rawY.toInt() - dragStartY
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        dragging = true
                        params.x = paramStartX + dx
                        params.y = paramStartY + dy
                        windowManager.updateViewLayout(bubble, params)
                        updatePanelPosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) togglePanel()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        bubbleRoot = bubble
        bubbleParams = params
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCameraPreview() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val preview = inflater.inflate(R.layout.overlay_camera_preview, null)

        cameraPreviewRoot = preview
        cameraPreviewContainer = preview.findViewById(R.id.camera_preview_container)
        cameraPreviewView = preview.findViewById<PreviewView>(R.id.camera_preview_view).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        cameraPreviewStatus = preview.findViewById(R.id.camera_preview_status)
        cameraPreviewToggle = preview.findViewById(R.id.camera_preview_toggle)

        cameraPreviewToggle?.setOnClickListener { togglePreviewSize() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(PREVIEW_START_X_DP)
            y = dp(PREVIEW_START_Y_DP)
        }

        var dragStartX = 0
        var dragStartY = 0
        var paramStartX = 0
        var paramStartY = 0
        var dragging = false

        preview.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN &&
                isTouchOnView(cameraPreviewToggle, event.rawX, event.rawY)
            ) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    dragStartX = event.rawX.toInt()
                    dragStartY = event.rawY.toInt()
                    paramStartX = params.x
                    paramStartY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - dragStartX
                    val dy = event.rawY.toInt() - dragStartY
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        dragging = true
                        params.x = paramStartX + dx
                        params.y = paramStartY + dy
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> dragging
                else -> false
            }
        }

        applyPreviewSize(expanded = true, animate = false)
        windowManager.addView(preview, params)
        cameraPreviewParams = params
    }

    private fun togglePreviewSize() {
        previewExpanded = !previewExpanded
        applyPreviewSize(expanded = previewExpanded, animate = true)
        cameraPreviewRoot?.let { root ->
            cameraPreviewParams?.let { params ->
                windowManager.updateViewLayout(root, params)
            }
        }
    }

    private fun applyPreviewSize(expanded: Boolean, animate: Boolean) {
        val container = cameraPreviewContainer ?: return
        val widthDp = if (expanded) PREVIEW_EXPANDED_WIDTH_DP else PREVIEW_MIN_WIDTH_DP
        val heightDp = if (expanded) PREVIEW_EXPANDED_HEIGHT_DP else PREVIEW_MIN_HEIGHT_DP

        val widthPx = dp(widthDp)
        val heightPx = dp(heightDp)

        container.layoutParams = container.layoutParams.apply {
            width = widthPx
            height = heightPx
        }

        cameraPreviewToggle?.setImageResource(
            if (expanded) R.drawable.ic_preview_minimize else R.drawable.ic_preview_maximize
        )
        cameraPreviewToggle?.contentDescription = getString(
            if (expanded) R.string.camera_preview_minimize else R.string.camera_preview_maximize
        )

        if (animate) {
            container.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                container.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
        }

        container.requestLayout()
    }

    @SuppressLint("InflateParams")
    private fun togglePanel() {
        if (panelOpen) {
            panelView?.let { windowManager.removeView(it) }
            panelView = null
            panelParams = null
            panelOpen = false
            return
        }

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val panel = inflater.inflate(R.layout.overlay_settings_panel, null)
        panel.findViewById<TextView>(R.id.panel_stop).setOnClickListener { stopSelf() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(panel, params)
        panelView = panel
        panelParams = params
        panelOpen = true
        updatePanelPosition()
    }

    private fun updatePanelPosition() {
        val bubble = bubbleRoot ?: return
        val bubbleLayout = bubbleParams ?: return
        val panel = panelParams ?: return
        panel.x = bubbleLayout.x - 20
        panel.y = bubbleLayout.y + bubble.height + 12
        panelView?.let { windowManager.updateViewLayout(it, panel) }
    }

    private fun bindCamera() {
        val previewView = cameraPreviewView ?: return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val rotation = windowManager.defaultDisplay.rotation

                val portraitSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        AspectRatioStrategy(
                            AspectRatio.RATIO_16_9,
                            AspectRatioStrategy.FALLBACK_RULE_AUTO
                        )
                    )
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(portraitSelector)
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val analyzer = HandGestureAnalyzer(
                    applicationContext,
                    onGestureDetected = ::onGestureDetected,
                    onStatusUpdated = ::onTrackingUpdated,
                )
                gestureAnalyzer?.close()
                gestureAnalyzer = analyzer

                val executor = analysisExecutor ?: Executors.newSingleThreadExecutor().also {
                    analysisExecutor = it
                }


                val analysis = ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }

                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onGestureDetected(gesture: GestureType) {
        when (gesture) {
            GestureType.SCROLL_UP -> SpatialAccessibilityService.nextItem()
            GestureType.SCROLL_DOWN -> SpatialAccessibilityService.previousItem()
            GestureType.PLAY_PAUSE -> SpatialAccessibilityService.playPause()
        }
        val label = when (gesture) {
            GestureType.SCROLL_UP -> getString(R.string.gesture_next)
            GestureType.SCROLL_DOWN -> getString(R.string.gesture_previous)
            GestureType.PLAY_PAUSE -> getString(R.string.gesture_play_pause)
        }
        bubbleRoot?.post {
            statusText?.text = label
            cameraPreviewStatus?.text = label
            bubbleRoot?.postDelayed({
                statusText?.text = getString(R.string.bubble_running)
                cameraPreviewStatus?.text = getString(R.string.bubble_running)
            }, 900)
        }
    }

    private fun onTrackingUpdated(x: Float, y: Float, handConfidence: Float) {
        bubbleRoot?.post {
            val active = handConfidence > 0.5f
            bubbleIcon?.setBackgroundResource(
                if (active) R.drawable.bubble_background_active else R.drawable.bubble_background
            )
            val trackingLabel = if (active) {
                getString(R.string.hand_detected)
            } else {
                getString(R.string.bubble_running)
            }
            if (!panelOpen && statusText?.text == getString(R.string.bubble_running)) {
                statusText?.text = trackingLabel
            }
            if (cameraPreviewStatus?.text == getString(R.string.bubble_running) ||
                cameraPreviewStatus?.text == getString(R.string.hand_detected)
            ) {
                cameraPreviewStatus?.text = trackingLabel
            }
        }
    }

    private fun removeOverlays() {
        panelView?.let { windowManager.removeView(it) }
        cameraPreviewRoot?.let { windowManager.removeView(it) }
        bubbleRoot?.let { windowManager.removeView(it) }
        panelView = null
        cameraPreviewRoot = null
        bubbleRoot = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun isTouchOnView(view: View?, rawX: Float, rawY: Float): Boolean {
        view ?: return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + view.width &&
            rawY >= location[1] && rawY <= location[1] + view.height
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    companion object {
        private const val TAG = "BubbleService"
        const val ACTION_STOP = "com.example.action.STOP_BUBBLE"
        private const val CHANNEL_ID = "spatial_motion_bubble"
        private const val NOTIFICATION_ID = 1001

        private const val PREVIEW_START_X_DP = 16
        private const val PREVIEW_START_Y_DP = 120
        private const val PREVIEW_EXPANDED_WIDTH_DP = 120
        private const val PREVIEW_EXPANDED_HEIGHT_DP = 213
        private const val PREVIEW_MIN_WIDTH_DP = 64
        private const val PREVIEW_MIN_HEIGHT_DP = 114

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BubbleService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BubbleService::class.java))
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }
    }
}
