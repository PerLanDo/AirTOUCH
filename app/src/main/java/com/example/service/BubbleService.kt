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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import com.example.gesture.HandSkeletonOverlayView
import com.example.gesture.HandTrackingFrame
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class BubbleService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleRoot: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleFrame: FrameLayout? = null
    private var bubbleIcon: ImageView? = null
    private var bubbleSideLabel: TextView? = null

    private var handOverlayView: HandSkeletonOverlayView? = null
    private var handOverlayParams: WindowManager.LayoutParams? = null

    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelOpen = false
    private var panelPreview: PreviewView? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var gestureAnalyzer: HandGestureAnalyzer? = null
    private var analysisExecutor: ExecutorService? = null

    private var handCurrentlyVisible = false
    private var gestureFlashActive = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        showHandTrackingOverlay()
        showBubble()
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

    private fun showHandTrackingOverlay() {
        val overlay = HandSkeletonOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlay, params)
        handOverlayView = overlay
        handOverlayParams = params
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val bubble = inflater.inflate(R.layout.overlay_bubble, null)
        bubbleFrame = bubble.findViewById(R.id.bubble_frame)
        bubbleIcon = bubble.findViewById(R.id.bubble_icon)
        bubbleSideLabel = bubble.findViewById(R.id.bubble_side_label)
        setBubbleBorder(BubbleBorder.IDLE)
        bubbleSideLabel?.text = getString(R.string.bubble_running)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
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

    @SuppressLint("InflateParams")
    private fun togglePanel() {
        if (panelOpen) {
            panelView?.let { windowManager.removeView(it) }
            panelView = null
            panelParams = null
            panelPreview = null
            panelOpen = false
            bindCamera()
            return
        }

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val panel = inflater.inflate(R.layout.overlay_settings_panel, null)
        panelPreview = panel.findViewById(R.id.panel_camera_preview)
        panel.findViewById<TextView>(R.id.panel_stop).setOnClickListener { stopSelf() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(panel, params)
        panelView = panel
        panelParams = params
        panelOpen = true
        updatePanelPosition()
        bindCamera()
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
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val analyzer = HandGestureAnalyzer(
                    applicationContext,
                    onGestureDetected = { gesture ->
                        mainHandler.post { onGestureDetected(gesture) }
                    },
                    onHandFrame = { frame ->
                        mainHandler.post { onHandFrame(frame) }
                    },
                )
                gestureAnalyzer?.close()
                gestureAnalyzer = analyzer

                val executor = analysisExecutor ?: Executors.newSingleThreadExecutor().also {
                    analysisExecutor = it
                }

                val analysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }

                val previewView = panelPreview
                if (previewView != null) {
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis,
                    )
                } else {
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                mainHandler.post {
                    bubbleSideLabel?.text = getString(R.string.camera_error)
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onHandFrame(frame: HandTrackingFrame?) {
        handOverlayView?.updateFrame(frame)

        if (gestureFlashActive) return

        val active = frame != null && frame.confidence >= 0.35f
        handCurrentlyVisible = active

        if (active) {
            setBubbleBorder(BubbleBorder.HAND)
            bubbleSideLabel?.text = when {
                frame.isOpenPalm -> getString(R.string.pose_open_palm)
                frame.isClosedFist -> getString(R.string.pose_closed_fist)
                else -> getString(R.string.hand_detected)
            }
        } else {
            setBubbleBorder(BubbleBorder.IDLE)
            bubbleSideLabel?.text = getString(R.string.bubble_running)
        }
    }

    private fun onGestureDetected(gesture: GestureType) {
        when (gesture) {
            GestureType.SCROLL_UP -> SpatialAccessibilityService.nextItem()
            GestureType.SCROLL_DOWN -> SpatialAccessibilityService.previousItem()
            GestureType.PLAY_PAUSE -> SpatialAccessibilityService.playPause()
        }

        gestureFlashActive = true
        val border = when (gesture) {
            GestureType.SCROLL_UP -> BubbleBorder.SCROLL_UP
            GestureType.SCROLL_DOWN -> BubbleBorder.SCROLL_DOWN
            GestureType.PLAY_PAUSE -> BubbleBorder.PAUSE
        }
        val label = when (gesture) {
            GestureType.SCROLL_UP -> getString(R.string.gesture_next)
            GestureType.SCROLL_DOWN -> getString(R.string.gesture_previous)
            GestureType.PLAY_PAUSE -> getString(R.string.gesture_play_pause)
        }

        setBubbleBorder(border)
        bubbleSideLabel?.text = label

        mainHandler.postDelayed({
            gestureFlashActive = false
            if (handCurrentlyVisible) {
                setBubbleBorder(BubbleBorder.HAND)
                bubbleSideLabel?.text = getString(R.string.hand_detected)
            } else {
                setBubbleBorder(BubbleBorder.IDLE)
                bubbleSideLabel?.text = getString(R.string.bubble_running)
            }
        }, GESTURE_FLASH_MS)
    }

    private fun setBubbleBorder(border: BubbleBorder) {
        val resId = when (border) {
            BubbleBorder.IDLE -> R.drawable.bubble_border_idle
            BubbleBorder.HAND -> R.drawable.bubble_border_hand
            BubbleBorder.SCROLL_UP -> R.drawable.bubble_border_scroll_up
            BubbleBorder.SCROLL_DOWN -> R.drawable.bubble_border_scroll_down
            BubbleBorder.PAUSE -> R.drawable.bubble_border_pause
        }
        bubbleFrame?.setBackgroundResource(resId)
    }

    private fun removeOverlays() {
        panelView?.let { windowManager.removeView(it) }
        bubbleRoot?.let { windowManager.removeView(it) }
        handOverlayView?.let { windowManager.removeView(it) }
        panelView = null
        bubbleRoot = null
        handOverlayView = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private enum class BubbleBorder {
        IDLE,
        HAND,
        SCROLL_UP,
        SCROLL_DOWN,
        PAUSE,
    }

    companion object {
        private const val TAG = "BubbleService"
        const val ACTION_STOP = "com.example.action.STOP_BUBBLE"
        private const val CHANNEL_ID = "spatial_motion_bubble"
        private const val NOTIFICATION_ID = 1001
        private const val GESTURE_FLASH_MS = 1200L

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
