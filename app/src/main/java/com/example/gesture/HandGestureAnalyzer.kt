package com.example.gesture

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
)

/**
 * Maps hand poses to gestures:
 * - Pinch hold → play/pause
 * - Open palm hold → scroll up (next)
 * - Closed fist hold → scroll down (previous)
 */
class HandGestureAnalyzer(
    context: Context,
    private val onGestureDetected: (GestureType) -> Unit,
    private val onStatusUpdated: (trackingX: Float, trackingY: Float, handConfidence: Float) -> Unit,
    private val onFrameUpdated: (HandFrameUpdate) -> Unit = { },
) : ImageAnalysis.Analyzer, Closeable {

    private data class FrameHistory(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val isOpenPalm: Boolean,
        val isClosedFist: Boolean,
        val isPinching: Boolean,
        val confidence: Float,
    )

    private val landmarkerEngine = HandLandmarkerEngine(context.applicationContext)
    private val history = mutableListOf<FrameHistory>()

    private val historyWindowMs = 800L
    private var lastTriggerTime = 0L
    private var cooldownMs = 1_500L
    private var minHandConfidence = 0.35f

    private var smoothedX = 0.5f
    private var smoothedY = 0.5f

    fun updateSettings(cooldownMs: Long, minHandConfidence: Float) {
        this.cooldownMs = cooldownMs.coerceIn(800L, 3_000L)
        this.minHandConfidence = minHandConfidence.coerceIn(0.2f, 0.7f)
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        val rotation = image.imageInfo.rotationDegrees
        val frameWidth = if (rotation == 90 || rotation == 270) image.height else image.width
        val frameHeight = if (rotation == 90 || rotation == 270) image.width else image.height

        try {
            val detection = landmarkerEngine.detect(image)
            if (detection == null) {
                decayTrackingTowardCenter()
                onStatusUpdated(smoothedX, smoothedY, 0f)
                onFrameUpdated(HandFrameUpdate(null, 0f, frameWidth, frameHeight))
                return
            }

            val pose = HandPoseEvaluator.evaluate(detection.landmarks, detection.confidence)
            smoothedX = smoothedX * 0.70f + pose.palmX * 0.30f
            smoothedY = smoothedY * 0.70f + pose.palmY * 0.30f
            onStatusUpdated(smoothedX, smoothedY, pose.confidence)
            onFrameUpdated(
                HandFrameUpdate(
                    landmarks = detection.landmarks,
                    confidence = pose.confidence,
                    imageWidth = frameWidth,
                    imageHeight = frameHeight,
                )
            )

            if (now - lastTriggerTime <= cooldownMs) {
                history.clear()
                return
            }

            if (pose.confidence >= minHandConfidence) {
                history.add(
                    FrameHistory(
                        timestamp = now,
                        x = smoothedX,
                        y = smoothedY,
                        isOpenPalm = pose.isOpenPalm,
                        isClosedFist = pose.isClosedFist,
                        isPinching = pose.isPinching,
                        confidence = pose.confidence,
                    )
                )
            }

            history.removeAll { now - it.timestamp > historyWindowMs }

            if (history.size < 6) return

            val first = history.first()
            val last = history.last()
            if (last.timestamp - first.timestamp <= 200) return

            val pinchHold = history.count { it.isPinching } >= (history.size * 0.75f)
            val openPalmHold = history.count { it.isOpenPalm } >= (history.size * 0.75f)
            val closedFistHold = history.count { it.isClosedFist } >= (history.size * 0.75f)
            val heldStill = isPalmHeldStill(history)
            val centered = isPalmCentered(history)

            when {
                pinchHold && heldStill && centered -> {
                    onGestureDetected(GestureType.PLAY_PAUSE)
                    history.clear()
                    lastTriggerTime = now
                }

                openPalmHold && heldStill && centered -> {
                    onGestureDetected(GestureType.SCROLL_UP)
                    history.clear()
                    lastTriggerTime = now
                }

                closedFistHold && heldStill && centered -> {
                    onGestureDetected(GestureType.SCROLL_DOWN)
                    history.clear()
                    lastTriggerTime = now
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onFrameUpdated(HandFrameUpdate(null, 0f, frameWidth, frameHeight))
        } finally {
            image.close()
        }
    }

    private fun decayTrackingTowardCenter() {
        smoothedX = smoothedX * 0.85f + 0.5f * 0.15f
        smoothedY = smoothedY * 0.85f + 0.5f * 0.15f
    }

    private fun isPalmHeldStill(frames: List<FrameHistory>): Boolean {
        if (frames.size < 6) return false

        val meanX = frames.map { it.x }.average().toFloat()
        val meanY = frames.map { it.y }.average().toFloat()
        val variance = frames.sumOf { frame ->
            val dx = frame.x - meanX
            val dy = frame.y - meanY
            (dx * dx + dy * dy).toDouble()
        }.toFloat() / frames.size

        return variance < 0.0035f
    }

    private fun isPalmCentered(frames: List<FrameHistory>): Boolean {
        val meanX = frames.map { it.x }.average().toFloat()
        val meanY = frames.map { it.y }.average().toFloat()
        return meanX in 0.30f..0.70f && meanY in 0.30f..0.70f
    }

    override fun close() {
        landmarkerEngine.close()
    }
}
