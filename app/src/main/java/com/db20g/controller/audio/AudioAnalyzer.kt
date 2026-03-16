package com.db20g.controller.audio

import kotlin.math.*

class AudioAnalyzer(private val sampleRate: Int = 44100) {

    data class AnalysisResult(
        val rms: Float,
        val peak: Int,
        val noiseFloor: Float,
        val snr: Float,
        val qualityScore: Int,        // 0-100
        val fftMagnitudes: FloatArray, // magnitude spectrum
        val fftFrequencies: FloatArray // corresponding frequencies
    )

    data class SignalEvent(
        val timestamp: Long,
        val channelNumber: Int,
        val rms: Float,
        val peak: Int,
        val qualityScore: Int,
        val durationMs: Long
    )

    interface AnalysisListener {
        fun onAnalysisResult(result: AnalysisResult)
        fun onSignalEvent(event: SignalEvent)
    }

    private var listener: AnalysisListener? = null
    private val signalHistory = mutableListOf<SignalEvent>()
    private val noiseFloorSamples = mutableListOf<Float>()
    private var baselineNoiseFloor: Float = 0f
    private var currentSignalStart: Long = 0L
    private var isSignalActive = false
    private var currentChannel = 0

    // Equalizer settings
    var bassGain: Float = 0f      // dB, -12 to +12
    var trebleGain: Float = 0f    // dB, -12 to +12
    var eqEnabled: Boolean = false

    fun setListener(l: AnalysisListener?) {
        listener = l
    }

    fun setCurrentChannel(channel: Int) {
        currentChannel = channel
    }

    fun analyze(samples: ShortArray, count: Int): AnalysisResult {
        val actualCount = minOf(count, samples.size)
        if (actualCount == 0) {
            return AnalysisResult(0f, 0, baselineNoiseFloor.coerceAtLeast(100f), 0f, 0, FloatArray(0), FloatArray(0))
        }

        // Calculate RMS and peak
        var sumSquares = 0.0
        var peak = 0
        for (i in 0 until actualCount) {
            val s = samples[i].toInt()
            sumSquares += s.toLong() * s.toLong()
            val abs = abs(s)
            if (abs > peak) peak = abs
        }
        val rms = sqrt(sumSquares / actualCount).toFloat()

        // Update noise floor estimate (running average of low-energy frames)
        if (rms < 500f) {
            noiseFloorSamples.add(rms)
            if (noiseFloorSamples.size > 50) noiseFloorSamples.removeAt(0)
            baselineNoiseFloor = noiseFloorSamples.average().toFloat()
        }

        val noiseFloor = if (baselineNoiseFloor > 0f) baselineNoiseFloor else 100f

        // SNR in dB
        val snr = if (noiseFloor > 0f && rms > noiseFloor) {
            20f * log10(rms / noiseFloor)
        } else {
            0f
        }

        // Quality score based on SNR and peak
        val qualityScore = calculateQualityScore(rms, peak, snr)

        // FFT
        val fftSize = nextPowerOf2(actualCount)
        val fftResult = performFft(samples, actualCount, fftSize)
        val magnitudes = fftResult.first
        val frequencies = fftResult.second

        // Signal event tracking
        trackSignalEvents(rms, peak, qualityScore)

        val result = AnalysisResult(rms, peak, noiseFloor, snr, qualityScore, magnitudes, frequencies)
        listener?.onAnalysisResult(result)
        return result
    }

    fun applyEqualizer(samples: ShortArray, count: Int): ShortArray {
        if (!eqEnabled) return samples

        val output = samples.copyOf()
        val actualCount = minOf(count, samples.size)

        // Simple 2-band biquad filter for bass (< 300Hz) and treble (> 3000Hz)
        val bassLinear = 10f.pow(bassGain / 20f)
        val trebleLinear = 10f.pow(trebleGain / 20f)

        // Low-pass coefficient for bass emphasis (~300Hz)
        val bassCutoff = 300.0 / sampleRate
        val bassAlpha = (2.0 * PI * bassCutoff / (2.0 * PI * bassCutoff + 1.0)).toFloat()

        // High-pass coefficient for treble (~3000Hz)
        val trebleCutoff = 3000.0 / sampleRate
        val trebleAlpha = (1.0 / (2.0 * PI * trebleCutoff + 1.0)).toFloat()

        var lowPass = 0f
        var highPass = 0f
        var prevSample = 0f

        for (i in 0 until actualCount) {
            val s = samples[i].toFloat()

            // Bass: low-pass filter
            lowPass += bassAlpha * (s - lowPass)
            val bassComponent = lowPass * bassLinear

            // Treble: high-pass filter
            highPass = trebleAlpha * (highPass + s - prevSample)
            val trebleComponent = highPass * trebleLinear
            prevSample = s

            // Mid passthrough + adjusted bass + adjusted treble
            val mid = s - lowPass - highPass
            val result = (mid + bassComponent + trebleComponent).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[i] = result.toShort()
        }

        return output
    }

