package com.example.gesture

/**
 * Bone edges between the 21 MediaPipe hand landmarks for skeleton rendering.
 * https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker
 */
object HandLandmarkConnections {
    val EDGES: List<Pair<Int, Int>> = listOf(
        HandLandmarkIndices.WRIST to HandLandmarkIndices.THUMB_CMC,
        HandLandmarkIndices.THUMB_CMC to HandLandmarkIndices.THUMB_MCP,
        HandLandmarkIndices.THUMB_MCP to HandLandmarkIndices.THUMB_IP,
        HandLandmarkIndices.THUMB_IP to HandLandmarkIndices.THUMB_TIP,

        HandLandmarkIndices.WRIST to HandLandmarkIndices.INDEX_MCP,
        HandLandmarkIndices.INDEX_MCP to HandLandmarkIndices.INDEX_PIP,
        HandLandmarkIndices.INDEX_PIP to HandLandmarkIndices.INDEX_DIP,
        HandLandmarkIndices.INDEX_DIP to HandLandmarkIndices.INDEX_TIP,

        HandLandmarkIndices.WRIST to HandLandmarkIndices.MIDDLE_MCP,
        HandLandmarkIndices.MIDDLE_MCP to HandLandmarkIndices.MIDDLE_PIP,
        HandLandmarkIndices.MIDDLE_PIP to HandLandmarkIndices.MIDDLE_DIP,
        HandLandmarkIndices.MIDDLE_DIP to HandLandmarkIndices.MIDDLE_TIP,

        HandLandmarkIndices.WRIST to HandLandmarkIndices.RING_MCP,
        HandLandmarkIndices.RING_MCP to HandLandmarkIndices.RING_PIP,
        HandLandmarkIndices.RING_PIP to HandLandmarkIndices.RING_DIP,
        HandLandmarkIndices.RING_DIP to HandLandmarkIndices.RING_TIP,

        HandLandmarkIndices.WRIST to HandLandmarkIndices.PINKY_MCP,
        HandLandmarkIndices.PINKY_MCP to HandLandmarkIndices.PINKY_PIP,
        HandLandmarkIndices.PINKY_PIP to HandLandmarkIndices.PINKY_DIP,
        HandLandmarkIndices.PINKY_DIP to HandLandmarkIndices.PINKY_TIP,

        HandLandmarkIndices.INDEX_MCP to HandLandmarkIndices.MIDDLE_MCP,
        HandLandmarkIndices.MIDDLE_MCP to HandLandmarkIndices.RING_MCP,
        HandLandmarkIndices.RING_MCP to HandLandmarkIndices.PINKY_MCP,
    )
}
