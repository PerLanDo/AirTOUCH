package com.example.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

/**
 * Interprets MediaPipe hand landmarks into palm position and open/closed pose.
 * Uses finger joint geometry instead of skin-color heuristics.
 */
object HandPoseEvaluator {

    data class HandPose(
        val palmX: Float,
        val palmY: Float,
        val confidence: Float,
        val extendedFingerCount: Int,
        val isOpenPalm: Boolean,
        val isClosedFist: Boolean,
    )

    fun evaluate(landmarks: List<NormalizedLandmark>, confidence: Float): HandPose {
        val palmX = HandLandmarkIndices.PALM_CENTER_INDICES
            .map { landmarks[it].x() }
            .average()
            .toFloat()
        val palmY = HandLandmarkIndices.PALM_CENTER_INDICES
            .map { landmarks[it].y() }
            .average()
            .toFloat()

        val indexExtended = isFingerExtended(
            landmarks,
            HandLandmarkIndices.INDEX_TIP,
            HandLandmarkIndices.INDEX_PIP,
            HandLandmarkIndices.INDEX_MCP,
        )
        val middleExtended = isFingerExtended(
            landmarks,
            HandLandmarkIndices.MIDDLE_TIP,
            HandLandmarkIndices.MIDDLE_PIP,
            HandLandmarkIndices.MIDDLE_MCP,
        )
        val ringExtended = isFingerExtended(
            landmarks,
            HandLandmarkIndices.RING_TIP,
            HandLandmarkIndices.RING_PIP,
            HandLandmarkIndices.RING_MCP,
        )
        val pinkyExtended = isFingerExtended(
            landmarks,
            HandLandmarkIndices.PINKY_TIP,
            HandLandmarkIndices.PINKY_PIP,
            HandLandmarkIndices.PINKY_MCP,
        )
        val thumbExtended = isThumbExtended(landmarks)

        val extendedFingerCount = listOf(
            indexExtended,
            middleExtended,
            ringExtended,
            pinkyExtended,
            thumbExtended,
        ).count { it }

        val isOpenPalm = extendedFingerCount >= 3
        val isClosedFist = extendedFingerCount <= 2

        return HandPose(
            palmX = palmX,
            palmY = palmY,
            confidence = confidence,
            extendedFingerCount = extendedFingerCount,
            isOpenPalm = isOpenPalm,
            isClosedFist = isClosedFist,
        )
    }

    /**
     * A finger is extended when its tip is farther from the MCP joint than the PIP joint.
     */
    private fun isFingerExtended(
        landmarks: List<NormalizedLandmark>,
        tipIdx: Int,
        pipIdx: Int,
        mcpIdx: Int,
    ): Boolean {
        val tip = landmarks[tipIdx]
        val pip = landmarks[pipIdx]
        val mcp = landmarks[mcpIdx]
        return distance(tip, mcp) > distance(pip, mcp) * 1.04f
    }

    private fun isThumbExtended(landmarks: List<NormalizedLandmark>): Boolean {
        val thumbTip = landmarks[HandLandmarkIndices.THUMB_TIP]
        val thumbIp = landmarks[HandLandmarkIndices.THUMB_IP]
        val indexMcp = landmarks[HandLandmarkIndices.INDEX_MCP]
        return distance(thumbTip, indexMcp) > distance(thumbIp, indexMcp) * 0.95f
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        val dz = a.z() - b.z()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
