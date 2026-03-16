package com.db20g.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.db20g.controller.R
import com.db20g.controller.audio.SpectrumAnalyzer
import com.db20g.controller.databinding.ActivitySpectrumBinding

class SpectrumActivity : AppCompatActivity(), SpectrumAnalyzer.SpectrumListener {

    private lateinit var binding: ActivitySpectrumBinding
    private lateinit var analyzer: SpectrumAnalyzer
    private var isRunning = false

    // Spectrum bar chart bitmap
    private var spectrumBitmap: Bitmap? = null
    private var spectrumCanvas: Canvas? = null
    private val barPaint = Paint().apply { color = Color.GREEN; style = Paint.Style.FILL }
    private val bgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val gridPaint = Paint().apply { color = 0x44FFFFFF.toInt(); strokeWidth = 1f }
    private val labelPaint = Paint().apply {
        color = 0xAAFFFFFF.toInt()
        textSize = 20f
        isAntiAlias = true
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startAnalyzer() else
            Toast.makeText(this, "Audio permission required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeId = getSharedPreferences("app_settings", MODE_PRIVATE).getInt("theme_id", 0)
        when (themeId) {
            1 -> setTheme(R.style.Theme_DB20GController_AMOLED)
            2 -> setTheme(R.style.Theme_DB20GController_RedLight)
        }

        super.onCreate(savedInstanceState)
        binding = ActivitySpectrumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analyzer = SpectrumAnalyzer(this)
        analyzer.spectrumListener = this

        setupToolbar()
        setupControls()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupControls() {
        binding.btnStartStop.setOnClickListener {
            if (isRunning) stopAnalyzer() else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                    startAnalyzer()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        binding.btnScreenshot.setOnClickListener {
            val file = analyzer.exportScreenshot()
            if (file != null) {
                Toast.makeText(this, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }

        // FFT size chips
        binding.chipFft512.setOnCheckedChangeListener { _, checked -> if (checked) updateFftSize(512) }
        binding.chipFft1024.setOnCheckedChangeListener { _, checked -> if (checked) updateFftSize(1024) }
        binding.chipFft2048.setOnCheckedChangeListener { _, checked -> if (checked) updateFftSize(2048) }
        binding.chipFft4096.setOnCheckedChangeListener { _, checked -> if (checked) updateFftSize(4096) }

        // Palette chips
        binding.chipHeat.setOnCheckedChangeListener { _, checked ->
            if (checked) analyzer.colorPalette = SpectrumAnalyzer.ColorPalette.HEAT
        }
        binding.chipViridis.setOnCheckedChangeListener { _, checked ->
            if (checked) analyzer.colorPalette = SpectrumAnalyzer.ColorPalette.VIRIDIS
        }
        binding.chipGray.setOnCheckedChangeListener { _, checked ->
            if (checked) analyzer.colorPalette = SpectrumAnalyzer.ColorPalette.GRAYSCALE
        }
        binding.chipGreen.setOnCheckedChangeListener { _, checked ->
            if (checked) analyzer.colorPalette = SpectrumAnalyzer.ColorPalette.GREEN
        }
    }

    private fun updateFftSize(size: Int) {
        val wasRunning = isRunning
        if (wasRunning) stopAnalyzer()
        analyzer.fftSize = size
        binding.toolbar.subtitle = "FFT: $size | Freq res: ${SpectrumAnalyzer.SAMPLE_RATE / size} Hz"
        if (wasRunning) startAnalyzer()
    }

    private fun startAnalyzer() {
        isRunning = true
        binding.btnStartStop.text = "STOP"
        binding.btnStartStop.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        binding.btnScreenshot.isEnabled = true

        // Init waterfall at view dimensions (default 512x256 until measured)
        val wfWidth = binding.ivWaterfall.width.let { if (it > 0) it else 512 }
        val wfHeight = binding.ivWaterfall.height.let { if (it > 0) it else 256 }
        analyzer.initWaterfall(wfWidth, wfHeight)

        // Init spectrum bitmap
        val spWidth = binding.ivSpectrum.width.let { if (it > 0) it else 512 }
        val spHeight = binding.ivSpectrum.height.let { if (it > 0) it else 120 }
        spectrumBitmap = Bitmap.createBitmap(spWidth, spHeight, Bitmap.Config.ARGB_8888)
        spectrumCanvas = Canvas(spectrumBitmap!!)

        analyzer.start()
    }

    private fun stopAnalyzer() {
        isRunning = false
        analyzer.stop()
        binding.btnStartStop.text = "START"
        binding.btnStartStop.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    // ======================== SpectrumListener ========================

    override fun onSpectrumData(magnitudes: FloatArray, freqResolution: Float) {
        val bmp = spectrumBitmap ?: return
        val canvas = spectrumCanvas ?: return

        runOnUiThread {
            canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), bgPaint)

            val barWidth = bmp.width.toFloat() / magnitudes.size
            val height = bmp.height.toFloat()

            for (i in magnitudes.indices) {
                val normalized = ((magnitudes[i] + 120f) / 120f).coerceIn(0f, 1f)
                val barHeight = normalized * height

                barPaint.color = when {
                    normalized > 0.8f -> Color.RED
                    normalized > 0.6f -> Color.YELLOW
                    else -> Color.GREEN
                }

                canvas.drawRect(
                    i * barWidth, height - barHeight,
                    (i + 1) * barWidth, height,
                    barPaint
                )
            }

            // Draw frequency grid lines
            val freqs = intArrayOf(500, 1000, 2000, 5000, 10000)
            for (freq in freqs) {
                val bin = freq / freqResolution
                val x = (bin / magnitudes.size) * bmp.width
                if (x in 0f..bmp.width.toFloat()) {
                    canvas.drawLine(x, 0f, x, height, gridPaint)
                    canvas.drawText("${freq / 1000}k", x + 2, 14f, labelPaint)
                }
            }

            binding.ivSpectrum.setImageBitmap(bmp)
        }
    }

    override fun onWaterfallUpdate(bitmap: Bitmap) {
        runOnUiThread {
            binding.ivWaterfall.setImageBitmap(bitmap)
        }
    }

    override fun onToneDetected(toneHz: Double, toneType: String) {
        runOnUiThread {
            binding.tvTone.text = "Tone: $toneType %.1f Hz".format(toneHz)
        }
    }

    override fun onPeakFrequency(freqHz: Float, magnitudeDb: Float) {
        runOnUiThread {
            binding.tvPeakFreq.text = "Peak: %.0f Hz (%.1f dB)".format(freqHz, magnitudeDb)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analyzer.release()
        spectrumBitmap?.recycle()
    }
}
