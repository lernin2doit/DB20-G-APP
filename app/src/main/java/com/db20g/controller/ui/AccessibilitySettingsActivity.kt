package com.db20g.controller.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.db20g.controller.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import android.content.Intent

class AccessibilitySettingsActivity : AppCompatActivity() {

    private lateinit var accessibilityManager: AccessibilityManager
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessibility_settings)

        accessibilityManager = AccessibilityManager(this)

        setupHighContrast()
        setupFontSize()
        setupHaptic()
        setupTranslationButton()
    }

    private fun setupHighContrast() {
        val switch = findViewById<SwitchMaterial>(R.id.switchHighContrast)
        switch.isChecked = accessibilityManager.highContrastEnabled
        switch.setOnCheckedChangeListener { _, isChecked ->
            accessibilityManager.highContrastEnabled = isChecked
            if (isChecked) {
                accessibilityManager.applyHighContrast(
                    findViewById<View>(android.R.id.content).rootView
                )
            } else {
                recreate()
            }
        }
    }

    private fun setupFontSize() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFontSize)
        val preview = findViewById<TextView>(R.id.tvFontPreview)

        val currentScale = accessibilityManager.fontScale
        when (currentScale) {
            AccessibilityManager.FontScale.SMALL -> chipGroup.check(R.id.chipSmall)
            AccessibilityManager.FontScale.MEDIUM -> chipGroup.check(R.id.chipMedium)
            AccessibilityManager.FontScale.LARGE -> chipGroup.check(R.id.chipLarge)
            AccessibilityManager.FontScale.EXTRA_LARGE -> chipGroup.check(R.id.chipExtraLarge)
        }
        preview.textSize = 14f * currentScale.scale

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val scale = when (checkedIds[0]) {
                R.id.chipSmall -> AccessibilityManager.FontScale.SMALL
                R.id.chipMedium -> AccessibilityManager.FontScale.MEDIUM
                R.id.chipLarge -> AccessibilityManager.FontScale.LARGE
                R.id.chipExtraLarge -> AccessibilityManager.FontScale.EXTRA_LARGE
                else -> AccessibilityManager.FontScale.MEDIUM
            }
            accessibilityManager.fontScale = scale
            preview.textSize = 14f * scale.scale
        }
    }

    private fun setupHaptic() {
        val switch = findViewById<SwitchMaterial>(R.id.switchHaptic)
        switch.isChecked = accessibilityManager.hapticEnabled
        switch.setOnCheckedChangeListener { _, isChecked ->
            accessibilityManager.hapticEnabled = isChecked
        }

        findViewById<MaterialButton>(R.id.btnTestHaptic).setOnClickListener {
            accessibilityManager.pttHapticFeedback()
        }
    }

    private fun setupTranslationButton() {
        findViewById<MaterialButton>(R.id.btnTranslation).setOnClickListener {
            startActivity(Intent(this, TranslationActivity::class.java))
        }
    }
}
