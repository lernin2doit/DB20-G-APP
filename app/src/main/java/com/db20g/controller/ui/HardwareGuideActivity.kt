package com.db20g.controller.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.db20g.controller.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject

class HardwareGuideActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvProgressText: TextView
    private lateinit var sectionsContainer: LinearLayout
    private lateinit var guideData: JSONObject

    private val checkboxes = mutableListOf<CheckBox>()
    private var totalSteps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_guide)

        prefs = getSharedPreferences("hardware_guide_progress", Context.MODE_PRIVATE)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)
        tvProgressText = findViewById(R.id.tvProgressText)
        sectionsContainer = findViewById(R.id.sectionsContainer)

        findViewById<MaterialButton>(R.id.btnResetProgress).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Progress")
                .setMessage("This will uncheck all assembly steps. Continue?")
                .setPositiveButton("Reset") { _, _ -> resetProgress() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        guideData = loadGuideJson()
        buildSections()
        updateProgress()
    }

    private fun loadGuideJson(): JSONObject {
        val inputStream = resources.openRawResource(R.raw.hardware_guide)
        val json = inputStream.bufferedReader().use { it.readText() }
        return JSONObject(json)
    }

    private fun buildSections() {
        val sections = guideData.getJSONArray("sections")
        for (i in 0 until sections.length()) {
            val section = sections.getJSONObject(i)
            val sectionId = section.getString("id")

            when (sectionId) {
                "assembly" -> buildAssemblySection(section)
                "troubleshooting" -> buildTroubleshootingSection(section)
                "connectors" -> buildConnectorsSection(section)
                "wiring_diagram" -> buildDiagramSection(section)
                else -> buildInfoSection(section)
            }
        }
    }

    private fun buildInfoSection(section: JSONObject) {
        val card = createSectionCard()
        val container = card.getChildAt(0) as LinearLayout

        addSectionHeader(container, section.getString("title"))

        if (section.has("content")) {
            val tv = TextView(this).apply {
                text = section.getString("content")
                setTextColor(getThemeColor(android.R.attr.textColorPrimary))
                textSize = 14f
                setPadding(0, 0, 0, dp(8))
            }
            container.addView(tv)
        }

        if (section.has("items")) {
            val items = section.getJSONArray("items")
            for (j in 0 until items.length()) {
                val bullet = TextView(this).apply {
                    text = "• ${items.getString(j)}"
                    setTextColor(getThemeColor(android.R.attr.textColorPrimary))
                    textSize = 13f
                    setPadding(dp(8), dp(2), 0, dp(2))
                }
                container.addView(bullet)
            }
        }

        sectionsContainer.addView(card)
    }

    private fun buildAssemblySection(section: JSONObject) {
        val phases = section.getJSONArray("phases")
        for (p in 0 until phases.length()) {
            val phase = phases.getJSONObject(p)
            val card = createSectionCard()
            val container = card.getChildAt(0) as LinearLayout

            addSectionHeader(container, phase.getString("title"))

            val steps = phase.getJSONArray("steps")
            for (s in 0 until steps.length()) {
                val step = steps.getJSONObject(s)
                val stepId = step.getString("id")
                val stepText = step.getString("text")

                val isTest = stepText.startsWith("TEST:")

                val cb = CheckBox(this).apply {
                    text = stepText
                    setTextColor(getThemeColor(android.R.attr.textColorPrimary))
                    textSize = 13f
                    isChecked = prefs.getBoolean(stepId, false)
                    tag = stepId
                    setPadding(dp(4), dp(4), dp(4), dp(4))

                    if (isTest) {
                        val span = SpannableString(stepText)
                        span.setSpan(StyleSpan(Typeface.BOLD), 0, 5, 0)
                        this.text = span
                    }

                    setOnCheckedChangeListener { _, checked ->
                        prefs.edit().putBoolean(stepId, checked).apply()
                        updateProgress()
                    }
                }

                checkboxes.add(cb)
                totalSteps++
                container.addView(cb)
            }

            sectionsContainer.addView(card)
        }
    }

    private fun buildTroubleshootingSection(section: JSONObject) {
        val issues = section.getJSONArray("issues")
        for (i in 0 until issues.length()) {
            val issue = issues.getJSONObject(i)
            val card = createSectionCard()
            val container = card.getChildAt(0) as LinearLayout

            val problemTv = TextView(this).apply {
                text = "⚠ ${issue.getString("problem")}"
                setTextColor(getThemeColor(android.R.attr.textColorPrimary))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(8))
            }
            container.addView(problemTv)

            val solutions = issue.getJSONArray("solutions")
            for (s in 0 until solutions.length()) {
                val solutionTv = TextView(this).apply {
                    text = "→ ${solutions.getString(s)}"
                    setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                    textSize = 13f
                    setPadding(dp(16), dp(2), 0, dp(2))
                }
                container.addView(solutionTv)
            }

            sectionsContainer.addView(card)
        }
    }

    private fun buildConnectorsSection(section: JSONObject) {
        val subsections = section.getJSONArray("subsections")
        for (i in 0 until subsections.length()) {
            val sub = subsections.getJSONObject(i)
            val card = createSectionCard()
            val container = card.getChildAt(0) as LinearLayout

            addSectionHeader(container, sub.getString("title"))

            if (sub.has("table")) {
                val tableData = sub.getJSONArray("table")
                val table = TableLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    isStretchAllColumns = true
                    setPadding(0, dp(4), 0, 0)
                }

                for (r in 0 until tableData.length()) {
                    val rowData = tableData.getJSONArray(r)
                    val row = TableRow(this)

                    for (c in 0 until rowData.length()) {
                        val cell = TextView(this).apply {
                            text = rowData.getString(c)
                            textSize = 12f
                            setPadding(dp(6), dp(4), dp(6), dp(4))
                            if (r == 0) {
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(getThemeColor(android.R.attr.textColorPrimary))
                            } else {
                                setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                            }
                        }
                        row.addView(cell)
                    }
                    table.addView(row)
                }

                container.addView(table)
            }

            sectionsContainer.addView(card)
        }
    }

    private fun buildDiagramSection(section: JSONObject) {
        val card = createSectionCard()
        val container = card.getChildAt(0) as LinearLayout

        addSectionHeader(container, section.getString("title"))

        if (section.has("diagram")) {
            val hsv = android.widget.HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val tv = TextView(this).apply {
                text = section.getString("diagram")
                setTextColor(getThemeColor(android.R.attr.textColorPrimary))
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundColor(getThemeColor(android.R.attr.colorBackground))
            }

            hsv.addView(tv)
            container.addView(hsv)
        }

        sectionsContainer.addView(card)
    }

    private fun createSectionCard(): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            radius = dp(12).toFloat()
            cardElevation = dp(2).toFloat()
            strokeWidth = 0
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        card.addView(inner)
        return card
    }

    private fun addSectionHeader(container: LinearLayout, title: String) {
        val tv = TextView(this).apply {
            text = title
            setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        container.addView(tv)
    }

    private fun updateProgress() {
        val completed = checkboxes.count { it.isChecked }
        val percent = if (totalSteps > 0) (completed * 100) / totalSteps else 0

        progressBar.progress = percent
        tvProgressText.text = "$completed of $totalSteps steps completed ($percent%)"
    }

    private fun resetProgress() {
        prefs.edit().clear().apply()
        checkboxes.forEach { it.isChecked = false }
        updateProgress()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}
