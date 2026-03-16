package com.db20g.controller.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

/**
 * Generates Morse code (CW) audio for automatic station identification.
 * FCC Part 95 requires GMRS stations to identify with their callsign
 * by voice or CW at the end of communications and every 15 minutes.
 */
class MorseCodeGenerator {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val DEFAULT_TONE_HZ = 700
        private const val DEFAULT_WPM = 20

        // Standard Morse: dit = 1 unit, dah = 3 units, inter-element = 1 unit,
        // inter-character = 3 units, inter-word = 7 units
        // At 20 WPM, 1 unit = 60ms

        private val MORSE_TABLE = mapOf(
            'A' to ".-",    'B' to "-...",  'C' to "-.-.",  'D' to "-..",
            'E' to ".",     'F' to "..-.",  'G' to "--.",   'H' to "....",
            'I' to "..",    'J' to ".---",  'K' to "-.-",   'L' to ".-..",
            'M' to "--",    'N' to "-.",    'O' to "---",   'P' to ".--.",
            'Q' to "--.-",  'R' to ".-.",   'S' to "...",   'T' to "-",
            'U' to "..-",   'V' to "...-",  'W' to ".--",   'X' to "-..-",
            'Y' to "-.--",  'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
            '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
            '8' to "---..", '9' to "----.",
            '/' to "-..-.", ' ' to " "
        )
    }

    var toneHz = DEFAULT_TONE_HZ
    var wordsPerMinute = DEFAULT_WPM

    private var audioTrack: AudioTrack? = null

    /** Duration of one "dit" unit in milliseconds based on WPM */
    private val unitMs: Int get() = 1200 / wordsPerMinute

    /**
     * Generate and play a Morse code representation of the given text.
     * Blocks until playback is complete.
     */
    fun play(text: String) {
        val samples = encode(text)
        if (samples.isEmpty()) return

        release()

        val bufferSize = samples.size * 2
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
        Thread.sleep((samples.size * 1000L / SAMPLE_RATE) + 50)
    }

    /**
     * Play Morse code on a background thread, calling onComplete when done.
     */
    fun playAsync(text: String, onComplete: (() -> Unit)? = null) {
        Thread({
            play(text)
            onComplete?.invoke()
        }, "MorseCW").start()
    }

    /**
     * Encode text into PCM audio samples as Morse code.
     */
    fun encode(text: String): ShortArray {
        val samples = mutableListOf<Short>()

        for ((index, char) in text.uppercase().withIndex()) {
            if (char == ' ') {
                // Inter-word gap (7 units, minus the 3 already added after prev char)
                addSilence(samples, unitMs * 4)
                continue
            }

            val pattern = MORSE_TABLE[char] ?: continue

            for ((elemIdx, element) in pattern.withIndex()) {
                when (element) {
                    '.' -> addTone(samples, unitMs)       // Dit
                    '-' -> addTone(samples, unitMs * 3)   // Dah
                }
                // Inter-element gap (1 unit) — don't add after last element
                if (elemIdx < pattern.length - 1) {
                    addSilence(samples, unitMs)
                }
            }

            // Inter-character gap (3 units) — don't add after last character
            if (index < text.length - 1 && text[index + 1] != ' ') {
                addSilence(samples, unitMs * 3)
            }
        }

        return samples.toShortArray()
    }

    private fun addTone(samples: MutableList<Short>, durationMs: Int) {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        val twoPi = 2.0 * Math.PI
        val amplitude = 0.75

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var value = sin(twoPi * toneHz * t) * amplitude

            // Apply short envelope to avoid clicks
            val ramp = minOf(i, numSamples - i, 30)
            if (ramp < 30) {
                value *= ramp / 30.0
            }

            samples.add((value * Short.MAX_VALUE).toInt().toShort())
        }
    }

    private fun addSilence(samples: MutableList<Short>, durationMs: Int) {
        val numSamples = SAMPLE_RATE * durationMs / 1000
        repeat(numSamples) { samples.add(0) }
    }

    /** Estimated duration in milliseconds for the given text */
    fun estimateDurationMs(text: String): Long {
        var units = 0L
        val upper = text.uppercase()
        for ((index, char) in upper.withIndex()) {
            if (char == ' ') {
                units += 7
                continue
            }
            val pattern = MORSE_TABLE[char] ?: continue
            for ((elemIdx, element) in pattern.withIndex()) {
                units += if (element == '.') 1 else 3
                if (elemIdx < pattern.length - 1) units += 1 // inter-element
            }
            if (index < upper.length - 1 && upper[index + 1] != ' ') {
                units += 3 // inter-character
            }
        }
        return units * unitMs
    }

    fun release() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
