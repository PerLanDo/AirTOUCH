package com.example.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.gesture.HandLandmarkConnections
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Draws hand skeleton bones and joints on top of the mirrored front-camera preview.
 */
class HandSkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var landmarks: List<NormalizedLandmark>? = null

    /** Front-camera preview is mirrored; flip landmark X to align overlay. */
    var mirrorHorizontally: Boolean = true

    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4ADE80")
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60A5FA")
        style = Paint.Style.FILL
    }

    fun updateLandmarks(points: List<NormalizedLandmark>?) {
        landmarks = points
        postInvalidateOnAnimation()
    }

    fun clearLandmarks() {
        landmarks = null
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val points = landmarks ?: return
        if (points.size < 21 || width == 0 || height == 0) return

        for ((startIdx, endIdx) in HandLandmarkConnections.EDGES) {
            val start = mapPoint(points[startIdx])
            val end = mapPoint(points[endIdx])
            canvas.drawLine(start.first, start.second, end.first, end.second, bonePaint)
        }

        val jointRadius = 4f * resources.displayMetrics.density
        for (landmark in points) {
            val (x, y) = mapPoint(landmark)
            canvas.drawCircle(x, y, jointRadius, jointPaint)
        }
    }

    private fun mapPoint(landmark: NormalizedLandmark): Pair<Float, Float> {
        val normalizedX = if (mirrorHorizontally) 1f - landmark.x() else landmark.x()
        return normalizedX * width to landmark.y() * height
    }
}
