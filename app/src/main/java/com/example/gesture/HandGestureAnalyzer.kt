package com.example.gesture

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
    private val onHandFrame: (HandTrackingFrame?) -> Unit,
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

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        try {
            val detection = landmarkerEngine.detect(imageProxy, mirrorForFrontCamera = true)
            if (detection == null) {
                decayTrackingTowardCenter()
                onHandFrame(null)
                return
            }

            val pose = HandPoseEvaluator.evaluate(detection.landmarks, detection.confidence)
            smoothedX = smoothedX * 0.70f + pose.palmX * 0.30f
            smoothedY = smoothedY * 0.70f + pose.palmY * 0.30f

            onHandFrame(
                HandTrackingFrame(
                    landmarks = detection.landmarks,
                    confidence = pose.confidence,
                    palmX = smoothedX,
                    palmY = smoothedY,
                    isOpenPalm = pose.isOpenPalm,
                    isClosedFist = pose.isClosedFist,
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
                        confidence = pose.confidence,
                    )
                )
            }

            history.removeAll { now - it.timestamp > historyWindowMs }

            if (history.size < 5) return

            val first = history.first()
            val last = history.last()
            val duration = last.timestamp - first.timestamp
            if (duration <= 180) return

            val closedFistSwipe = history.count { it.isClosedFist } >= (history.size * 0.55f).toInt()
            val openPalmHold = history.count { it.isOpenPalm } >= (history.size * 0.6f).toInt()

            when {
                first.y < 0.42f && last.y > 0.58f && closedFistSwipe -> {
                    onGestureDetected(GestureType.SCROLL_UP)
                    history.clear()
                    lastTriggerTime = now
                }

                first.y > 0.58f && last.y < 0.42f && closedFistSwipe -> {
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
            onHandFrame(null)
        } finally {
            imageProxy.close()
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

        return variance < 0.0045f
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
