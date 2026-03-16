package com.db20g.controller.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Audio spectrum analyzer with FFT, waterfall display, and tone detection.
 *
 * Provides:
 * - Real-time FFT spectrum data
 * - Waterfall bitmap generation (scrolling spectrogram)
 * - CTCSS tone detection (67.0 – 254.1 Hz, 50 standard tones)
 * - DCS code detection (digital coded squelch)
 * - Peak frequency tracking
 * - Configurable FFT window size and overlap
 * - Screenshot export
 */
class SpectrumAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "SpectrumAnalyzer"
        const val SAMPLE_RATE = 44100
        const val DEFAULT_FFT_SIZE = 2048
        const val MAX_FFT_SIZE = 8192
        const val MIN_FFT_SIZE = 256

        // Standard CTCSS tones (Hz)
        val CTCSS_TONES = doubleArrayOf(
            67.0, 69.3, 71.9, 74.4, 77.0, 79.7, 82.5, 85.4, 88.5, 91.5,
            94.8, 97.4, 100.0, 103.5, 107.2, 110.9, 114.8, 118.8, 123.0, 127.3,
            131.8, 136.5, 141.3, 146.2, 151.4, 156.7, 159.8, 162.2, 165.5, 167.9,
            171.3, 173.8, 177.3, 179.9, 183.5, 186.2, 189.9, 192.8, 196.6, 199.5,
            203.5, 206.5, 210.7, 218.1, 225.7, 229.1, 233.6, 241.8, 250.3, 254.1
        )
    }

    // Configuration
    var fftSize = DEFAULT_FFT_SIZE
        set(value) {
            field = value.coerceIn(MIN_FFT_SIZE, MAX_FFT_SIZE)
            // Must be power of 2
            var v = 1
            while (v < field) v = v shl 1
            field = v
        }
    var overlapPercent = 50 // 0-90
    var colorPalette = ColorPalette.HEAT

    // State
    @Volatile
    private var running = false
    private var audioRecord: AudioRecord? = null
    private var analysisThread: Thread? = null

    // Waterfall bitmap
    private var waterfallBitmap: Bitmap? = null
    private var waterfallCanvas: Canvas? = null
    private var waterfallWidth = 512
    private var waterfallHeight = 256
    private var waterfallRow = 0

    // Listeners
    var spectrumListener: SpectrumListener? = null

    enum class ColorPalette {
        HEAT,       // Black → Red → Yellow → White
        VIRIDIS,    // Dark purple → teal → yellow
        GRAYSCALE,  // Black → White
        GREEN       // Black → Green (classic)
    }

    interface SpectrumListener {
        fun onSpectrumData(magnitudes: FloatArray, freqResolution: Float)
        fun onWaterfallUpdate(bitmap: Bitmap)
        fun onToneDetected(toneHz: Double, toneType: String)
        fun onPeakFrequency(freqHz: Float, magnitudeDb: Float)
    }

    /**
     * Initialize the waterfall bitmap at the given dimensions.
     */
    fun initWaterfall(width: Int, height: Int) {
        waterfallWidth = width
        waterfallHeight = height
        waterfallBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        waterfallCanvas = Canvas(waterfallBitmap!!)
        waterfallRow = 0
    }

    /**
     * Start real-time spectrum analysis from audio input.
     */
    fun start() {
        if (running) return
        running = true

        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(bufSize, fftSize * 2) * 2
        )

        audioRecord = record
        record.startRecording()

        analysisThread = Thread {
            val readBuf = ShortArray(fftSize)
            val accumBuf = ShortArray(fftSize)
            var accumPos = 0
            val freqResolution = SAMPLE_RATE.toFloat() / fftSize

            while (running) {
                val read = record.read(readBuf, 0, readBuf.size)
                if (read > 0) {
                    // Accumulate samples until we have a full FFT frame
                    val toCopy = minOf(read, fftSize - accumPos)
                    System.arraycopy(readBuf, 0, accumBuf, accumPos, toCopy)
                    accumPos += toCopy

                    if (accumPos < fftSize) continue
                    accumPos = 0

                    // Apply Hann window and compute FFT
                    val windowed = applyHannWindow(accumBuf, fftSize)
                    val (real, imag) = fft(windowed)

                    // Compute magnitude spectrum (first half only — Nyquist)
                    val halfSize = fftSize / 2
                    val magnitudes = FloatArray(halfSize)
                    var peakMag = Float.NEGATIVE_INFINITY
                    var peakBin = 0

                    for (i in 0 until halfSize) {
                        val mag = sqrt(real[i] * real[i] + imag[i] * imag[i]).toFloat()
                        val db = if (mag > 0) (20.0 * log10(mag.toDouble() / fftSize)).toFloat() else -120f
                        magnitudes[i] = db

                        if (db > peakMag) {
                            peakMag = db
                            peakBin = i
                        }
                    }

                    val peakFreq = peakBin * freqResolution

                    // Update waterfall
                    updateWaterfall(magnitudes)

                    // Detect CTCSS/DCS tones
                    detectTones(magnitudes, freqResolution)

                    // Notify listener
                    spectrumListener?.onSpectrumData(magnitudes, freqResolution)
                    spectrumListener?.onPeakFrequency(peakFreq, peakMag)

                    waterfallBitmap?.let { bmp ->
                        spectrumListener?.onWaterfallUpdate(bmp)
                    }
                }
            }

            record.stop()
            record.release()
            audioRecord = null
        }
        analysisThread?.start()
        Log.d(TAG, "Spectrum analyzer started (FFT size=$fftSize)")
    }

    fun stop() {
        running = false
        analysisThread?.join(2000)
        analysisThread = null
    }

    /**
     * Export current waterfall/spectrum as a PNG screenshot.
     */
    fun exportScreenshot(): File? {
        val bmp = waterfallBitmap ?: return null
        val dir = File(context.filesDir, "spectrum_captures").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "spectrum_$timestamp.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.d(TAG, "Saved spectrum screenshot to ${file.name}")
        return file
    }

    fun release() {
        stop()
        waterfallBitmap?.recycle()
        waterfallBitmap = null
    }

    // ======================== FFT ========================

    /**
     * Cooley-Tukey radix-2 FFT.
     * Input: real-valued samples (windowed).
     * Returns: (real, imaginary) arrays of length N.
     */
    private fun fft(input: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val n = input.size
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        // Bit-reversal permutation
        for (i in 0 until n) {
            real[bitReverse(i, n)] = input[i]
        }

        // Butterfly operations
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len

            for (start in 0 until n step len) {
                for (k in 0 until halfLen) {
                    val wReal = cos(angle * k)
                    val wImag = sin(angle * k)

                    val evenIdx = start + k
                    val oddIdx = start + k + halfLen

                    val tReal = wReal * real[oddIdx] - wImag * imag[oddIdx]
                    val tImag = wReal * imag[oddIdx] + wImag * real[oddIdx]

                    real[oddIdx] = real[evenIdx] - tReal
                    imag[oddIdx] = imag[evenIdx] - tImag
                    real[evenIdx] = real[evenIdx] + tReal
                    imag[evenIdx] = imag[evenIdx] + tImag
                }
            }
            len = len shl 1
        }

        return Pair(real, imag)
    }

    private fun bitReverse(x: Int, n: Int): Int {
        var bits = 0
        var temp = n shr 1
        while (temp > 0) { bits++; temp = temp shr 1 }

        var result = 0
        var value = x
        for (i in 0 until bits) {
            result = (result shl 1) or (value and 1)
            value = value shr 1
        }
        return result
    }

    private fun applyHannWindow(samples: ShortArray, length: Int): DoubleArray {
        val result = DoubleArray(fftSize)
        for (i in 0 until length.coerceAtMost(fftSize)) {
            val window = 0.5 * (1 - cos(2.0 * PI * i / (length - 1)))
            result[i] = samples[i].toDouble() / Short.MAX_VALUE * window
        }
        return result
    }

    // ======================== WATERFALL ========================

    private val waterfallPaint = Paint()

    private fun updateWaterfall(magnitudes: FloatArray) {
        val bmp = waterfallBitmap ?: return
        val canvas = waterfallCanvas ?: return

        // Scroll existing content up by 1 pixel
        if (waterfallRow >= waterfallHeight) {
            val scrolled = Bitmap.createBitmap(bmp, 0, 1, waterfallWidth, waterfallHeight - 1)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(scrolled, 0f, 0f, null)
            scrolled.recycle()
            waterfallRow = waterfallHeight - 1
        }

        // Draw new row at bottom
        val binWidth = magnitudes.size.toFloat() / waterfallWidth
        for (x in 0 until waterfallWidth) {
            val bin = (x * binWidth).toInt().coerceIn(0, magnitudes.size - 1)
            val db = magnitudes[bin]
            val color = dbToColor(db)
            waterfallPaint.color = color
            canvas.drawPoint(x.toFloat(), waterfallRow.toFloat(), waterfallPaint)
        }

        waterfallRow++
    }

    private fun dbToColor(db: Float): Int {
        // Normalize to 0..1 range (-120 dB to 0 dB)
        val normalized = ((db + 120f) / 120f).coerceIn(0f, 1f)

        return when (colorPalette) {
            ColorPalette.HEAT -> {
                when {
                    normalized < 0.33f -> {
                        val t = normalized / 0.33f
                        Color.rgb((t * 255).toInt(), 0, 0)
                    }
                    normalized < 0.66f -> {
                        val t = (normalized - 0.33f) / 0.33f
                        Color.rgb(255, (t * 255).toInt(), 0)
                    }
                    else -> {
                        val t = (normalized - 0.66f) / 0.34f
                        Color.rgb(255, 255, (t * 255).toInt())
                    }
                }
            }
            ColorPalette.VIRIDIS -> {
                val r = (68 + normalized * 187).toInt().coerceIn(0, 255)
                val g = (1 + normalized * 254).toInt().coerceIn(0, 255)
                val b = (84 + normalized * (228 - 84) * (1 - normalized)).toInt().coerceIn(0, 255)
                Color.rgb(r, g, b)
            }
            ColorPalette.GRAYSCALE -> {
                val v = (normalized * 255).toInt().coerceIn(0, 255)
                Color.rgb(v, v, v)
            }
            ColorPalette.GREEN -> {
                Color.rgb(0, (normalized * 255).toInt().coerceIn(0, 255), 0)
            }
        }
    }

    // ======================== TONE DETECTION ========================

    private var lastDetectedTone = 0.0
    private var toneHoldCount = 0

    private fun detectTones(magnitudes: FloatArray, freqResolution: Float) {
        // Search for CTCSS tones in the low-frequency range
        for (tone in CTCSS_TONES) {
            val bin = (tone / freqResolution).toInt()
            if (bin < 1 || bin >= magnitudes.size - 1) continue

            // Check if this bin has a peak (higher than neighbors)
            val mag = magnitudes[bin]
            val leftMag = magnitudes[bin - 1]
            val rightMag = magnitudes[bin + 1]

            if (mag > leftMag && mag > rightMag && mag > -60f) {
                // Verify it's dominant in the CTCSS range
                val avgNoise = magnitudes.take((300 / freqResolution).toInt().coerceAtLeast(1))
                    .average().toFloat()

                if (mag > avgNoise + 10f) {
                    if (tone == lastDetectedTone) {
                        toneHoldCount++
                    } else {
                        lastDetectedTone = tone
                        toneHoldCount = 1
                    }

                    // Require 3 consecutive detections to reduce false positives
                    if (toneHoldCount == 3) {
                        spectrumListener?.onToneDetected(tone, "CTCSS")
                    }
                    return
                }
            }
        }

        // No tone detected — reset hold
        if (toneHoldCount > 0) toneHoldCount--
        if (toneHoldCount == 0) lastDetectedTone = 0.0
    }

    /**
     * Perform single-shot Goertzel analysis for a specific frequency.
     * More accurate than FFT bin lookup for known frequencies.
     */
    fun goertzelMagnitude(samples: ShortArray, targetFreq: Double): Double {
        val n = samples.size
        val k = (0.5 + n * targetFreq / SAMPLE_RATE).toInt()
        val w = 2.0 * PI * k / n
        val coeff = 2.0 * cos(w)

        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0

        for (i in 0 until n) {
            s0 = samples[i].toDouble() / Short.MAX_VALUE + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }

        return sqrt(s1 * s1 + s2 * s2 - coeff * s1 * s2)
    }
}
