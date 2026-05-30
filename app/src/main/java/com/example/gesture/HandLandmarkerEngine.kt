package com.example.gesture

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable
import kotlin.math.max

/**
 * Wraps MediaPipe Hand Landmarker for live camera frames.
 */
class HandLandmarkerEngine private constructor(
    private val handLandmarker: HandLandmarker,
) : Closeable {

    data class Detection(
        val landmarks: List<NormalizedLandmark>,
        val confidence: Float,
    )

    private var frameTimestampMs = 0L

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

            val handednessScore = result.handedness()
                .firstOrNull()
                ?.firstOrNull()
                ?.score()
                ?: 0f

            val avgVisibility = landmarks
                .map { landmark -> landmark.visibility().orElse(0f) }
                .average()
                .toFloat()

            val confidence = max(handednessScore, avgVisibility)

            Detection(landmarks = landmarks, confidence = confidence)
        } catch (e: Exception) {
            Log.e(TAG, "Hand detection failed", e)
            null
        } finally {
            mpImage.close()
        }
    }

    override fun close() {
        handLandmarker.close()
    }

    companion object {
        private const val TAG = "HandLandmarkerEngine"
        private const val MODEL_ASSET = "hand_landmarker.task"
        private const val MIN_DETECTION_CONFIDENCE = 0.35f
        private const val MIN_PRESENCE_CONFIDENCE = 0.35f
        private const val MIN_TRACKING_CONFIDENCE = 0.35f

        fun create(context: Context): HandLandmarkerEngine? {
            return try {
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

                HandLandmarkerEngine(HandLandmarker.createFromOptions(context, options))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create HandLandmarker", e)
                null
            }
        }
    }
}
