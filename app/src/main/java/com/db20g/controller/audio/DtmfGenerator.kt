package com.db20g.controller.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

/**
 * Generates DTMF (Dual-Tone Multi-Frequency) tones for sending
 * through the radio's audio path. Standard telephone/radio DTMF.
 */
class DtmfGenerator {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TONE_DURATION_MS = 250
        private const val INTER_TONE_GAP_MS = 100
        private const val AMPLITUDE = 0.8

        // DTMF frequency pairs: [low freq, high freq]
        private val DTMF_FREQS = mapOf(
            '1' to Pair(697, 1209), '2' to Pair(697, 1336), '3' to Pair(697, 1477), 'A' to Pair(697, 1633),
            '4' to Pair(770, 1209), '5' to Pair(770, 1336), '6' to Pair(770, 1477), 'B' to Pair(770, 1633),
            '7' to Pair(852, 1209), '8' to Pair(852, 1336), '9' to Pair(852, 1477), 'C' to Pair(852, 1633),
            '*' to Pair(941, 1209), '0' to Pair(941, 1336), '#' to Pair(941, 1477), 'D' to Pair(941, 1633),
        )
    }

    private var audioTrack: AudioTrack? = null
    var toneDurationMs = TONE_DURATION_MS
    var interToneGapMs = INTER_TONE_GAP_MS

    /**
     * Play a single DTMF tone for the given character.
     */
    fun playTone(char: Char) {
        val freqs = DTMF_FREQS[char.uppercaseChar()] ?: return
        val samples = generateDualTone(freqs.first, freqs.second, toneDurationMs)
        playBuffer(samples)
    }

    /**
     * Play a sequence of DTMF tones (e.g. a phone number or code).
     */
    fun playSequence(sequence: String, onTonePlayed: ((Char, Int) -> Unit)? = null) {
        Thread({
            for ((index, char) in sequence.withIndex()) {
                if (DTMF_FREQS.containsKey(char.uppercaseChar())) {
                    onTonePlayed?.invoke(char, index)
                    playTone(char)
                    Thread.sleep(interToneGapMs.toLong())
                }
            }
        }, "DTMF-Sequence").start()
    }

    /**
     * Generate a single-frequency tone (for alert/roger beep).
     */
    fun playSingleTone(frequencyHz: Int, durationMs: Int = 200) {
        val samples = generateDualTone(frequencyHz, 0, durationMs)
        playBuffer(samples)
    }

    private fun generateDualTone(freq1: Int, freq2: Int, durationMs: Int): ShortArray {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val samples = ShortArray(numSamples)
        val twoPi = 2.0 * Math.PI

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var value = sin(twoPi * freq1 * t) * AMPLITUDE
            if (freq2 > 0) {
                value += sin(twoPi * freq2 * t) * AMPLITUDE
                value /= 2.0  // Normalize when mixing two tones
            }
            // Apply short fade-in/fade-out to avoid clicks
            val envelope = when {
                i < 50 -> i / 50.0
                i > numSamples - 50 -> (numSamples - i) / 50.0
                else -> 1.0
            }
            samples[i] = (value * envelope * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun playBuffer(samples: ShortArray) {
        release()

        val bufferSize = samples.size * 2  // 16-bit = 2 bytes per sample
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(samples, 0, samples.size)
        audioTrack?.play()

        // Wait for playback to complete
        Thread.sleep((samples.size * 1000L / SAMPLE_RATE) + 10)
    }

    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
