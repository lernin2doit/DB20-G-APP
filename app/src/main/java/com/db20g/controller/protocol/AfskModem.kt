package com.db20g.controller.protocol

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bell 202 AFSK modem — 1200 baud audio frequency-shift keying.
 *
 * Mark (1) = 1200 Hz
 * Space (0) = 2200 Hz
 *
 * Used for text messaging over standard radio audio channels.
 * Compatible with packet radio conventions (AX.25 framing style).
 *
 * Signal flow:
 *   TX: bytes → NRZI → AFSK tones → AudioTrack
 *   RX: AudioRecord → correlation → NRZI decode → bytes
 */
class AfskModem {

    companion object {
        private const val TAG = "AfskModem"

        const val SAMPLE_RATE = 44100
        const val BAUD_RATE = 1200
        const val MARK_FREQ = 1200  // 1 bit
        const val SPACE_FREQ = 2200 // 0 bit
        const val SAMPLES_PER_BIT = SAMPLE_RATE / BAUD_RATE // 36.75 → 37

        // Framing
        const val PREAMBLE_FLAGS = 32  // Send 32 flag bytes (0x7E) as preamble
        const val FLAG_BYTE = 0x7E
        const val POSTAMBLE_FLAGS = 4

        // Amplitude
        const val TX_AMPLITUDE = 0.8f
    }

    private var demodCallback: ((ByteArray) -> Unit)? = null
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var receiving = false

    fun setDemodulateCallback(callback: (ByteArray) -> Unit) {
        demodCallback = callback
    }

    // ======================== TRANSMIT ========================

    /**
     * Modulate a byte array into AFSK audio samples.
     * Includes preamble flags, bit stuffing, and postamble.
     */
    fun modulate(data: ByteArray): ShortArray {
        val bits = mutableListOf<Int>()

        // Preamble: flag bytes for receiver sync
        repeat(PREAMBLE_FLAGS) {
            addByte(bits, FLAG_BYTE, stuffing = false)
        }

        // Data with bit stuffing (insert 0 after five consecutive 1s)
        var consecutiveOnes = 0
        for (byte in data) {
            for (bitPos in 0 until 8) {
                val bit = (byte.toInt() shr bitPos) and 1
                bits.add(bit)
                if (bit == 1) {
                    consecutiveOnes++
                    if (consecutiveOnes == 5) {
                        bits.add(0) // Stuff bit
                        consecutiveOnes = 0
                    }
                } else {
                    consecutiveOnes = 0
                }
            }
        }

        // Postamble flags
        repeat(POSTAMBLE_FLAGS) {
            addByte(bits, FLAG_BYTE, stuffing = false)
        }

        // NRZI encode: toggle on 0, no change on 1
        val nrziBits = nrziEncode(bits)

        // Generate audio samples
        return generateTones(nrziBits)
    }

    private fun addByte(bits: MutableList<Int>, byte: Int, stuffing: Boolean) {
        var consecutiveOnes = 0
        for (bitPos in 0 until 8) {
            val bit = (byte shr bitPos) and 1
            bits.add(bit)
            if (stuffing) {
                if (bit == 1) {
                    consecutiveOnes++
                    if (consecutiveOnes == 5) {
                        bits.add(0)
                        consecutiveOnes = 0
                    }
                } else {
                    consecutiveOnes = 0
                }
            }
        }
    }

    private fun nrziEncode(bits: List<Int>): List<Int> {
        val result = mutableListOf<Int>()
        var currentLevel = 0
        for (bit in bits) {
            if (bit == 0) {
                currentLevel = 1 - currentLevel // Toggle on 0
            }
            // No change on 1
            result.add(currentLevel)
        }
        return result
    }

    /**
     * Convert NRZI-encoded bits to AFSK audio samples.
     * Uses continuous phase to avoid clicks between tones.
     */
    private fun generateTones(bits: List<Int>): ShortArray {
        val samplesPerBit = SAMPLE_RATE.toDouble() / BAUD_RATE
        val totalSamples = (bits.size * samplesPerBit).roundToInt()
        val samples = ShortArray(totalSamples)

        var phase = 0.0
        var sampleIndex = 0

        for (bit in bits) {
            val freq = if (bit == 1) MARK_FREQ else SPACE_FREQ
            val phaseIncrement = 2.0 * PI * freq / SAMPLE_RATE
            val bitSamples = samplesPerBit.roundToInt()

            for (i in 0 until bitSamples) {
                if (sampleIndex < totalSamples) {
                    val value = (sin(phase) * TX_AMPLITUDE * Short.MAX_VALUE).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    samples[sampleIndex] = value.toShort()
                    phase += phaseIncrement
                    sampleIndex++
                }
            }
        }

        return samples
    }

    /**
     * Transmit modulated data through the audio output.
     * Blocks until transmission is complete.
     */
    fun transmit(data: ByteArray) {
        val samples = modulate(data)

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track
        track.write(samples, 0, samples.size)
        track.play()

        // Wait for playback to complete
        val durationMs = (samples.size * 1000L) / SAMPLE_RATE
        Thread.sleep(durationMs + 100)

        track.stop()
        track.release()
        audioTrack = null

        Log.d(TAG, "Transmitted ${data.size} bytes as ${samples.size} samples (${durationMs}ms)")
    }

    // ======================== RECEIVE ========================

    /**
     * Start listening for incoming AFSK data on the audio input.
     * Calls the demodCallback when a complete frame is decoded.
     */
    fun startReceiving() {
        if (receiving) return
        receiving = true

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 4
        )

