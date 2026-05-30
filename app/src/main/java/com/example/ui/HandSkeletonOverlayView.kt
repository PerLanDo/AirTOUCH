package com.example.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.camera.view.transform.CoordinateTransform
import com.example.gesture.HandLandmarkConnections
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Draws hand skeleton aligned with [androidx.camera.view.PreviewView] via [CoordinateTransform].
 */
class HandSkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var landmarks: List<NormalizedLandmark>? = null
    private var coordinateTransform: CoordinateTransform? = null
    private var cropLeft: Int = 0
    private var cropTop: Int = 0
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

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

    fun setCoordinateTransform(
        transform: CoordinateTransform?,
        frameWidth: Int,
        frameHeight: Int,
        cropLeft: Int,
        cropTop: Int,
    ) {
        coordinateTransform = transform
        imageWidth = frameWidth.coerceAtLeast(1)
        imageHeight = frameHeight.coerceAtLeast(1)
        this.cropLeft = cropLeft
        this.cropTop = cropTop
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
        val transform = coordinateTransform
        if (transform != null) {
            return try {
                val rect = RectF(
                    cropLeft + landmark.x() * imageWidth,
                    cropTop + landmark.y() * imageHeight,
                    cropLeft + landmark.x() * imageWidth + 1f,
                    cropTop + landmark.y() * imageHeight + 1f,
                )
                transform.mapRect(rect)
                rect.left to rect.top
            } catch (_: Exception) {
                mapPointFallback(landmark)
            }
        }

        return mapPointFallback(landmark)
    }

    /** Used only before PreviewView output transform is ready. */
    private fun mapPointFallback(landmark: NormalizedLandmark): Pair<Float, Float> {
        var nx = landmark.x().coerceIn(0f, 1f)
        val ny = landmark.y().coerceIn(0f, 1f)
        nx = 1f - nx

        val scale = minOf(
            width / imageWidth.toFloat(),
            height / imageHeight.toFloat(),
        )
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f
        return offsetX + nx * imageWidth * scale to offsetY + ny * imageHeight * scale
    }
}
