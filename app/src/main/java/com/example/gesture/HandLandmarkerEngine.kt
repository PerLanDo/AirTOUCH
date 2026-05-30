package com.example.gesture

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable

/**
 * Wraps MediaPipe Hand Landmarker for live camera frames.
 * Uses RGB bitmap input (with rotation + mirror) for reliable detection across devices.
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

    fun detect(imageProxy: ImageProxy, mirrorForFrontCamera: Boolean = true): Detection? {
        val bitmap = ImageProxyConverter.toBitmap(imageProxy, mirrorForFrontCamera)
        return detect(bitmap)
    }

    fun detect(bitmap: Bitmap): Detection? {
        val mpImage = BitmapImageBuilder(bitmap).build()
        return try {
            frameTimestampMs = maxOf(frameTimestampMs + 1, SystemClock.uptimeMillis())
            val result = handLandmarker.detectForVideo(mpImage, frameTimestampMs)
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
        private const val MIN_DETECTION_CONFIDENCE = 0.35f
        private const val MIN_PRESENCE_CONFIDENCE = 0.35f
        private const val MIN_TRACKING_CONFIDENCE = 0.35f
    }
}
