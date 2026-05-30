package com.example.gesture

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

enum class GestureType {
    SCROLL_UP,    // Hand moves high to low (up to down)
    SCROLL_DOWN,  // Hand moves low to high (down to up)
    PLAY_PAUSE    // Showing palm / held still in center
}

class HandGestureAnalyzer(
    private val onGestureDetected: (GestureType) -> Unit,
    private val onStatusUpdated: (trackingX: Float, trackingY: Float, skinRatio: Float) -> Unit
) : ImageAnalysis.Analyzer {

    data class FrameHistory(
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val skinRatio: Float
    )

    private val history = mutableListOf<FrameHistory>()
    private val historyWindowMs = 800L
    private var lastTriggerTime = 0L
    private val cooldownMs = 1500L

    // Minimum skin coverage to register active tracking (0.8% of sub-sampled frame)
    private val minActiveSkinRatio = 0.008f 
    
    private var smoothedX = 0.5f
    private var smoothedY = 0.5f

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        
        try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val width = image.width
            val height = image.height

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixelStride = vPlane.pixelStride

            var skinPixelsCount = 0
            var sumX = 0L
            var sumY = 0L

            // Step size for high frequency frame sampling (instant compile with 0 extra load)
            val stepX = 8
            val stepY = 8
            var totalChecked = 0

            for (y in 0 until height step stepY) {
                for (x in 0 until width step stepX) {
                    totalChecked++
                    
                    val yIndex = y * yRowStride + x * yPixelStride
                    val uvX = x / 2
                    val uvY = y / 2
                    
                    val uIndex = uvY * uRowStride + uvX * uPixelStride
                    val vIndex = uvY * vRowStride + uvX * vPixelStride

                    if (yIndex >= yBuffer.remaining() || uIndex >= uBuffer.remaining() || vIndex >= vBuffer.remaining()) {
                        continue
                    }

                    val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                    val uVal = uBuffer.get(uIndex).toInt() and 0xFF
                    val vVal = vBuffer.get(vIndex).toInt() and 0xFF

                    // Human skin-color clustering boundary:
                    // V is generally 133 to 175 and U is generally 75 to 128
                    if (vVal in 133..175 && uVal in 75..128 && yVal > 55) {
                        skinPixelsCount++
                        sumX += x
                        sumY += y
                    }
                }
            }

            val skinRatio = skinPixelsCount.toFloat() / totalChecked

            var centroidX = 0.5f
            var centroidY = 0.5f

            if (skinPixelsCount > 10) {
                centroidX = (sumX.toFloat() / skinPixelsCount) / width
                // For layout matching, invert camera Y if needed, but standard sensor coordinate is top-to-bottom
                centroidY = (sumY.toFloat() / skinPixelsCount) / height
                
                // Keep history smooth with an exponential filter
                smoothedX = smoothedX * 0.70f + centroidX * 0.30f
                smoothedY = smoothedY * 0.70f + centroidY * 0.30f
            } else {
                smoothedX = smoothedX * 0.85f + 0.5f * 0.15f
                smoothedY = smoothedY * 0.85f + 0.5f * 0.15f
            }

            // Report dynamic indicators back to UI layer
            onStatusUpdated(smoothedX, smoothedY, skinRatio)

            // Evaluate movement paths when outside of cool-down periods
            if (now - lastTriggerTime > cooldownMs) {
                if (skinRatio > minActiveSkinRatio) {
                    history.add(FrameHistory(now, smoothedX, smoothedY, skinRatio))
                }

                history.removeAll { now - it.timestamp > historyWindowMs }

                if (history.size >= 6) {
                    val first = history.first()
                    val last = history.last()
                    val duration = last.timestamp - first.timestamp

                    if (duration > 200) {
                        // Gesture 1: SCROLL_UP (Raised / swiped from top to bottom)
                        // Triggered when Y starts in top half (<0.4) and ends in bottom half (>0.6)
                        if (first.y < 0.40f && last.y > 0.60f) {
                            onGestureDetected(GestureType.SCROLL_UP)
                            history.clear()
                            lastTriggerTime = now
                        }
                        // Gesture 2: SCROLL_DOWN (Swiped from bottom to top)
                        // Triggered when Y starts in bottom half (>0.6) and ends in top half (<0.4)
                        else if (first.y > 0.60f && last.y < 0.40f) {
                            onGestureDetected(GestureType.SCROLL_DOWN)
                            history.clear()
                            lastTriggerTime = now
                        }
                        // Gesture 3: SHOW PALM FOR PLAY/PAUSE
                        // Triggered when a large palm (high skin ratio) sits steadily in the middle bounds
                        else if (skinRatio > 0.050f) {
                            var meanY = 0f
                            var meanX = 0f
                            history.forEach {
                                meanY += it.y
                                meanX += it.x
                            }
                            meanY /= history.size
                            meanX /= history.size

                            var variance = 0f
                            history.forEach {
                                val dy = it.y - meanY
                                val dx = it.x - meanX
                                variance += (dy * dy + dx * dx)
                            }
                            variance /= history.size

                            // Low coordinate movement variance over recent trail frames proves hand is still and showing a palm
                            if (variance < 0.0035f && meanY in 0.35f..0.65f && meanX in 0.35f..0.65f && history.size >= 8) {
                                onGestureDetected(GestureType.PLAY_PAUSE)
                                history.clear()
                                lastTriggerTime = now
                            }
                        }
                    }
                }
            } else {
                history.clear()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}
