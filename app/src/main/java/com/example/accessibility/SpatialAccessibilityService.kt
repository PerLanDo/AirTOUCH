package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class SpatialAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun swipeVertically(fromFraction: Float, toFraction: Float) {
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val centerX = width * 0.5f

        val path = Path().apply {
            moveTo(centerX, height * fromFraction)
            lineTo(centerX, height * toFraction)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 280))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun tapCenter() {
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width * 0.5f, height * 0.5f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()

        dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        private var instance: SpatialAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null

        fun nextItem() {
            // Hand moves down → next reel in TikTok / IG / YT Shorts
            instance?.swipeVertically(fromFraction = 0.75f, toFraction = 0.25f)
        }

        fun previousItem() {
            instance?.swipeVertically(fromFraction = 0.25f, toFraction = 0.75f)
        }

        fun playPause() {
            instance?.tapCenter()
        }
    }
}
