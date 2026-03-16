package com.db20g.controller.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.R
import com.db20g.controller.translation.RadioTranslator
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TranslationActivity : AppCompatActivity(), RadioTranslator.TranslationListener {

    private lateinit var translator: RadioTranslator

    // Views
    private lateinit var switchTranslation: SwitchMaterial
    private lateinit var layoutListening: View
    private lateinit var tvListeningStatus: TextView
    private lateinit var tvOriginalText: TextView
    private lateinit var tvTranslatedText: TextView
    private lateinit var tvConfidenceDisplay: TextView
    private lateinit var rvModels: RecyclerView
    private lateinit var rvHistory: RecyclerView

    private lateinit var modelAdapter: ModelAdapter
    private lateinit var historyAdapter: HistoryAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            translator.startListening()
        } else {
            Toast.makeText(this, "Microphone permission required for translation", Toast.LENGTH_LONG).show()
            switchTranslation.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeId = getSharedPreferences("app_settings", MODE_PRIVATE).getInt("theme_id", 0)
        when (themeId) {
            1 -> setTheme(R.style.Theme_DB20GController_AMOLED)
            2 -> setTheme(R.style.Theme_DB20GController_RedLight)
            else -> setTheme(R.style.Theme_DB20GController)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translation)

        translator = RadioTranslator(this)
        translator.setTranslationListener(this)
        translator.initialize()
        translator.loadHistory()

        initViews()
        setupTranslationToggle()
        setupLanguageSelection()
        setupConfidenceThreshold()
        setupToggles()
        setupModels()
        setupHistory()
    }

    private fun initViews() {
        switchTranslation = findViewById(R.id.switchTranslation)
        layoutListening = findViewById(R.id.layoutListening)
        tvListeningStatus = findViewById(R.id.tvListeningStatus)
        tvOriginalText = findViewById(R.id.tvOriginalText)
        tvTranslatedText = findViewById(R.id.tvTranslatedText)
        tvConfidenceDisplay = findViewById(R.id.tvConfidenceDisplay)
        rvModels = findViewById(R.id.rvModels)
        rvHistory = findViewById(R.id.rvHistory)
    }

    private fun setupTranslationToggle() {
        switchTranslation.isChecked = translator.enabled
        switchTranslation.setOnCheckedChangeListener { _, checked ->
            translator.enabled = checked
            if (checked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                    translator.startListening()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            } else {
                translator.stopListening()
            }
        }
    }

    private fun setupLanguageSelection() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupLanguage)

        // Set initial selection
        when (translator.preferredLanguage) {
            "en" -> chipGroup.check(R.id.chipEnglish)
            "es" -> chipGroup.check(R.id.chipSpanish)
            "fr" -> chipGroup.check(R.id.chipFrench)
            "de" -> chipGroup.check(R.id.chipGerman)
            "pt" -> chipGroup.check(R.id.chipPortuguese)
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            translator.preferredLanguage = when {
                checkedIds.contains(R.id.chipSpanish) -> "es"
                checkedIds.contains(R.id.chipFrench) -> "fr"
                checkedIds.contains(R.id.chipGerman) -> "de"
                checkedIds.contains(R.id.chipPortuguese) -> "pt"
                else -> "en"
            }
        }
    }

    private fun setupConfidenceThreshold() {
        val seekBar = findViewById<SeekBar>(R.id.seekConfidence)
        val tvConf = findViewById<TextView>(R.id.tvConfidence)

        val initial = (translator.confidenceThreshold * 100).toInt()
        seekBar.progress = initial
        tvConf.text = "$initial%"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvConf.text = "$progress%"
                if (fromUser) {
                    translator.confidenceThreshold = progress / 100f
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupToggles() {
        val switchTts = findViewById<SwitchMaterial>(R.id.switchTts)
        val switchAutoOutgoing = findViewById<SwitchMaterial>(R.id.switchAutoOutgoing)

        switchTts.isChecked = translator.ttsEnabled
        switchTts.setOnCheckedChangeListener { _, checked ->
            translator.ttsEnabled = checked
        }

        switchAutoOutgoing.isChecked = translator.autoTranslateOutgoing
        switchAutoOutgoing.setOnCheckedChangeListener { _, checked ->
            translator.autoTranslateOutgoing = checked
        }
    }

    private fun setupModels() {
        modelAdapter = ModelAdapter(mutableListOf()) { model ->
            if (model.downloaded) {
                translator.deleteModel(model.languageCode) { success ->
                    runOnUiThread {
                        if (success) refreshModels()
                        else Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                translator.downloadModel(model.languageCode) { success ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, "${model.languageName} model downloaded", Toast.LENGTH_SHORT).show()
                            refreshModels()
                        } else {
                            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        rvModels.layoutManager = LinearLayoutManager(this)
        rvModels.adapter = modelAdapter
        refreshModels()
    }

    private fun refreshModels() {
        translator.getAvailableModels { models ->
            runOnUiThread { modelAdapter.updateModels(models) }
        }
    }

    private fun setupHistory() {
        historyAdapter = HistoryAdapter(translator.getHistory().reversed().toMutableList())
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        findViewById<MaterialButton>(R.id.btnExportHistory).setOnClickListener {
            val csv = translator.exportHistoryCsv()
            val file = File(filesDir, "translation_history.csv")
            file.writeText(csv)
            Toast.makeText(this, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }

        findViewById<MaterialButton>(R.id.btnClearHistory).setOnClickListener {
            translator.clearHistory()
            historyAdapter.clear()
        }
    }

    // --- TranslationListener Callbacks ---

    override fun onSpeechRecognized(text: String, language: String) {
        runOnUiThread {
            tvOriginalText.text = text
            findViewById<TextView>(R.id.tvOriginalLabel).text = "Original ($language):"
        }
    }

    override fun onTranslationResult(result: RadioTranslator.TranslationResult) {
        runOnUiThread {
            tvOriginalText.text = result.originalText
            tvTranslatedText.text = result.translatedText
            tvConfidenceDisplay.text = String.format(
                "%s → %s  |  Confidence: %.0f%%",
                result.sourceLanguage.uppercase(), result.targetLanguage.uppercase(),
                result.confidence * 100
            )
            historyAdapter.addResult(result)
        }
    }

    override fun onTranslationError(error: String) {
        runOnUiThread {
            tvConfidenceDisplay.text = error
            tvConfidenceDisplay.setTextColor(0xFFFF5252.toInt())
        }
    }

    override fun onListeningStateChanged(isListening: Boolean) {
        runOnUiThread {
            layoutListening.visibility = if (isListening) View.VISIBLE else View.GONE
            tvListeningStatus.text = if (isListening) "Listening..." else "Stopped"
        }
    }

    override fun onTtsSpeaking(text: String) {
        runOnUiThread {
            tvListeningStatus.text = "Speaking translation..."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator.destroy()
    }

    // === Adapters ===

    inner class ModelAdapter(
        private val models: MutableList<RadioTranslator.ModelInfo>,
        private val onAction: (RadioTranslator.ModelInfo) -> Unit
    ) : RecyclerView.Adapter<ModelAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvModelName)
            val tvStatus: TextView = view.findViewById(R.id.tvModelStatus)
            val btnAction: MaterialButton = view.findViewById(R.id.btnModelAction)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_language_model, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = models[position]
            holder.tvName.text = m.languageName
            if (m.downloaded) {
                holder.tvStatus.text = "Downloaded ✓"
                holder.tvStatus.setTextColor(0xFF4CAF50.toInt())
                holder.btnAction.text = "Delete"
            } else {
                holder.tvStatus.text = "~${m.sizeEstimateMb}MB"
                holder.tvStatus.setTextColor(0xFF888888.toInt())
                holder.btnAction.text = "Download"
            }
            holder.btnAction.setOnClickListener { onAction(m) }
        }

        override fun getItemCount() = models.size

        fun updateModels(newModels: List<RadioTranslator.ModelInfo>) {
            models.clear()
            models.addAll(newModels)
            notifyDataSetChanged()
        }
    }

    inner class HistoryAdapter(
        private val items: MutableList<RadioTranslator.TranslationResult>
    ) : RecyclerView.Adapter<HistoryAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvLangs: TextView = view.findViewById(R.id.tvHistoryLangs)
            val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
            val tvOriginal: TextView = view.findViewById(R.id.tvHistoryOriginal)
            val tvTranslated: TextView = view.findViewById(R.id.tvHistoryTranslated)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_translation_history, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = items[position]
            holder.tvLangs.text = "${r.sourceLanguage.uppercase()} → ${r.targetLanguage.uppercase()}"
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
            holder.tvTime.text = sdf.format(Date(r.timestamp))
            holder.tvOriginal.text = r.originalText
            holder.tvTranslated.text = r.translatedText
        }

        override fun getItemCount() = items.size

        fun addResult(result: RadioTranslator.TranslationResult) {
            items.add(0, result)
            notifyItemInserted(0)
        }

        fun clear() {
            items.clear()
            notifyDataSetChanged()
        }
    }
}
