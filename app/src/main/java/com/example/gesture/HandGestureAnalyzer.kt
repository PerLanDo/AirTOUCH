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

/**
 * Runs MediaPipe hand landmark inference on camera frames and maps skeletal motion
 * to scroll / play-pause gestures.
 */
class HandGestureAnalyzer(
    context: Context,
    private val onGestureDetected: (GestureType) -> Unit,
    /** x/y = normalized palm position, handConfidence = model confidence (0..1). */
    private val onStatusUpdated: (trackingX: Float, trackingY: Float, handConfidence: Float) -> Unit,
    private val onLandmarksUpdated: (landmarks: List<NormalizedLandmark>?, confidence: Float) -> Unit = { _, _ -> },
) : ImageAnalysis.Analyzer, Closeable {

    private data class FrameHistory(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val isOpenPalm: Boolean,
        val isClosedFist: Boolean,
        val confidence: Float,
    )

    private val landmarkerEngine = HandLandmarkerEngine(context.applicationContext)
    private val history = mutableListOf<FrameHistory>()

    private val historyWindowMs = 800L
    private var lastTriggerTime = 0L
    private val cooldownMs = 1500L
    private val minHandConfidence = 0.35f

    private var smoothedX = 0.5f
    private var smoothedY = 0.5f

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()

        try {
            val detection = landmarkerEngine.detect(image)
            if (detection == null) {
                decayTrackingTowardCenter()
                onStatusUpdated(smoothedX, smoothedY, 0f)
                onLandmarksUpdated(null, 0f)
                return
            }

            val pose = HandPoseEvaluator.evaluate(detection.landmarks, detection.confidence)
            smoothedX = smoothedX * 0.70f + pose.palmX * 0.30f
            smoothedY = smoothedY * 0.70f + pose.palmY * 0.30f
            onStatusUpdated(smoothedX, smoothedY, pose.confidence)
            onLandmarksUpdated(detection.landmarks, pose.confidence)

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
                        confidence = pose.confidence,
                    )
                )
            }

            history.removeAll { now - it.timestamp > historyWindowMs }

            if (history.size < 6) return

            val first = history.first()
            val last = history.last()
            val duration = last.timestamp - first.timestamp
            if (duration <= 200) return

            val closedFistSwipe = history.count { it.isClosedFist } >= (history.size * 0.7f)
            val openPalmHold = history.count { it.isOpenPalm } >= (history.size * 0.75f)

            when {
                first.y < 0.40f && last.y > 0.60f && closedFistSwipe -> {
                    onGestureDetected(GestureType.SCROLL_UP)
                    history.clear()
                    lastTriggerTime = now
                }

                first.y > 0.60f && last.y < 0.40f && closedFistSwipe -> {
                    onGestureDetected(GestureType.SCROLL_DOWN)
                    history.clear()
                    lastTriggerTime = now
                }

                openPalmHold && isPalmHeldStill(history) && isPalmCentered(history) -> {
                    onGestureDetected(GestureType.PLAY_PAUSE)
                    history.clear()
                    lastTriggerTime = now
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onLandmarksUpdated(null, 0f)
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
        return meanX in 0.35f..0.65f && meanY in 0.35f..0.65f
    }

    override fun close() {
        landmarkerEngine.close()
    }
}
