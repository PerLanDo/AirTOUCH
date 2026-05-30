package com.example.gesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sin

/**
 * Full-screen overlay that draws animated hand skeleton landmarks in real time.
 */
class HandSkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark>? = null
    private var visible = false
    private var pulsePhase = 0f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#80D0E4FF")
        strokeCap = Paint.Cap.ROUND
    }

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D0E4FF")
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#44D0E4FF")
    }

    fun updateFrame(frame: HandTrackingFrame?) {
        if (frame == null || frame.confidence < 0.35f) {
            if (visible) {
                visible = false
                landmarks = null
                removeCallbacks(animationTick)
                postInvalidate()
            }
            return
        }
        val wasVisible = visible
        visible = true
        landmarks = frame.landmarks
        if (!wasVisible) {
            postOnAnimation(animationTick)
        }
    }

    private val animationTick = object : Runnable {
        override fun run() {
            if (!visible) return
            pulsePhase += 0.14f
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = landmarks ?: return
        if (!visible || width == 0 || height == 0) return

        val pulse = 0.55f + 0.45f * ((sin(pulsePhase.toDouble()) + 1.0) / 2.0).toFloat()
        linePaint.alpha = (180 * pulse).toInt().coerceIn(60, 255)
        jointPaint.alpha = (255 * pulse).toInt().coerceIn(120, 255)
        glowPaint.alpha = (90 * pulse).toInt().coerceIn(30, 120)

        val points = Array(pts.size) { i ->
            toScreen(pts[i])
        }

        for ((start, end) in CONNECTIONS) {
            val a = points.getOrNull(start) ?: continue
            val b = points.getOrNull(end) ?: continue
            canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
        }

        for (point in points) {
            canvas.drawCircle(point.x, point.y, 22f * pulse, glowPaint)
            canvas.drawCircle(point.x, point.y, 9f, jointPaint)
        }
    }

    private fun toScreen(landmark: NormalizedLandmark): PointF {
        // Landmarks are from a mirrored front-camera bitmap — map to full screen.
        return PointF(landmark.x() * width, landmark.y() * height)
    }

    companion object {
        private val CONNECTIONS = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            0 to 9, 9 to 10, 10 to 11, 11 to 12,
            0 to 13, 13 to 14, 14 to 15, 15 to 16,
            0 to 17, 17 to 18, 18 to 19, 19 to 20,
            5 to 9, 9 to 13, 13 to 17,
        )
    }
}
