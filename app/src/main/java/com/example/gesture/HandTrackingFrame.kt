package com.example.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/** Snapshot of one frame of ML hand tracking for UI overlay and gesture logic. */
data class HandTrackingFrame(
    val landmarks: List<NormalizedLandmark>,
    val confidence: Float,
    val palmX: Float,
    val palmY: Float,
    val isOpenPalm: Boolean,
    val isClosedFist: Boolean,
)