    fun getSignalHistory(): List<SignalEvent> = signalHistory.toList()

    fun clearHistory() {
        signalHistory.clear()
        noiseFloorSamples.clear()
        baselineNoiseFloor = 0f
    }

    fun exportHistoryCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("Timestamp,Channel,RMS,Peak,Quality,Duration_ms")
        for (event in signalHistory) {
            sb.appendLine("${event.timestamp},${event.channelNumber},${event.rms},${event.peak},${event.qualityScore},${event.durationMs}")
        }
        return sb.toString()
    }

    private fun calculateQualityScore(rms: Float, peak: Int, snr: Float): Int {
        // Score components:
        // - SNR contribution (0-40 points): >20dB is excellent
        val snrScore = (snr * 2f).coerceIn(0f, 40f)

        // - Level contribution (0-30 points): good audio level without clipping
        val levelRatio = rms / 10000f
        val levelScore = when {
            levelRatio < 0.05f -> levelRatio * 200f  // Too quiet
            levelRatio > 0.8f -> 30f * (1f - (levelRatio - 0.8f) * 5f) // Clipping risk
            else -> 30f * (levelRatio / 0.4f).coerceAtMost(1f)
        }

        // - Crest factor (0-30 points): peak/RMS ratio (healthy voice ~12-18 dB)
        val crestFactor = if (rms > 0) peak.toFloat() / rms else 0f
        val crestScore = when {
            crestFactor < 2f -> crestFactor * 7.5f
            crestFactor > 10f -> 30f - (crestFactor - 10f) * 2f
            else -> 30f * ((crestFactor - 2f) / 8f).coerceAtMost(1f)
        }

        return (snrScore + levelScore.coerceIn(0f, 30f) + crestScore.coerceIn(0f, 30f))
            .toInt().coerceIn(0, 100)
    }

    private fun trackSignalEvents(rms: Float, peak: Int, qualityScore: Int) {
        val threshold = baselineNoiseFloor * 3f + 200f

        if (rms > threshold && !isSignalActive) {
            // Signal start
            isSignalActive = true
            currentSignalStart = System.currentTimeMillis()
        } else if (rms <= threshold && isSignalActive) {
            // Signal end
            isSignalActive = false
            val duration = System.currentTimeMillis() - currentSignalStart
            if (duration > 200) { // Ignore very short blips
                val event = SignalEvent(
                    timestamp = currentSignalStart,
                    channelNumber = currentChannel,
                    rms = rms,
                    peak = peak,
                    qualityScore = qualityScore,
                    durationMs = duration
                )
                signalHistory.add(0, event)
                if (signalHistory.size > 200) signalHistory.removeAt(signalHistory.lastIndex)
                listener?.onSignalEvent(event)
            }
        }
    }

    private fun performFft(samples: ShortArray, count: Int, fftSize: Int): Pair<FloatArray, FloatArray> {
        // Zero-padded real/imag arrays
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)

        // Apply Hanning window and copy
        for (i in 0 until minOf(count, fftSize)) {
            val window = 0.5f * (1f - cos(2f * PI.toFloat() * i / (count - 1)))
            real[i] = samples[i].toFloat() * window
        }

        // In-place Cooley-Tukey FFT
        fftInPlace(real, imag, fftSize)

        // Compute magnitudes for positive frequencies only
        val halfSize = fftSize / 2
        val magnitudes = FloatArray(halfSize)
        val frequencies = FloatArray(halfSize)
        val freqResolution = sampleRate.toFloat() / fftSize

        for (i in 0 until halfSize) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i]) / halfSize
            frequencies[i] = i * freqResolution
        }

        return Pair(magnitudes, frequencies)
    }

    private fun fftInPlace(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                var temp = real[i]; real[i] = real[j]; real[j] = temp
                temp = imag[i]; imag[i] = imag[j]; imag[j] = temp
            }
            var m = n / 2
            while (m >= 1 && j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        // FFT butterfly
        var step = 1
        while (step < n) {
            val halfStep = step
            step *= 2
            val angle = -PI.toFloat() / halfStep
            val wReal = cos(angle)
            val wImag = sin(angle)

            for (group in 0 until n step step) {
                var tReal = 1f
                var tImag = 0f
                for (pair in 0 until halfStep) {
                    val a = group + pair
                    val b = a + halfStep

                    val bReal = real[b] * tReal - imag[b] * tImag
                    val bImag = real[b] * tImag + imag[b] * tReal

                    real[b] = real[a] - bReal
                    imag[b] = imag[a] - bImag
                    real[a] = real[a] + bReal
                    imag[a] = imag[a] + bImag

                    val newTReal = tReal * wReal - tImag * wImag
                    tImag = tReal * wImag + tImag * wReal
                    tReal = newTReal
                }
            }
        }
    }

    private fun nextPowerOf2(n: Int): Int {
        var v = n - 1
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return (v + 1).coerceIn(64, 4096)
    }
}
