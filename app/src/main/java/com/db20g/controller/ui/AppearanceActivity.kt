package com.db20g.controller.ui

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.db20g.controller.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial

class AppearanceActivity : AppCompatActivity() {

    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_appearance)

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }

        setupThemeSelector()
        setupDisplaySettings()
    }

    private fun setupThemeSelector() {
        val tvCurrentTheme = findViewById<TextView>(R.id.tvCurrentTheme)
        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)

        tvCurrentTheme.text = "Current: ${themeManager.getThemeDisplayName()}"

        when (themeManager.currentTheme) {
            ThemeManager.THEME_DEFAULT -> findViewById<android.widget.RadioButton>(R.id.rbThemeDefault).isChecked = true
            ThemeManager.THEME_AMOLED -> findViewById<android.widget.RadioButton>(R.id.rbThemeAmoled).isChecked = true
            ThemeManager.THEME_RED_LIGHT -> findViewById<android.widget.RadioButton>(R.id.rbThemeRedLight).isChecked = true
            ThemeManager.THEME_SYSTEM -> findViewById<android.widget.RadioButton>(R.id.rbThemeSystem).isChecked = true
        }

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.rbThemeAmoled -> ThemeManager.THEME_AMOLED
                R.id.rbThemeRedLight -> ThemeManager.THEME_RED_LIGHT
                R.id.rbThemeSystem -> ThemeManager.THEME_SYSTEM
                else -> ThemeManager.THEME_DEFAULT
            }
            if (newTheme != themeManager.currentTheme) {
                themeManager.currentTheme = newTheme
                themeManager.applyNightMode()
                recreate()
            }
        }
    }

    private fun setupDisplaySettings() {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val switchStatusPill = findViewById<SwitchMaterial>(R.id.switchStatusPill)
        val chipNavRailLeft = findViewById<Chip>(R.id.chipNavRailLeft)
        val chipNavRailRight = findViewById<Chip>(R.id.chipNavRailRight)
        val chipGroupNavRailSide = findViewById<ChipGroup>(R.id.chipGroupNavRailSide)

        switchStatusPill.isChecked = prefs.getBoolean("show_status_pill", true)
        switchStatusPill.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("show_status_pill", checked).apply()
        }

        val side = prefs.getString("nav_rail_side", "left")
        if (side == "right") chipNavRailRight.isChecked = true else chipNavRailLeft.isChecked = true

        chipGroupNavRailSide.setOnCheckedStateChangeListener { _, checkedIds ->
            val isRight = checkedIds.contains(R.id.chipNavRailRight)
            prefs.edit().putString("nav_rail_side", if (isRight) "right" else "left").apply()
        }
    }
}
