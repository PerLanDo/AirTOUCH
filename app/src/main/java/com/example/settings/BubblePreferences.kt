package com.example.settings

import android.content.Context
import androidx.core.content.edit

object BubblePreferences {

    private const val PREFS_NAME = "spatial_motion_bubble"

    private const val KEY_SHOW_SKELETON = "show_skeleton"
    private const val KEY_SHOW_CAMERA_ON_START = "show_camera_on_start"
    private const val KEY_VIBRATE_ON_GESTURE = "vibrate_on_gesture"
    private const val KEY_GESTURE_COOLDOWN_MS = "gesture_cooldown_ms"
    private const val KEY_MIN_HAND_CONFIDENCE = "min_hand_confidence"
    private const val KEY_PANEL_EXPANDED = "panel_expanded"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isShowSkeleton(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_SKELETON, true)

    fun setShowSkeleton(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_SKELETON, enabled) }
    }

    fun isShowCameraOnStart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_CAMERA_ON_START, true)

    fun setShowCameraOnStart(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_CAMERA_ON_START, enabled) }
    }

    fun isVibrateOnGesture(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE_ON_GESTURE, true)

    fun setVibrateOnGesture(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_VIBRATE_ON_GESTURE, enabled) }
    }

    fun getGestureCooldownMs(context: Context): Long =
        prefs(context).getLong(KEY_GESTURE_COOLDOWN_MS, 1_500L)

    fun setGestureCooldownMs(context: Context, ms: Long) {
        prefs(context).edit { putLong(KEY_GESTURE_COOLDOWN_MS, ms.coerceIn(800L, 3_000L)) }
    }

    fun getMinHandConfidence(context: Context): Float =
        prefs(context).getFloat(KEY_MIN_HAND_CONFIDENCE, 0.35f)

    fun setMinHandConfidence(context: Context, value: Float) {
        prefs(context).edit { putFloat(KEY_MIN_HAND_CONFIDENCE, value.coerceIn(0.2f, 0.7f)) }
    }

    fun isPanelExpanded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PANEL_EXPANDED, true)

    fun setPanelExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit { putBoolean(KEY_PANEL_EXPANDED, expanded) }
    }
}
