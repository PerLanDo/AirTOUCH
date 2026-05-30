package com.example.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.gesture.HandLandmarkConnections
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.max

/**
 * Draws hand skeleton aligned with [androidx.camera.view.PreviewView] FIT_CENTER mapping.
 */
class HandSkeletonOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var landmarks: List<NormalizedLandmark>? = null

    /** Matches front-camera preview mirroring in CameraX. */
    var mirrorHorizontally: Boolean = true

    /** Un-rotated analysis frame size (matches MediaPipe input after rotation). */
    var sourceImageWidth: Int = 1
    var sourceImageHeight: Int = 1

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

    fun setFrameSize(width: Int, height: Int) {
        if (width > 0 && height > 0 && (width != sourceImageWidth || height != sourceImageHeight)) {
            sourceImageWidth = width
            sourceImageHeight = height
            postInvalidateOnAnimation()
        }
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
        var nx = landmark.x().coerceIn(0f, 1f)
        val ny = landmark.y().coerceIn(0f, 1f)
        if (mirrorHorizontally) {
            nx = 1f - nx
        }

        val imageX = nx * sourceImageWidth
        val imageY = ny * sourceImageHeight

        val scale = minOf(
            width / sourceImageWidth.toFloat(),
            height / sourceImageHeight.toFloat(),
        )
        val offsetX = (width - sourceImageWidth * scale) / 2f
        val offsetY = (height - sourceImageHeight * scale) / 2f

        return offsetX + imageX * scale to offsetY + imageY * scale
    }
}
