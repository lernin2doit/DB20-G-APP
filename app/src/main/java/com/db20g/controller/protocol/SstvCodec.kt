package com.db20g.controller.protocol

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Slow-Scan Television (SSTV) encoder/decoder.
 *
 * Supported modes:
 * - Robot36: 320×240, 36 seconds, color (Y/RY/BY)
 * - Martin M1: 320×256, 114 seconds, color (GBR)
 *
 * Signal structure:
 * - 1900 Hz calibration header
 * - VIS (Vertical Interval Signaling) code identifies the mode
 * - Scan lines: frequency 1500-2300 Hz maps to luminance/chrominance 0-255
 *
 * Frequency mapping:
 *   1500 Hz = black (0)
 *   2300 Hz = white (255)
 *   1200 Hz = sync pulse
 *   1900 Hz = calibration
 */
class SstvCodec(private val context: Context) {

    companion object {
        private const val TAG = "SstvCodec"

        const val SAMPLE_RATE = 44100

        // Frequency constants
        const val SYNC_FREQ = 1200.0
        const val BLACK_FREQ = 1500.0
        const val WHITE_FREQ = 2300.0
        const val CALIB_FREQ = 1900.0

        // VIS codes
        const val VIS_ROBOT36 = 8
        const val VIS_MARTIN_M1 = 44

        // Image dimensions
        const val ROBOT36_WIDTH = 320
        const val ROBOT36_HEIGHT = 240
        const val MARTIN_M1_WIDTH = 320
        const val MARTIN_M1_HEIGHT = 256

        // Timing (in seconds)
        const val ROBOT36_LINE_TIME = 0.150       // Total line time
        const val ROBOT36_SYNC_TIME = 0.009       // Sync pulse
        const val ROBOT36_PORCH_TIME = 0.003      // Sync porch
        const val ROBOT36_Y_TIME = 0.088          // Luminance scan
        const val ROBOT36_SEPARATOR_TIME = 0.0045 // Separator
        const val ROBOT36_CR_TIME = 0.044         // Chrominance scan

        const val MARTIN_M1_SYNC_TIME = 0.004862
        const val MARTIN_M1_PORCH_TIME = 0.000572
        const val MARTIN_M1_COLOR_TIME = 0.146432
        const val MARTIN_M1_SEPARATOR_TIME = 0.000572
    }

    // Received image gallery
    private val galleryDir = File(context.filesDir, "sstv_gallery").apply { mkdirs() }
    private var receiveCallback: ((Bitmap, SstvMode) -> Unit)? = null
    @Volatile
    private var receiving = false
    private var audioRecord: AudioRecord? = null

    enum class SstvMode(val visCode: Int, val width: Int, val height: Int, val label: String) {
        ROBOT36(VIS_ROBOT36, ROBOT36_WIDTH, ROBOT36_HEIGHT, "Robot 36"),
        MARTIN_M1(VIS_MARTIN_M1, MARTIN_M1_WIDTH, MARTIN_M1_HEIGHT, "Martin M1")
    }

    fun setReceiveCallback(callback: (Bitmap, SstvMode) -> Unit) {
        receiveCallback = callback
    }

    // ======================== ENCODE ========================

    /**
     * Encode a Bitmap into SSTV audio samples.
     */
    fun encode(bitmap: Bitmap, mode: SstvMode): ShortArray {
        resetPhase()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, mode.width, mode.height, true)
        val samples = mutableListOf<Short>()

        // Leader tone: 300ms of 1900 Hz
        addTone(samples, CALIB_FREQ, 0.300)

        // Break: 10ms of 1200 Hz
        addTone(samples, SYNC_FREQ, 0.010)

        // Leader tone: 300ms of 1900 Hz
        addTone(samples, CALIB_FREQ, 0.300)

        // VIS start bit: 30ms of 1200 Hz
        addTone(samples, SYNC_FREQ, 0.030)

        // VIS code: 8 bits, LSB first, 30ms each
        // 1=1100 Hz, 0=1300 Hz
        var visBits = mode.visCode
        var parity = 0
        for (i in 0 until 7) {
            val bit = (visBits shr i) and 1
            parity = parity xor bit
            addTone(samples, if (bit == 1) 1100.0 else 1300.0, 0.030)
        }
        // Parity bit
        addTone(samples, if (parity == 1) 1100.0 else 1300.0, 0.030)

