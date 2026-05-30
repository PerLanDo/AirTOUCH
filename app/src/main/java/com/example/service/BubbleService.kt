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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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

    private var panelPreview: PreviewView? = null
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
            PixelFormat.TRANSLUCENT
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
                    onGestureDetected = ::onGestureDetected,
                    onStatusUpdated = ::onTrackingUpdated,
                )
                gestureAnalyzer?.close()
                gestureAnalyzer = analyzer

                val executor = analysisExecutor ?: Executors.newSingleThreadExecutor().also {
                    analysisExecutor = it
                }

                val analysis = ImageAnalysis.Builder()
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
                        analysis
                    )
                } else {
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis
                    )
                }
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
        bubbleRoot?.post {
            statusText?.text = when (gesture) {
                GestureType.SCROLL_UP -> getString(R.string.gesture_next)
                GestureType.SCROLL_DOWN -> getString(R.string.gesture_previous)
                GestureType.PLAY_PAUSE -> getString(R.string.gesture_play_pause)
            }
            bubbleRoot?.postDelayed({
                statusText?.text = getString(R.string.bubble_running)
            }, 900)
        }
    }

    private fun onTrackingUpdated(x: Float, y: Float, handConfidence: Float) {
        bubbleRoot?.post {
            val active = handConfidence > 0.5f
            bubbleIcon?.setBackgroundResource(
                if (active) R.drawable.bubble_background_active else R.drawable.bubble_background
            )
            if (!panelOpen && statusText?.text == getString(R.string.bubble_running)) {
                statusText?.text = if (active) {
                    getString(R.string.hand_detected)
                } else {
                    getString(R.string.bubble_running)
                }
            }
        }
    }

    private fun removeOverlays() {
        panelView?.let { windowManager.removeView(it) }
        bubbleRoot?.let { windowManager.removeView(it) }
        panelView = null
        bubbleRoot = null
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

    companion object {
        private const val TAG = "BubbleService"
        const val ACTION_STOP = "com.example.action.STOP_BUBBLE"
        private const val CHANNEL_ID = "spatial_motion_bubble"
        private const val NOTIFICATION_ID = 1001

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
