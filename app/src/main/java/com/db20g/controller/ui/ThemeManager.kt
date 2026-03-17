package com.db20g.controller.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.db20g.controller.R

/**
 * Manages app theme selection and persistence.
 * Supports: Default Dark, AMOLED True-Black, Red-Light (Night Vision), System Auto.
 */
class ThemeManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME = "selected_theme"
        const val THEME_DEFAULT = "default"
        const val THEME_AMOLED = "amoled"
        const val THEME_RED_LIGHT = "red_light"
        const val THEME_SYSTEM = "system"

        val THEME_OPTIONS = listOf(
            THEME_DEFAULT to "Dark (Default)",
            THEME_AMOLED to "AMOLED True-Black",
            THEME_RED_LIGHT to "Red-Light (Night Vision)",
            THEME_SYSTEM to "Follow System"
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var currentTheme: String
        get() = prefs.getString(KEY_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
        set(value) {
            prefs.edit().putString(KEY_THEME, value).apply()
        }

    /**
     * Get the theme resource ID for the current selection.
     */
    fun getThemeResId(): Int {
        return when (currentTheme) {
            THEME_AMOLED -> R.style.Theme_DB20GController_AMOLED
            THEME_RED_LIGHT -> R.style.Theme_DB20GController_RedLight
            THEME_SYSTEM -> R.style.Theme_DB20GController_System
            else -> R.style.Theme_DB20GController
        }
    }

    /**
     * Apply the system night mode based on theme selection.
     */
    fun applyNightMode() {
        when (currentTheme) {
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    /**
     * Get display name for the current theme.
     */
    fun getThemeDisplayName(): String {
        return THEME_OPTIONS.find { it.first == currentTheme }?.second ?: "Dark (Default)"
    }
}