        audioRecord = record
        record.startRecording()

        Thread {
            val readBuffer = ShortArray(bufferSize / 2)
            val demodBuffer = ShortArray(SAMPLE_RATE * 5) // 5 second circular buffer
            var writePos = 0

            while (receiving) {
                val read = record.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    // Add to circular demod buffer
                    for (i in 0 until read) {
                        demodBuffer[writePos % demodBuffer.size] = readBuffer[i]
                        writePos++
                    }

                    // Try to demodulate — linearize the circular buffer first
                    val length = minOf(writePos, demodBuffer.size)
                    val linearBuffer = if (writePos > demodBuffer.size) {
                        // Buffer wrapped: copy tail + head into contiguous array
                        val wrapPos = writePos % demodBuffer.size
                        ShortArray(demodBuffer.size).also { buf ->
                            System.arraycopy(demodBuffer, wrapPos, buf, 0, demodBuffer.size - wrapPos)
                            System.arraycopy(demodBuffer, 0, buf, demodBuffer.size - wrapPos, wrapPos)
                        }
                    } else {
                        demodBuffer
                    }
                    val result = demodulate(linearBuffer, length)
                    if (result != null) {
                        Log.d(TAG, "Demodulated ${result.size} bytes")
                        demodCallback?.invoke(result)
                        writePos = 0 // Reset buffer after successful decode
                    }
                }
            }

            record.stop()
            record.release()
            audioRecord = null
        }.start()

        Log.d(TAG, "Receiver started")
    }

    fun stopReceiving() {
        receiving = false
        Log.d(TAG, "Receiver stopped")
    }

    /**
     * Demodulate AFSK audio samples back to bytes.
     * Uses correlation-based detection (Goertzel algorithm for Mark/Space energy).
     */
    fun demodulate(samples: ShortArray, length: Int): ByteArray? {
        val samplesPerBit = SAMPLE_RATE.toDouble() / BAUD_RATE
        val bitCount = (length / samplesPerBit).toInt()
        if (bitCount < 16) return null // Not enough data

        // Detect bits using frequency correlation
        val detectedBits = mutableListOf<Int>()
        for (bitIndex in 0 until bitCount) {
            val start = (bitIndex * samplesPerBit).toInt()
            val end = minOf(((bitIndex + 1) * samplesPerBit).toInt(), length)
            if (end <= start || end > length) break

            val markEnergy = goertzelEnergy(samples, start, end, MARK_FREQ.toDouble())
            val spaceEnergy = goertzelEnergy(samples, start, end, SPACE_FREQ.toDouble())

            detectedBits.add(if (markEnergy > spaceEnergy) 1 else 0)
        }

        // NRZI decode
        val nrziBits = nrziDecode(detectedBits)

        // Find flag sequences and extract data
        return extractFrame(nrziBits)
    }

    /**
     * Goertzel algorithm — efficient single-frequency energy detection.
     */
    private fun goertzelEnergy(samples: ShortArray, start: Int, end: Int, freq: Double): Double {
        val n = end - start
        if (n <= 0) return 0.0

        val k = (0.5 + n * freq / SAMPLE_RATE).toInt()
        val omega = 2.0 * PI * k / n
        val coeff = 2.0 * cos(omega)

        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0

        for (i in start until end) {
            s0 = samples[i].toDouble() / Short.MAX_VALUE + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }

        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }

    private fun nrziDecode(bits: List<Int>): List<Int> {
        val result = mutableListOf<Int>()
        var prev = 0
        for (bit in bits) {
            result.add(if (bit != prev) 0 else 1)
            prev = bit
        }
        return result
    }

    /**
     * Extract a data frame from decoded bits.
     * Looks for flag byte (0x7E) boundaries and removes bit stuffing.
     */
    private fun extractFrame(bits: List<Int>): ByteArray? {
        // Find flag sequence: 01111110
        val flagPattern = listOf(0, 1, 1, 1, 1, 1, 1, 0)

        var flagStart = -1
        for (i in 0..bits.size - 8) {
            if (bits.subList(i, i + 8) == flagPattern) {
                if (flagStart < 0) {
                    flagStart = i + 8 // Data starts after first flag
                } else {
                    // Found end flag — extract data between flags
                    val dataBits = bits.subList(flagStart, i)
                    if (dataBits.size >= 8) {
                        return unstuffAndDecode(dataBits)
                    }
                    flagStart = i + 8 // Reset for next frame
                }
            }
        }
        return null
    }

    /**
     * Remove bit stuffing and convert bits to bytes.
     */
    private fun unstuffAndDecode(bits: List<Int>): ByteArray {
        val unstuffed = mutableListOf<Int>()
        var consecutiveOnes = 0

        for (bit in bits) {
            if (consecutiveOnes == 5 && bit == 0) {
                // Skip stuffed bit
                consecutiveOnes = 0
                continue
            }
            unstuffed.add(bit)
            consecutiveOnes = if (bit == 1) consecutiveOnes + 1 else 0
        }

        // Convert bits to bytes (LSB first)
        val byteCount = unstuffed.size / 8
        val result = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            var byte = 0
            for (bitPos in 0 until 8) {
                byte = byte or (unstuffed[i * 8 + bitPos] shl bitPos)
            }
            result[i] = byte.toByte()
        }
        return result
    }

    fun release() {
        stopReceiving()
        audioTrack?.release()
        audioTrack = null
    }
}
