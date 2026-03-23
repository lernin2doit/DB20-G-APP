package com.db20g.controller.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.db20g.controller.R
import com.db20g.controller.serial.PinoutConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class PinoutConfigActivity : AppCompatActivity() {

    private lateinit var pinoutConfig: PinoutConfig
    private lateinit var presetSpinner: Spinner
    private lateinit var pinoutTable: TableLayout
    private lateinit var tvPresetDescription: TextView
    private val pinSpinners = mutableMapOf<Int, Spinner>()
    private var ignoreSpinnerCallbacks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)

        pinoutConfig = PinoutConfig(this)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Back button
        root.addView(MaterialButton(this).apply {
            text = "← Back"
            setOnClickListener { finish() }
        })

        // Title
        root.addView(TextView(this).apply {
            text = "Hardware Pinout Configuration"
            textSize = 20f
            setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            setPadding(0, dp(8), 0, dp(4))
        })

        // Warning
        val warningCard = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
            setCardBackgroundColor(0x33FF9800.toInt())
        }
        warningCard.addView(TextView(this).apply {
            text = "⚠️ The RJ-45 pinout has not been verified on actual DB20-G hardware. " +
                "The default pinout is an assumed standard based on Kenwood-mobile-compatible radios. " +
                "Verify your radio's pinout with a multimeter before wiring. " +
                "See WIRING.md for the verification procedure."
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            textSize = 13f
        })
        root.addView(warningCard)

        // Preset selector
        root.addView(TextView(this).apply {
            text = "Preset"
            textSize = 14f
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(0, dp(12), 0, dp(4))
        })

        presetSpinner = Spinner(this)
        val presetNames = pinoutConfig.presets.map { it.name } + PinoutConfig.PRESET_CUSTOM
        presetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presetNames)
        root.addView(presetSpinner)

        // Description
        tvPresetDescription = TextView(this).apply {
            textSize = 13f
            setTextColor(getThemeColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(tvPresetDescription)

        // Pinout table card
        val tableCard = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        val tableContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Table header
        root.addView(TextView(this).apply {
            text = "Pin Assignments"
            textSize = 14f
            setTextColor(getThemeColor(com.google.android.material.R.attr.colorPrimary))
            setPadding(0, dp(12), 0, dp(4))
        })

        pinoutTable = TableLayout(this).apply {
            isStretchAllColumns = true
        }

        // Header row
        val headerRow = TableRow(this)
        headerRow.addView(makeHeaderCell("Pin"))
        headerRow.addView(makeHeaderCell("Signal"))
        pinoutTable.addView(headerRow)

        // Pin rows (1-8)
        val signalNames = PinoutConfig.Signal.entries.map { pinoutConfig.signalDisplayName(it) }
        for (pin in 1..8) {
            val row = TableRow(this)
            row.addView(TextView(this).apply {
                text = "Pin $pin"
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            })

            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, signalNames)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (!ignoreSpinnerCallbacks) onPinChanged()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            pinSpinners[pin] = spinner
            row.addView(spinner)
            pinoutTable.addView(row)
        }

        tableContainer.addView(pinoutTable)
        tableCard.addView(tableContainer)
        root.addView(tableCard)

        // Info text
        root.addView(TextView(this).apply {
            text = "\nNote: Changing the pinout configuration here updates what the app " +
                "expects for documentation and guidance. If you use a non-default pinout, " +
                "you must also rewire the physical RJ-45 connections on the PCB to match."
            textSize = 12f
            setTextColor(getThemeColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(8), 0, dp(80))
        })

        scrollView.addView(root)
        setContentView(scrollView)

        // Set up preset spinner listener
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ignoreSpinnerCallbacks) return
                val preset = when (position) {
                    0 -> pinoutConfig.db20gDefault
                    1 -> pinoutConfig.kenwoodMobile
                    else -> null
                }
                if (preset != null) {
                    loadPinout(preset)
                    pinoutConfig.currentPinout = preset
                }
                updateDescription()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Load current pinout
        loadCurrentPinout()
    }

    private fun loadCurrentPinout() {
        val current = pinoutConfig.currentPinout
        val presetIndex = pinoutConfig.presets.indexOfFirst { it.name == current.name }
        ignoreSpinnerCallbacks = true
        presetSpinner.setSelection(if (presetIndex >= 0) presetIndex else 2) // 2 = Custom
        loadPinout(current)
        updateDescription()
        ignoreSpinnerCallbacks = false
    }

    private fun loadPinout(pinout: PinoutConfig.Pinout) {
        ignoreSpinnerCallbacks = true
        val signals = PinoutConfig.Signal.entries
        for (pin in 1..8) {
            val signal = pinout.signalForPin(pin)
            val index = signals.indexOf(signal)
            pinSpinners[pin]?.setSelection(if (index >= 0) index else 0)
        }
        ignoreSpinnerCallbacks = false
    }

    private fun onPinChanged() {
        // Build current mapping from spinners
        val signals = PinoutConfig.Signal.entries
        val pins = mutableMapOf<Int, PinoutConfig.Signal>()
        for (pin in 1..8) {
            val selectedIndex = pinSpinners[pin]?.selectedItemPosition ?: 0
            pins[pin] = signals[selectedIndex]
        }

        // Check if it matches a preset
        val pinout = PinoutConfig.Pinout(PinoutConfig.PRESET_CUSTOM, pins)
        val matchingPreset = pinoutConfig.presets.find { it.pins == pins }

        val savePinout = if (matchingPreset != null) {
            ignoreSpinnerCallbacks = true
            presetSpinner.setSelection(pinoutConfig.presets.indexOf(matchingPreset))
            ignoreSpinnerCallbacks = false
            matchingPreset
        } else {
            ignoreSpinnerCallbacks = true
            presetSpinner.setSelection(2) // Custom
            ignoreSpinnerCallbacks = false
            pinout
        }

        pinoutConfig.currentPinout = savePinout
        updateDescription()
    }

    private fun updateDescription() {
        val name = pinoutConfig.currentPresetName
        tvPresetDescription.text = when (name) {
            PinoutConfig.PRESET_DB20G_DEFAULT ->
                "Assumed standard pinout for the Radioddity DB20-G. Not verified on hardware."
            PinoutConfig.PRESET_KENWOOD_MOBILE ->
                "Common Kenwood-mobile-compatible pinout (Pin 1=MIC, Pin 5=PTT, Pin 8=GND)."
            else ->
                "Custom pin mapping. Ensure your physical wiring matches this configuration."
        }
    }

    private fun makeHeaderCell(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setTextColor(getThemeColor(android.R.attr.textColorSecondary))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun getThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
