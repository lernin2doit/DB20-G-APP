package com.db20g.controller.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.db20g.controller.R

/**
 * Manages accessibility features:
 * - High-contrast mode (beyond dark theme)
 * - Configurable font sizes (small/medium/large/extra-large)
 * - Haptic feedback for PTT confirmation
 * - TalkBack content description helpers
 */
class AccessibilityManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("accessibility_settings", Context.MODE_PRIVATE)

    enum class FontScale(val label: String, val scale: Float) {
        SMALL("Small", 0.85f),
        MEDIUM("Medium", 1.0f),
        LARGE("Large", 1.3f),
        EXTRA_LARGE("Extra Large", 1.6f)
    }

    // --- High Contrast Mode ---

    var highContrastEnabled: Boolean
        get() = prefs.getBoolean("high_contrast", false)
        set(value) { prefs.edit().putBoolean("high_contrast", value).apply() }

    /**
     * Apply high-contrast styling to a view hierarchy.
     * Increases text contrast and adds bold outlines.
     */
    fun applyHighContrast(root: View) {
        if (!highContrastEnabled) return

        if (root is TextView) {
            // Max contrast: pure white text on dark backgrounds
            root.setTextColor(0xFFFFFFFF.toInt())
            root.setShadowLayer(2f, 1f, 1f, 0xFF000000.toInt())
        }

        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                applyHighContrast(root.getChildAt(i))
            }
        }
    }

    // --- Font Size ---

    var fontScale: FontScale
        get() {
            val ordinal = prefs.getInt("font_scale", FontScale.MEDIUM.ordinal)
            return FontScale.entries.getOrElse(ordinal) { FontScale.MEDIUM }
        }
        set(value) { prefs.edit().putInt("font_scale", value.ordinal).apply() }

    /**
     * Apply font scaling to all TextViews in a view hierarchy.
     */
    fun applyFontScale(root: View) {
        val scale = fontScale.scale
        if (scale == 1.0f) return

        if (root is TextView) {
            if (root.getTag(R.id.tag_font_scaled) == true) return
            root.setTextSize(TypedValue.COMPLEX_UNIT_PX, root.textSize * scale)
            root.setTag(R.id.tag_font_scaled, true)
        }

        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                applyFontScale(root.getChildAt(i))
            }
        }
    }

    // --- Haptic Feedback ---

    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_feedback", true)
        set(value) { prefs.edit().putBoolean("haptic_feedback", value).apply() }

    /**
     * Trigger haptic feedback for PTT press.
     * Uses a strong double-pulse pattern to confirm PTT activation.
     */
    fun pttHapticFeedback() {
        if (!hapticEnabled) return
        vibrate(longArrayOf(0, 50, 30, 80), -1)
    }

    /**
     * Trigger haptic feedback for PTT release.
     * Uses a short single pulse to confirm PTT deactivation.
     */
    fun pttReleaseHapticFeedback() {
        if (!hapticEnabled) return
        vibrate(longArrayOf(0, 30), -1)
    }

    /**
     * Trigger haptic feedback for channel change.
     */
    fun channelChangeHapticFeedback() {
        if (!hapticEnabled) return
        vibrate(longArrayOf(0, 20), -1)
    }

    private fun vibrate(pattern: LongArray, repeat: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = mgr.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
        }
    }

    // --- TalkBack / Content Description Helpers ---

    /**
     * Set a descriptive content description for radio UI elements.
     */
    fun setRadioContentDescription(view: View, channel: Int, frequency: String, isTransmitting: Boolean) {
        val state = if (isTransmitting) "Transmitting" else "Idle"
        view.contentDescription = "Channel $channel, $frequency megahertz, $state"
    }

    fun setPttContentDescription(view: View, isTransmitting: Boolean) {
        if (isTransmitting) {
            view.contentDescription = "Push to talk button, currently transmitting. Double tap to release."
        } else {
            view.contentDescription = "Push to talk button, idle. Double tap to transmit."
        }
    }

    fun setChannelContentDescription(view: View, channel: Int, name: String, frequency: String) {
        view.contentDescription = "Channel $channel, $name, $frequency megahertz"
    }

    /**
     * Apply all accessibility features (high contrast, font scale) to a root view.
     */
    fun applyAll(root: View) {
        applyHighContrast(root)
        applyFontScale(root)
    }
}
