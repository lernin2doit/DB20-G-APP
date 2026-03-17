package com.db20g.controller.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import com.db20g.controller.R
import com.db20g.controller.protocol.RadioSettings
import com.db20g.controller.protocol.ScanMode
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.RadioGroup
import android.widget.TextView

class RadioSettingsActivity : AppCompatActivity() {

    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_settings)

        val sliderSquelch = findViewById<Slider>(R.id.sliderSquelch)
        val tvSquelchValue = findViewById<TextView>(R.id.tvSquelchValue)
        val sliderVox = findViewById<Slider>(R.id.sliderVox)
        val tvVoxValue = findViewById<TextView>(R.id.tvVoxValue)
        val sliderBacklight = findViewById<Slider>(R.id.sliderBacklight)
        val tvBacklightValue = findViewById<TextView>(R.id.tvBacklightValue)
        val sliderTot = findViewById<Slider>(R.id.sliderTot)
        val tvTotValue = findViewById<TextView>(R.id.tvTotValue)
        val switchBeep = findViewById<SwitchMaterial>(R.id.switchBeep)
        val switchRoger = findViewById<SwitchMaterial>(R.id.switchRoger)
        val switchAutoLock = findViewById<SwitchMaterial>(R.id.switchAutoLock)
        val rgScanMode = findViewById<RadioGroup>(R.id.rgScanMode)

        sliderSquelch.addOnChangeListener { _, value, fromUser ->
            tvSquelchValue.text = value.toInt().toString()
            if (fromUser) applySettings()
        }

        sliderVox.addOnChangeListener { _, value, fromUser ->
            tvVoxValue.text = if (value.toInt() == 0) "Off" else value.toInt().toString()
            if (fromUser) applySettings()
        }

        sliderBacklight.addOnChangeListener { _, value, fromUser ->
            tvBacklightValue.text = value.toInt().toString()
            if (fromUser) applySettings()
        }

        sliderTot.addOnChangeListener { _, value, fromUser ->
            val seconds = value.toInt() * 30
            tvTotValue.text = if (seconds == 0) "Off" else "${seconds}s"
            if (fromUser) applySettings()
        }

        switchBeep.setOnCheckedChangeListener { _, _ -> applySettings() }
        switchRoger.setOnCheckedChangeListener { _, _ -> applySettings() }
        switchAutoLock.setOnCheckedChangeListener { _, _ -> applySettings() }
        rgScanMode.setOnCheckedChangeListener { _, _ -> applySettings() }

        viewModel.settings.observe(this) { settings ->
            if (settings != null) populateSettings(settings)
        }

        viewModel.radioIdent.observe(this) { ident ->
            if (ident.isNotEmpty()) {
                findViewById<TextView>(R.id.tvRadioIdent).text = "Ident: $ident"
                findViewById<TextView>(R.id.tvRadioModel).text = "Radioddity DB20-G"
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun populateSettings(settings: RadioSettings) {
        val sliderSquelch = findViewById<Slider>(R.id.sliderSquelch)
        val tvSquelchValue = findViewById<TextView>(R.id.tvSquelchValue)
        val sliderVox = findViewById<Slider>(R.id.sliderVox)
        val tvVoxValue = findViewById<TextView>(R.id.tvVoxValue)
        val sliderBacklight = findViewById<Slider>(R.id.sliderBacklight)
        val tvBacklightValue = findViewById<TextView>(R.id.tvBacklightValue)
        val sliderTot = findViewById<Slider>(R.id.sliderTot)
        val tvTotValue = findViewById<TextView>(R.id.tvTotValue)

        sliderSquelch.value = settings.squelch.toFloat().coerceIn(0f, 9f)
        tvSquelchValue.text = settings.squelch.toString()

        sliderVox.value = settings.vox.toFloat().coerceIn(0f, 10f)
        tvVoxValue.text = if (settings.vox == 0) "Off" else settings.vox.toString()

        sliderBacklight.value = settings.backlight.toFloat().coerceIn(0f, 9f)
        tvBacklightValue.text = settings.backlight.toString()

        sliderTot.value = settings.timeoutTimer.toFloat().coerceIn(0f, 10f)
        val totSeconds = settings.timeoutTimer * 30
        tvTotValue.text = if (totSeconds == 0) "Off" else "${totSeconds}s"

        findViewById<SwitchMaterial>(R.id.switchBeep).isChecked = settings.beep
        findViewById<SwitchMaterial>(R.id.switchRoger).isChecked = settings.roger
        findViewById<SwitchMaterial>(R.id.switchAutoLock).isChecked = settings.autoLock

        when (settings.scanMode) {
            ScanMode.TIME_OPERATED -> findViewById<android.widget.RadioButton>(R.id.rbScanTime).isChecked = true
            ScanMode.CARRIER_OPERATED -> findViewById<android.widget.RadioButton>(R.id.rbScanCarrier).isChecked = true
            ScanMode.SEARCH -> findViewById<android.widget.RadioButton>(R.id.rbScanSearch).isChecked = true
        }
    }

    private fun applySettings() {
        val current = viewModel.settings.value ?: return
        val updated = current.copy(
            squelch = findViewById<Slider>(R.id.sliderSquelch).value.toInt(),
            vox = findViewById<Slider>(R.id.sliderVox).value.toInt(),
            backlight = findViewById<Slider>(R.id.sliderBacklight).value.toInt(),
            timeoutTimer = findViewById<Slider>(R.id.sliderTot).value.toInt(),
            beep = findViewById<SwitchMaterial>(R.id.switchBeep).isChecked,
            roger = findViewById<SwitchMaterial>(R.id.switchRoger).isChecked,
            autoLock = findViewById<SwitchMaterial>(R.id.switchAutoLock).isChecked,
            scanMode = when (findViewById<RadioGroup>(R.id.rgScanMode).checkedRadioButtonId) {
                R.id.rbScanCarrier -> ScanMode.CARRIER_OPERATED
                R.id.rbScanSearch -> ScanMode.SEARCH
                else -> ScanMode.TIME_OPERATED
            }
        )
        viewModel.updateSettings(updated)
    }
}
