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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
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
import com.example.gesture.HandFrameUpdate
import com.example.gesture.HandGestureAnalyzer
import com.example.settings.BubblePreferences
import com.example.ui.HandSkeletonOverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

class BubbleService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var overlayRoot: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private var bubbleHeader: View? = null
    private var expandedPanel: View? = null
    private var bubbleMinimizeToggle: ImageView? = null
    private var cameraPreviewContainer: FrameLayout? = null
    private var cameraPreviewView: PreviewView? = null
    private var cameraPreviewStatus: TextView? = null
    private var cameraSkeletonOverlay: HandSkeletonOverlayView? = null
    private var cameraPreviewToggle: ImageView? = null
    private var cameraPreviewVisible = true
    private var bubbleMinimized = true

    private var statusText: TextView? = null
    private var bubbleIcon: ImageView? = null
    private var settingShowSkeleton: Switch? = null
    private var settingShowCamera: Switch? = null
    private var settingVibrate: Switch? = null
    private var settingMinimizeOnStart: Switch? = null
    private var settingCooldown: SeekBar? = null
    private var settingCooldownValue: TextView? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var gestureAnalyzer: HandGestureAnalyzer? = null
    private var analysisExecutor: ExecutorService? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        cameraPreviewVisible = BubblePreferences.isShowCameraOnStart(this)
        bubbleMinimized = BubblePreferences.isBubbleMinimized(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        showUnifiedOverlay()
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
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
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
    private fun showUnifiedOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val root = inflater.inflate(R.layout.overlay_unified_bubble, null) as LinearLayout

        overlayRoot = root
        bubbleHeader = root.findViewById(R.id.bubble_header)
        bubbleIcon = root.findViewById(R.id.bubble_icon)
        statusText = root.findViewById(R.id.bubble_status)
        expandedPanel = root.findViewById(R.id.expanded_panel)
        bubbleMinimizeToggle = root.findViewById(R.id.bubble_minimize_toggle)
        cameraPreviewContainer = root.findViewById(R.id.camera_preview_container)
        cameraPreviewView = root.findViewById<PreviewView>(R.id.camera_preview_view).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        cameraPreviewStatus = root.findViewById(R.id.camera_preview_status)
        cameraSkeletonOverlay = root.findViewById(R.id.camera_skeleton_overlay)
        cameraPreviewToggle = root.findViewById(R.id.camera_preview_toggle)

        settingShowSkeleton = root.findViewById(R.id.setting_show_skeleton)
        settingShowCamera = root.findViewById(R.id.setting_show_camera)
        settingVibrate = root.findViewById(R.id.setting_vibrate)
        settingMinimizeOnStart = root.findViewById(R.id.setting_minimize_on_start)
        settingCooldown = root.findViewById(R.id.setting_cooldown)
        settingCooldownValue = root.findViewById(R.id.setting_cooldown_value)

        root.findViewById<TextView>(R.id.panel_stop).setOnClickListener { stopSelf() }

        bubbleMinimizeToggle?.setOnClickListener { toggleBubbleMinimized() }
        cameraPreviewToggle?.setOnClickListener { toggleCameraPreviewVisibility() }

        bubbleIcon?.setOnClickListener {
            if (bubbleMinimized) {
                bubbleMinimized = false
                BubblePreferences.setBubbleMinimized(this, false)
                applyBubbleMinimizedState(animate = true)
            }
        }

        bindSettingsControls()
        applyBubbleMinimizedState(animate = false)
        applyCameraPreviewVisibility()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(OVERLAY_START_X_DP)
            y = dp(OVERLAY_START_Y_DP)
        }

        var dragStartX = 0
        var dragStartY = 0
        var paramStartX = 0
        var paramStartY = 0
        var dragging = false

        root.setOnTouchListener { view, event ->
            if (isTouchOnInteractiveChild(event.rawX, event.rawY)) {
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

        windowManager.addView(root, params)
        overlayParams = params
    }

    private fun toggleBubbleMinimized() {
        bubbleMinimized = !bubbleMinimized
        BubblePreferences.setBubbleMinimized(this, bubbleMinimized)
        applyBubbleMinimizedState(animate = true)
    }

    private fun applyBubbleMinimizedState(animate: Boolean) {
        val minimized = bubbleMinimized
        statusText?.visibility = if (minimized) View.GONE else View.VISIBLE
        expandedPanel?.visibility = if (minimized) View.GONE else View.VISIBLE

        val padding = if (minimized) dp(4) else dp(8)
        overlayRoot?.setPadding(padding, padding, padding, padding)

        bubbleMinimizeToggle?.setImageResource(
            if (minimized) R.drawable.ic_preview_maximize else R.drawable.ic_preview_minimize,
        )
        bubbleMinimizeToggle?.contentDescription = getString(
            if (minimized) R.string.bubble_restore else R.string.bubble_minimize,
        )

        if (animate) {
            bubbleIcon?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(80)?.withEndAction {
                bubbleIcon?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
            }?.start()
        }
    }

    private fun bindSettingsControls() {
        settingShowSkeleton?.isChecked = BubblePreferences.isShowSkeleton(this)
        settingShowCamera?.isChecked = BubblePreferences.isShowCameraOnStart(this)
        settingVibrate?.isChecked = BubblePreferences.isVibrateOnGesture(this)
        settingMinimizeOnStart?.isChecked = BubblePreferences.isBubbleMinimized(this)

        val cooldownMs = BubblePreferences.getGestureCooldownMs(this)
        settingCooldown?.progress = cooldownMsToProgress(cooldownMs)
        updateCooldownLabel(cooldownMs)

        settingShowSkeleton?.setOnCheckedChangeListener { _, checked ->
            BubblePreferences.setShowSkeleton(this, checked)
            applySkeletonVisibility()
        }

        settingShowCamera?.setOnCheckedChangeListener { _, checked ->
            BubblePreferences.setShowCameraOnStart(this, checked)
            if (checked && !cameraPreviewVisible) {
                cameraPreviewVisible = true
                applyCameraPreviewVisibility()
            }
        }

        settingVibrate?.setOnCheckedChangeListener { _, checked ->
            BubblePreferences.setVibrateOnGesture(this, checked)
        }

        settingMinimizeOnStart?.setOnCheckedChangeListener { _, checked ->
            BubblePreferences.setBubbleMinimized(this, checked)
        }

        settingCooldown?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val ms = progressToCooldownMs(progress)
                BubblePreferences.setGestureCooldownMs(this@BubbleService, ms)
                updateCooldownLabel(ms)
                gestureAnalyzer?.updateSettings(
                    ms,
                    BubblePreferences.getMinHandConfidence(this@BubbleService),
                )
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        gestureAnalyzer?.updateSettings(
            cooldownMs,
            BubblePreferences.getMinHandConfidence(this),
        )
    }

    private fun cooldownMsToProgress(ms: Long): Int =
        ((ms - 600L) / 100L).toInt().coerceIn(0, 19)

    private fun progressToCooldownMs(progress: Int): Long =
        600L + progress * 100L

    private fun updateCooldownLabel(ms: Long) {
        settingCooldownValue?.text = getString(R.string.setting_cooldown_value, ms / 1000f)
    }

    private fun toggleCameraPreviewVisibility() {
        cameraPreviewVisible = !cameraPreviewVisible
        applyCameraPreviewVisibility()
    }

    private fun applyCameraPreviewVisibility() {
        val visible = cameraPreviewVisible
        cameraPreviewContainer?.visibility = if (visible) View.VISIBLE else View.GONE
        cameraPreviewToggle?.setImageResource(
            if (visible) R.drawable.ic_preview_minimize else R.drawable.ic_preview_maximize,
        )
        cameraPreviewToggle?.contentDescription = getString(
            if (visible) R.string.camera_preview_hide else R.string.camera_preview_show,
        )
    }

    private fun applySkeletonVisibility() {
        val show = BubblePreferences.isShowSkeleton(this)
        cameraSkeletonOverlay?.visibility = if (show) View.VISIBLE else View.INVISIBLE
        if (!show) {
            cameraSkeletonOverlay?.clearLandmarks()
        }
    }

    private fun isTouchOnInteractiveChild(rawX: Float, rawY: Float): Boolean {
        if (bubbleMinimized) {
            return isTouchOnView(bubbleIcon, rawX, rawY) ||
                isTouchOnView(bubbleMinimizeToggle, rawX, rawY)
        }
        val interactive = listOfNotNull(
            bubbleMinimizeToggle,
            bubbleIcon,
            cameraPreviewToggle,
            settingShowSkeleton,
            settingShowCamera,
            settingVibrate,
            settingMinimizeOnStart,
            settingCooldown,
            overlayRoot?.findViewById(R.id.panel_stop),
        )
        return interactive.any { isTouchOnView(it, rawX, rawY) }
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
                            AspectRatioStrategy.FALLBACK_RULE_AUTO,
                        ),
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
                    onFrameUpdated = ::onFrameUpdated,
                    previewTransformProvider = {
                        previewView.outputTransform
                    },
                )
                gestureAnalyzer?.close()
                gestureAnalyzer = analyzer
                bindSettingsControls()

                val executor = analysisExecutor ?: Executors.newSingleThreadExecutor().also {
                    analysisExecutor = it
                }

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(portraitSelector)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }

                previewView.post {
                    val viewPort = buildViewPort(previewView, rotation)
                    val group = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(analysis)
                        .setViewPort(viewPort)
                        .build()
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, group)
                }

                applySkeletonVisibility()
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun buildViewPort(previewView: PreviewView, rotation: Int): ViewPort {
        val width = max(previewView.width, 1)
        val height = max(previewView.height, 1)
        return ViewPort.Builder(Rational(width, height), rotation)
            .setScaleType(ViewPort.FIT)
            .build()
    }

    private fun onGestureDetected(gesture: GestureType) {
        when (gesture) {
            GestureType.SCROLL_UP -> SpatialAccessibilityService.nextItem()
            GestureType.SCROLL_DOWN -> SpatialAccessibilityService.previousItem()
            GestureType.PLAY_PAUSE -> SpatialAccessibilityService.playPause()
        }

        if (BubblePreferences.isVibrateOnGesture(this)) {
            vibrateGesture()
        }

        val label = when (gesture) {
            GestureType.SCROLL_UP -> getString(R.string.gesture_next)
            GestureType.SCROLL_DOWN -> getString(R.string.gesture_previous)
            GestureType.PLAY_PAUSE -> getString(R.string.gesture_play_pause)
        }
        overlayRoot?.post {
            statusText?.text = label
            cameraPreviewStatus?.text = label
            if (!bubbleMinimized) {
                bubbleIcon?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.setDuration(100)?.withEndAction {
                    bubbleIcon?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(150)?.start()
                }?.start()
            }
            overlayRoot?.postDelayed({
                if (!bubbleMinimized) {
                    statusText?.text = getString(R.string.bubble_running)
                }
                cameraPreviewStatus?.text = getString(R.string.bubble_running)
            }, 700)
        }
    }

    private fun vibrateGesture() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(35)
        }
    }

    private fun onFrameUpdated(frame: HandFrameUpdate) {
        overlayRoot?.post {
            val overlay = cameraSkeletonOverlay ?: return@post
            overlay.setCoordinateTransform(
                frame.coordinateTransform,
                frame.imageWidth,
                frame.imageHeight,
                frame.cropLeft,
                frame.cropTop,
            )

            val minConfidence = BubblePreferences.getMinHandConfidence(this)
            if (
                BubblePreferences.isShowSkeleton(this) &&
                frame.landmarks != null &&
                frame.confidence >= minConfidence
            ) {
                overlay.updateLandmarks(frame.landmarks)
            } else {
                overlay.clearLandmarks()
            }
        }
    }

    private fun onTrackingUpdated(x: Float, y: Float, handConfidence: Float) {
        overlayRoot?.post {
            val active = handConfidence >= BubblePreferences.getMinHandConfidence(this)
            bubbleIcon?.setBackgroundResource(
                if (active) R.drawable.bubble_background_active else R.drawable.bubble_background,
            )
            if (bubbleMinimized) return@post

            val trackingLabel = if (active) {
                getString(R.string.hand_detected)
            } else {
                getString(R.string.bubble_running)
            }
            if (statusText?.text == getString(R.string.bubble_running) ||
                statusText?.text == getString(R.string.hand_detected)
            ) {
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
        overlayRoot?.let { windowManager.removeView(it) }
        overlayRoot = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    private fun isTouchOnView(view: View?, rawX: Float, rawY: Float): Boolean {
        view ?: return false
        if (view.visibility != View.VISIBLE) return false
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
        private const val OVERLAY_START_X_DP = 16
        private const val OVERLAY_START_Y_DP = 120

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BubbleService::class.java),
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
                    NotificationManager.IMPORTANCE_LOW,
                )
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }
        }
    }
}
