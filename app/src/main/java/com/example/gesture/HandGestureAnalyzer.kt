package com.example.gesture

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.io.Closeable

enum class GestureType {
    SCROLL_UP,
    SCROLL_DOWN,
    PLAY_PAUSE,
}

data class HandFrameUpdate(
    val landmarks: List<NormalizedLandmark>?,
    val confidence: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val coordinateTransform: CoordinateTransform?,
)

/**
 * Gestures (hold still + centered):
 * - Thumbs up → play/pause
 * - Open palm → next
 * - Closed fist → previous
 */
class HandGestureAnalyzer(
    context: Context,
    private val onGestureDetected: (GestureType) -> Unit,
    private val onStatusUpdated: (trackingX: Float, trackingY: Float, handConfidence: Float) -> Unit,
    private val onFrameUpdated: (HandFrameUpdate) -> Unit = { },
    private val previewTransformProvider: () -> OutputTransform?,
) : ImageAnalysis.Analyzer, Closeable {

    private data class FrameSample(
        val x: Float,
        val y: Float,
        val isOpenPalm: Boolean,
        val isClosedFist: Boolean,
        val isThumbsUp: Boolean,
    )

    private val landmarkerEngine = HandLandmarkerEngine(context.applicationContext)
    private val recentSamples = ArrayDeque<FrameSample>()

    private var lastTriggerTime = 0L
    private var cooldownMs = 1_200L
    private var minHandConfidence = 0.35f

    private var thumbsUpStreak = 0
    private var openPalmStreak = 0
    private var closedFistStreak = 0

    private var smoothedX = 0.5f
    private var smoothedY = 0.5f

    fun updateSettings(cooldownMs: Long, minHandConfidence: Float) {
        this.cooldownMs = cooldownMs.coerceIn(600L, 2_500L)
        this.minHandConfidence = minHandConfidence.coerceIn(0.2f, 0.7f)
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        val rotation = image.imageInfo.rotationDegrees
        val crop = image.cropRect
        val cropWidth = crop.width().coerceAtLeast(1)
        val cropHeight = crop.height().coerceAtLeast(1)

        val coordinateTransform = buildCoordinateTransform(image)

        try {
            val detection = landmarkerEngine.detect(image)
            if (detection == null) {
                decayTrackingTowardCenter()
                resetStreaks()
                onStatusUpdated(smoothedX, smoothedY, 0f)
                onFrameUpdated(
                    HandFrameUpdate(null, 0f, cropWidth, cropHeight, crop.left, crop.top, coordinateTransform),
                )
                return
            }

            val pose = HandPoseEvaluator.evaluate(detection.landmarks, detection.confidence)
            smoothedX = smoothedX * 0.55f + pose.palmX * 0.45f
            smoothedY = smoothedY * 0.55f + pose.palmY * 0.45f
            onStatusUpdated(smoothedX, smoothedY, pose.confidence)
            onFrameUpdated(
                HandFrameUpdate(
                    landmarks = detection.landmarks,
                    confidence = pose.confidence,
                    imageWidth = cropWidth,
                    imageHeight = cropHeight,
                    cropLeft = crop.left,
                    cropTop = crop.top,
                    coordinateTransform = coordinateTransform,
                ),
            )

            if (now - lastTriggerTime <= cooldownMs) {
                resetStreaks()
                return
            }

            if (pose.confidence < minHandConfidence) {
                resetStreaks()
                return
            }

            recentSamples.addLast(
                FrameSample(
                    x = smoothedX,
                    y = smoothedY,
                    isOpenPalm = pose.isOpenPalm,
                    isClosedFist = pose.isClosedFist,
                    isThumbsUp = pose.isThumbsUp,
                ),
            )
            while (recentSamples.size > STREAK_REQUIRED) {
                recentSamples.removeFirst()
            }

            updateStreaks(pose)

            if (!isHandCentered(smoothedX, smoothedY) || !isRecentlyStill()) {
                return
            }

            when {
                thumbsUpStreak >= STREAK_REQUIRED -> {
                    fireGesture(GestureType.PLAY_PAUSE, now)
                }
                openPalmStreak >= STREAK_REQUIRED -> {
                    fireGesture(GestureType.SCROLL_UP, now)
                }
                closedFistStreak >= STREAK_REQUIRED -> {
                    fireGesture(GestureType.SCROLL_DOWN, now)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFrameUpdated(
                HandFrameUpdate(null, 0f, cropWidth, cropHeight, crop.left, crop.top, coordinateTransform),
            )
        } finally {
            image.close()
        }
    }

    private fun buildCoordinateTransform(image: ImageProxy): CoordinateTransform? {
        val target = previewTransformProvider() ?: return null
        return try {
            val source = ImageProxyTransformFactory().getOutputTransform(image)
            CoordinateTransform(source, target)
        } catch (_: Exception) {
            null
        }
    }

    private fun updateStreaks(pose: HandPoseEvaluator.HandPose) {
        thumbsUpStreak = if (pose.isThumbsUp) thumbsUpStreak + 1 else 0
        openPalmStreak = if (pose.isOpenPalm) openPalmStreak + 1 else 0
        closedFistStreak = if (pose.isClosedFist) closedFistStreak + 1 else 0
    }

    private fun fireGesture(type: GestureType, now: Long) {
        onGestureDetected(type)
        lastTriggerTime = now
        resetStreaks()
        recentSamples.clear()
    }

    private fun resetStreaks() {
        thumbsUpStreak = 0
        openPalmStreak = 0
        closedFistStreak = 0
    }

    private fun isHandCentered(x: Float, y: Float): Boolean =
        x in 0.28f..0.72f && y in 0.28f..0.72f

    private fun isRecentlyStill(): Boolean {
        if (recentSamples.size < STREAK_REQUIRED) return false
        val meanX = recentSamples.map { it.x }.average().toFloat()
        val meanY = recentSamples.map { it.y }.average().toFloat()
        val variance = recentSamples.sumOf { sample ->
            val dx = sample.x - meanX
            val dy = sample.y - meanY
            (dx * dx + dy * dy).toDouble()
        }.toFloat() / recentSamples.size
        return variance < 0.006f
    }

    private fun decayTrackingTowardCenter() {
        smoothedX = smoothedX * 0.85f + 0.5f * 0.15f
        smoothedY = smoothedY * 0.85f + 0.5f * 0.15f
    }

    override fun close() {
        landmarkerEngine.close()
    }

    companion object {
        /** ~4 analysis frames at ~15–30 fps ≈ 150–300 ms hold. */
        private const val STREAK_REQUIRED = 4
    }
}
