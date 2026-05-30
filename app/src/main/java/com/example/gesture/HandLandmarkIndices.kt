package com.example.gesture

/**
 * MediaPipe Hand Landmarker outputs 21 landmarks per detected hand.
 * https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker
 */
object HandLandmarkIndices {
    const val WRIST = 0

    const val THUMB_CMC = 1
    const val THUMB_MCP = 2
    const val THUMB_IP = 3
    const val THUMB_TIP = 4

    const val INDEX_MCP = 5
    const val INDEX_PIP = 6
    const val INDEX_DIP = 7
    const val INDEX_TIP = 8

    const val MIDDLE_MCP = 9
    const val MIDDLE_PIP = 10
    const val MIDDLE_DIP = 11
    const val MIDDLE_TIP = 12

    const val RING_MCP = 13
    const val RING_PIP = 14
    const val RING_DIP = 15
    const val RING_TIP = 16

    const val PINKY_MCP = 17
    const val PINKY_PIP = 18
    const val PINKY_DIP = 19
    const val PINKY_TIP = 20

    val PALM_CENTER_INDICES = intArrayOf(WRIST, INDEX_MCP, MIDDLE_MCP, RING_MCP, PINKY_MCP)
}
