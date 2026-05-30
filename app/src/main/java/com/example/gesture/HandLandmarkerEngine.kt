package com.example.gesture

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable

/**
 * Wraps MediaPipe Hand Landmarker for live camera frames.
 * Detects up to one hand with 21 skeletal landmarks (wrist, palm, and finger joints).
 */
class HandLandmarkerEngine(context: Context) : Closeable {

    data class Detection(
        val landmarks: List<NormalizedLandmark>,
        val confidence: Float,
    )

    private val handLandmarker: HandLandmarker
    private var frameTimestampMs = 0L

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(MIN_DETECTION_CONFIDENCE)
            .setMinHandPresenceConfidence(MIN_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .build()

        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detect(imageProxy: ImageProxy): Detection? {
        val mediaImage = imageProxy.image ?: return null
        val mpImage = MediaImageBuilder(mediaImage).build()
        return try {
            frameTimestampMs = maxOf(frameTimestampMs + 1, SystemClock.uptimeMillis())
            val processingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
                .build()

            val result = handLandmarker.detectForVideo(mpImage, processingOptions, frameTimestampMs)
            val landmarks = result.landmarks().firstOrNull() ?: return null
            val confidence = result.handedness()
                .firstOrNull()
                ?.firstOrNull()
                ?.score()
                ?: MIN_PRESENCE_CONFIDENCE

            Detection(landmarks = landmarks, confidence = confidence)
        } finally {
            mpImage.close()
        }
    }

    override fun close() {
        handLandmarker.close()
    }

    companion object {
        private const val MODEL_ASSET = "hand_landmarker.task"
        private const val MIN_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_PRESENCE_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
    }
}