        // VIS stop bit: 30ms of 1200 Hz
        addTone(samples, SYNC_FREQ, 0.030)

        // Encode scan lines based on mode
        when (mode) {
            SstvMode.ROBOT36 -> encodeRobot36(scaledBitmap, samples)
            SstvMode.MARTIN_M1 -> encodeMartinM1(scaledBitmap, samples)
        }

        scaledBitmap.recycle()

        return samples.toShortArray()
    }

    private fun encodeRobot36(bitmap: Bitmap, samples: MutableList<Short>) {
        for (y in 0 until bitmap.height) {
            // Sync pulse: 9ms at 1200 Hz
            addTone(samples, SYNC_FREQ, ROBOT36_SYNC_TIME)

            // Sync porch: 3ms at 1500 Hz
            addTone(samples, BLACK_FREQ, ROBOT36_PORCH_TIME)

            // Y (luminance) scan: 88ms
            val yTimePerPixel = ROBOT36_Y_TIME / bitmap.width
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val yVal = (0.299 * r + 0.587 * g + 0.114 * b).coerceIn(0.0, 255.0)
                val freq = BLACK_FREQ + (yVal / 255.0) * (WHITE_FREQ - BLACK_FREQ)
                addTone(samples, freq, yTimePerPixel)
            }

            // Separator: 4.5ms alternating for even/odd lines
            addTone(samples, BLACK_FREQ, ROBOT36_SEPARATOR_TIME)

            // Chrominance (R-Y for even lines, B-Y for odd lines): 44ms
            val crTimePerPixel = ROBOT36_CR_TIME / bitmap.width
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val chrVal = if (y % 2 == 0) {
                    // R-Y
                    (0.5 * r - 0.419 * g - 0.081 * b + 128).coerceIn(0.0, 255.0)
                } else {
                    // B-Y
                    (-0.169 * r - 0.331 * g + 0.5 * b + 128).coerceIn(0.0, 255.0)
                }
                val freq = BLACK_FREQ + (chrVal / 255.0) * (WHITE_FREQ - BLACK_FREQ)
                addTone(samples, freq, crTimePerPixel)
            }
        }
    }

    private fun encodeMartinM1(bitmap: Bitmap, samples: MutableList<Short>) {
        for (y in 0 until bitmap.height) {
            // Sync pulse
            addTone(samples, SYNC_FREQ, MARTIN_M1_SYNC_TIME)
            // Sync porch
            addTone(samples, BLACK_FREQ, MARTIN_M1_PORCH_TIME)

            // Green scan
            val colorTimePerPixel = MARTIN_M1_COLOR_TIME / bitmap.width
            for (x in 0 until bitmap.width) {
                val g = Color.green(bitmap.getPixel(x, y)).toDouble()
                val freq = BLACK_FREQ + (g / 255.0) * (WHITE_FREQ - BLACK_FREQ)
                addTone(samples, freq, colorTimePerPixel)
            }

            // Separator
            addTone(samples, BLACK_FREQ, MARTIN_M1_SEPARATOR_TIME)

            // Blue scan
            for (x in 0 until bitmap.width) {
                val b = Color.blue(bitmap.getPixel(x, y)).toDouble()
                val freq = BLACK_FREQ + (b / 255.0) * (WHITE_FREQ - BLACK_FREQ)
                addTone(samples, freq, colorTimePerPixel)
            }

            // Separator
            addTone(samples, BLACK_FREQ, MARTIN_M1_SEPARATOR_TIME)

            // Red scan
            for (x in 0 until bitmap.width) {
                val r = Color.red(bitmap.getPixel(x, y)).toDouble()
                val freq = BLACK_FREQ + (r / 255.0) * (WHITE_FREQ - BLACK_FREQ)
                addTone(samples, freq, colorTimePerPixel)
            }

            // Separator
            addTone(samples, BLACK_FREQ, MARTIN_M1_SEPARATOR_TIME)
        }
    }

    /**
     * Transmit an SSTV-encoded image through the audio output.
     */
    fun transmit(bitmap: Bitmap, mode: SstvMode) {
        val samples = encode(bitmap, mode)
        Log.d(TAG, "Encoded ${mode.label} image: ${samples.size} samples, " +
                "${samples.size * 1000L / SAMPLE_RATE}ms")

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
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

        track.write(samples, 0, samples.size)
        track.play()

        val durationMs = (samples.size * 1000L) / SAMPLE_RATE
        Thread.sleep(durationMs + 200)

        track.stop()
        track.release()
        Log.d(TAG, "SSTV transmission complete")
    }

    // ======================== DECODE ========================

    /**
     * Start listening for incoming SSTV images.
     * Uses audio input to detect calibration header and decode scan lines.
     */
    fun startReceiving() {
        if (receiving) return
        receiving = true

        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )

        val record = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufSize * 4
        )

        audioRecord = record
        record.startRecording()

        Thread {
            val readBuf = ShortArray(bufSize / 2)
            val accumulator = mutableListOf<Short>()
            var headerDetected = false

            while (receiving) {
                val read = record.read(readBuf, 0, readBuf.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        accumulator.add(readBuf[i])
                    }

                    // Look for calibration header (1900 Hz leader)
                    if (!headerDetected && accumulator.size > SAMPLE_RATE) {
                        val freq = estimateFrequency(accumulator.toShortArray(),
                            accumulator.size - SAMPLE_RATE / 4, accumulator.size)
                        if (freq in 1850.0..1950.0) {
                            headerDetected = true
                            Log.d(TAG, "SSTV header detected at $freq Hz")
                        }
                    }

                    // If we have enough data for a full image, try to decode
                    if (headerDetected && accumulator.size > SAMPLE_RATE * 30) {
                        val decoded = decodeImage(accumulator.toShortArray())
                        if (decoded != null) {
                            val (bitmap, mode) = decoded
                            saveToGallery(bitmap, mode)
                            receiveCallback?.invoke(bitmap, mode)
                            accumulator.clear()
                            headerDetected = false
                        }
                    }

                    // Prevent unbounded growth
                    if (accumulator.size > SAMPLE_RATE * 120) {
                        accumulator.clear()
                        headerDetected = false
                    }
                }
            }

            record.stop()
            record.release()
            audioRecord = null
        }.start()

        Log.d(TAG, "SSTV receiver started")
    }

    fun stopReceiving() {
        receiving = false
    }

    /**
     * Decode an SSTV image from audio samples.
     * Detects VIS code to determine mode, then decodes scan lines.
     */
    fun decodeImage(samples: ShortArray): Pair<Bitmap, SstvMode>? {
        // Find VIS code
        val visResult = decodeVis(samples) ?: return null
        val (visCode, dataStart) = visResult

        val mode = SstvMode.entries.find { it.visCode == visCode }
        if (mode == null) {
            Log.w(TAG, "Unknown VIS code: $visCode")
            return null
        }

        Log.d(TAG, "Decoding ${mode.label} image (VIS=$visCode)")

        return when (mode) {
            SstvMode.ROBOT36 -> {
                val bitmap = decodeRobot36(samples, dataStart, mode)
                if (bitmap != null) Pair(bitmap, mode) else null
            }
            SstvMode.MARTIN_M1 -> {
                val bitmap = decodeMartinM1(samples, dataStart, mode)
                if (bitmap != null) Pair(bitmap, mode) else null
            }
        }
    }

    private fun decodeVis(samples: ShortArray): Pair<Int, Int>? {
        // Search for 1200 Hz start bit
        val samplesPerBit = (SAMPLE_RATE * 0.030).toInt()

        for (offset in 0 until samples.size - samplesPerBit * 10) {
            val freq = estimateFrequency(samples, offset, offset + samplesPerBit)
            if (freq in 1150.0..1250.0) {
                // Might be VIS start bit — decode 8 data bits
                var vis = 0
                var valid = true
                for (bit in 0 until 8) {
                    val bitStart = offset + samplesPerBit * (bit + 1)
                    val bitEnd = bitStart + samplesPerBit
                    if (bitEnd > samples.size) { valid = false; break }

                    val bitFreq = estimateFrequency(samples, bitStart, bitEnd)
                    if (bitFreq in 1050.0..1150.0) {
                        vis = vis or (1 shl bit) // 1100 Hz = 1
                    } else if (bitFreq in 1250.0..1350.0) {
                        // 1300 Hz = 0, already 0
                    } else {
                        valid = false
                        break
                    }
                }

                if (valid) {
                    val dataStart = offset + samplesPerBit * 10 // After stop bit
                    return Pair(vis and 0x7F, dataStart) // 7-bit VIS code
                }
            }
        }
        return null
    }

    private fun decodeRobot36(samples: ShortArray, startOffset: Int, mode: SstvMode): Bitmap? {
        val bitmap = Bitmap.createBitmap(mode.width, mode.height, Bitmap.Config.ARGB_8888)
        val samplesPerLine = (ROBOT36_LINE_TIME * SAMPLE_RATE).toInt()
        val ySamplesPerPixel = (ROBOT36_Y_TIME * SAMPLE_RATE / mode.width).toInt()
        val syncSamples = (ROBOT36_SYNC_TIME * SAMPLE_RATE).toInt()
        val porchSamples = (ROBOT36_PORCH_TIME * SAMPLE_RATE).toInt()

        // Robot36: even lines have Y then Cr, odd lines have Y then Cb
        val yImage = Array(mode.height) { DoubleArray(mode.width) { 128.0 } }
        val crLine = DoubleArray(mode.width) { 128.0 }
        val cbLine = DoubleArray(mode.width) { 128.0 }

        // First pass: decode Y for every line and chroma where available
        for (y in 0 until mode.height) {
            val lineStart = startOffset + y * samplesPerLine
            val yStart = lineStart + syncSamples + porchSamples

            for (x in 0 until mode.width) {
                val samplePos = yStart + x * ySamplesPerPixel
                if (samplePos + ySamplesPerPixel > samples.size) {
                    break
                }
                val freq = estimateFrequency(samples, samplePos, samplePos + ySamplesPerPixel)
                yImage[y][x] = ((freq - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ) * 255).coerceIn(0.0, 255.0)
            }

            // Chroma follows Y data — Robot36 interleaves Cr (even) and Cb (odd)
            val chromaStart = yStart + mode.width * ySamplesPerPixel + porchSamples
            val chromaSamplesPerPixel = (ROBOT36_CR_TIME * SAMPLE_RATE / mode.width).toInt()
            if (y % 2 == 0) {
                // Even line: decode Cr (R-Y)
                for (x in 0 until mode.width) {
                    val samplePos = chromaStart + x * chromaSamplesPerPixel
                    if (samplePos + chromaSamplesPerPixel > samples.size) break
                    val freq = estimateFrequency(samples, samplePos, samplePos + chromaSamplesPerPixel)
                    crLine[x] = ((freq - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ) * 255).coerceIn(0.0, 255.0)
                }
            } else {
                // Odd line: decode Cb (B-Y)
                for (x in 0 until mode.width) {
                    val samplePos = chromaStart + x * chromaSamplesPerPixel
                    if (samplePos + chromaSamplesPerPixel > samples.size) break
                    val freq = estimateFrequency(samples, samplePos, samplePos + chromaSamplesPerPixel)
                    cbLine[x] = ((freq - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ) * 255).coerceIn(0.0, 255.0)
                }
            }

            // After odd line, we have both Cr (from previous even line) and Cb — render both lines in color
            if (y % 2 == 1 && y >= 1) {
                for (row in (y - 1)..y) {
                    for (x in 0 until mode.width) {
                        val yv = yImage[row][x]
                        val cr = crLine[x] - 128.0
                        val cb = cbLine[x] - 128.0
                        val r = (yv + 1.402 * cr).coerceIn(0.0, 255.0).toInt()
                        val g = (yv - 0.344136 * cb - 0.714136 * cr).coerceIn(0.0, 255.0).toInt()
                        val b = (yv + 1.772 * cb).coerceIn(0.0, 255.0).toInt()
                        bitmap.setPixel(x, row, Color.rgb(r, g, b))
                    }
                }
            }
        }

        // Handle last line if height is odd (no Cb pair) — render as grayscale
        if (mode.height % 2 == 1) {
            val lastY = mode.height - 1
            for (x in 0 until mode.width) {
                val yv = yImage[lastY][x].toInt().coerceIn(0, 255)
                bitmap.setPixel(x, lastY, Color.rgb(yv, yv, yv))
            }
        }

        return bitmap
    }

    private fun decodeMartinM1(samples: ShortArray, startOffset: Int, mode: SstvMode): Bitmap? {
        val bitmap = Bitmap.createBitmap(mode.width, mode.height, Bitmap.Config.ARGB_8888)
        val syncSamples = (MARTIN_M1_SYNC_TIME * SAMPLE_RATE).toInt()
        val porchSamples = (MARTIN_M1_PORCH_TIME * SAMPLE_RATE).toInt()
        val colorSamples = (MARTIN_M1_COLOR_TIME * SAMPLE_RATE).toInt()
        val sepSamples = (MARTIN_M1_SEPARATOR_TIME * SAMPLE_RATE).toInt()
        val colorSamplesPerPixel = colorSamples / mode.width
        val lineSize = syncSamples + porchSamples + colorSamples * 3 + sepSamples * 3

        for (y in 0 until mode.height) {
            val lineStart = startOffset + y * lineSize
            val greenStart = lineStart + syncSamples + porchSamples
            val blueStart = greenStart + colorSamples + sepSamples
            val redStart = blueStart + colorSamples + sepSamples

            for (x in 0 until mode.width) {
                val gPos = greenStart + x * colorSamplesPerPixel
                val bPos = blueStart + x * colorSamplesPerPixel
                val rPos = redStart + x * colorSamplesPerPixel

                if (rPos + colorSamplesPerPixel > samples.size) {
                    return bitmap
                }

                val gFreq = estimateFrequency(samples, gPos, gPos + colorSamplesPerPixel)
                val bFreq = estimateFrequency(samples, bPos, bPos + colorSamplesPerPixel)
                val rFreq = estimateFrequency(samples, rPos, rPos + colorSamplesPerPixel)

                val g = ((gFreq - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ) * 255).coerceIn(0.0, 255.0).toInt()
                val b = ((bFreq - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ) * 255).coerceIn(0.0, 255.0).toInt()
                val r = ((rFreq - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ) * 255).coerceIn(0.0, 255.0).toInt()

                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        return bitmap
    }

    /**
     * Estimate the dominant frequency in a range of samples using zero-crossing detection.
     */
    private fun estimateFrequency(samples: ShortArray, start: Int, end: Int): Double {
        val s = start.coerceIn(0, samples.size - 1)
        val e = end.coerceIn(s + 1, samples.size)
        if (e - s < 4) return 0.0

        var crossings = 0
        for (i in s + 1 until e) {
            if ((samples[i - 1] >= 0 && samples[i] < 0) ||
                (samples[i - 1] < 0 && samples[i] >= 0)) {
                crossings++
            }
        }

        val duration = (e - s).toDouble() / SAMPLE_RATE
        return crossings / (2.0 * duration)
    }

    // ======================== GALLERY ========================

    fun saveToGallery(bitmap: Bitmap, mode: SstvMode): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(galleryDir, "sstv_${mode.name}_$timestamp.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.d(TAG, "Saved SSTV image to ${file.name}")
        return file
    }

    fun getGallery(): List<SstvImage> {
        return galleryDir.listFiles()
            ?.filter { it.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val modeName = file.nameWithoutExtension
                    .removePrefix("sstv_")
                    .substringBefore("_")
                SstvImage(
                    file = file,
                    mode = SstvMode.entries.find { it.name == modeName } ?: SstvMode.ROBOT36,
                    timestamp = file.lastModified(),
                    thumbnail = loadThumbnail(file)
                )
            } ?: emptyList()
    }

    fun deleteImage(file: File) {
        file.delete()
    }

    private fun loadThumbnail(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }

    fun release() {
        stopReceiving()
    }

    data class SstvImage(
        val file: File,
        val mode: SstvMode,
        val timestamp: Long,
        val thumbnail: Bitmap?
    ) {
        fun formattedTime(): String {
            return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))
        }
    }

    // ======================== TONE GENERATION ========================

    private var phase = 0.0

    private fun resetPhase() {
        phase = 0.0
    }

    private fun addTone(samples: MutableList<Short>, frequency: Double, duration: Double) {
        val numSamples = (duration * SAMPLE_RATE).roundToInt()
        val phaseIncrement = 2.0 * PI * frequency / SAMPLE_RATE

        for (i in 0 until numSamples) {
            val value = (sin(phase) * 0.8 * Short.MAX_VALUE).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples.add(value.toShort())
            phase += phaseIncrement
        }
    }
}
