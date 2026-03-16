package com.db20g.controller.audio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records radio audio (RX and TX) to WAV files with metadata.
 *
 * Features:
 * - Record received audio with timestamps
 * - Record transmitted audio
 * - Per-channel recording toggle
 * - Playback with channel/time metadata
 * - Export recordings as WAV
 * - Activity log: PTT events, channel changes, repeater connections, callsign IDs
 * - Storage management (auto-delete after X days, max size)
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val PREFS_NAME = "audio_recorder_prefs"
        private const val KEY_ENABLED = "recording_enabled"
        private const val KEY_RECORD_RX = "record_rx"
        private const val KEY_RECORD_TX = "record_tx"
        private const val KEY_MAX_DAYS = "max_days"
        private const val KEY_MAX_SIZE_MB = "max_size_mb"

        private const val SAMPLE_RATE = RadioAudioEngine.SAMPLE_RATE
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16

        private const val DEFAULT_MAX_DAYS = 30
        private const val DEFAULT_MAX_SIZE_MB = 500
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val recordingsDir: File
        get() = File(context.filesDir, "recordings").also { it.mkdirs() }

    private val logFile: File
        get() = File(recordingsDir, "activity_log.txt")

    // --- Settings ---

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var recordRx: Boolean
        get() = prefs.getBoolean(KEY_RECORD_RX, true)
        set(value) = prefs.edit().putBoolean(KEY_RECORD_RX, value).apply()

    var recordTx: Boolean
        get() = prefs.getBoolean(KEY_RECORD_TX, true)
        set(value) = prefs.edit().putBoolean(KEY_RECORD_TX, value).apply()

    var maxRetentionDays: Int
        get() = prefs.getInt(KEY_MAX_DAYS, DEFAULT_MAX_DAYS)
        set(value) = prefs.edit().putInt(KEY_MAX_DAYS, value.coerceIn(1, 365)).apply()

    var maxStorageMb: Int
        get() = prefs.getInt(KEY_MAX_SIZE_MB, DEFAULT_MAX_SIZE_MB)
        set(value) = prefs.edit().putInt(KEY_MAX_SIZE_MB, value.coerceIn(50, 5000)).apply()

    // Per-channel recording toggles (channel number → enabled)
    private val channelRecordingEnabled = mutableMapOf<Int, Boolean>()

    fun setChannelRecordingEnabled(channel: Int, enabled: Boolean) {
        channelRecordingEnabled[channel] = enabled
        prefs.edit().putBoolean("channel_rec_$channel", enabled).apply()
    }

    fun isChannelRecordingEnabled(channel: Int): Boolean {
        return channelRecordingEnabled.getOrPut(channel) {
            prefs.getBoolean("channel_rec_$channel", true) // default: record all
        }
    }

    // --- Recording State ---

    private var currentOutputStream: FileOutputStream? = null
    private var currentFile: File? = null
    private var currentRecordingType: RecordingType? = null
    private var currentChannelNumber: Int = 0
    private var currentChannelName: String = ""
    private var bytesWritten: Long = 0
    private var isRecording = false

    enum class RecordingType { RX, TX }

    data class RecordingInfo(
        val file: File,
        val type: RecordingType,
        val channelNumber: Int,
        val channelName: String,
        val startTime: Long,
        val durationMs: Long,
        val sizeBytes: Long
    )

    // --- Record start/stop ---

    /**
     * Start recording audio for a given channel.
     * Call on RX squelch open or TX PTT down.
     */
    fun startRecording(type: RecordingType, channelNumber: Int, channelName: String) {
        if (!isEnabled) return
        if (type == RecordingType.RX && !recordRx) return
        if (type == RecordingType.TX && !recordTx) return
        if (!isChannelRecordingEnabled(channelNumber)) return
        if (isRecording) stopRecording()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (type == RecordingType.RX) "rx" else "tx"
        val filename = "${prefix}_ch${channelNumber}_${timestamp}.wav"
        val file = File(recordingsDir, filename)

        try {
            val fos = FileOutputStream(file)
            // Write placeholder WAV header (will be updated on stop)
            fos.write(createWavHeader(0))
            currentOutputStream = fos
            currentFile = file
            currentRecordingType = type
            currentChannelNumber = channelNumber
            currentChannelName = channelName
            bytesWritten = 0
            isRecording = true

            logEvent("REC_START", "Started ${type.name} recording on Ch $channelNumber ($channelName)")
            Log.d(TAG, "Recording started: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    /**
     * Write audio samples to the current recording.
     * Call this from the audio engine's onAudioData callback.
     */
    fun writeAudioData(samples: ShortArray, count: Int) {
        if (!isRecording) return

        try {
            val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                buffer.putShort(samples[i])
            }
            currentOutputStream?.write(buffer.array())
            bytesWritten += count * 2
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio data", e)
        }
    }

    /**
     * Stop the current recording and finalize the WAV file.
     */
    fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        try {
            currentOutputStream?.close()
            // Update WAV header with actual data size
            currentFile?.let { updateWavHeader(it, bytesWritten) }

            val durationMs = (bytesWritten.toDouble() / (SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8) * 1000).toLong()
            logEvent("REC_STOP", "Stopped recording: ${currentFile?.name} (${durationMs}ms, ${bytesWritten / 1024}KB)")
            Log.d(TAG, "Recording stopped: ${currentFile?.name}, ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recording", e)
        }

        currentOutputStream = null
        currentFile = null
        currentRecordingType = null
        bytesWritten = 0
    }

    // --- Listing & Playback ---

    /**
     * List all recordings, newest first.
     */
    fun listRecordings(): List<RecordingInfo> {
        return recordingsDir.listFiles { f -> f.extension == "wav" }
            ?.mapNotNull { parseRecordingInfo(it) }
            ?.sortedByDescending { it.startTime }
            ?: emptyList()
    }

    /**
     * List recordings filtered by channel.
     */
    fun listRecordingsForChannel(channelNumber: Int): List<RecordingInfo> {
        return listRecordings().filter { it.channelNumber == channelNumber }
    }

    /**
     * Delete a specific recording.
     */
    fun deleteRecording(file: File): Boolean {
        return file.delete().also { if (it) logEvent("REC_DELETE", "Deleted: ${file.name}") }
    }

    /**
     * Delete all recordings.
     */
    fun deleteAllRecordings() {
        recordingsDir.listFiles { f -> f.extension == "wav" }?.forEach { it.delete() }
        logEvent("REC_DELETE_ALL", "All recordings deleted")
    }

    private fun parseRecordingInfo(file: File): RecordingInfo? {
        val name = file.nameWithoutExtension
        // Expected format: rx_ch5_20250313_143022 or tx_ch12_20250313_143022
        val parts = name.split("_")
        if (parts.size < 4) return null

        val type = when (parts[0]) {
            "rx" -> RecordingType.RX
            "tx" -> RecordingType.TX
            else -> return null
        }
        val channelNumber = parts[1].removePrefix("ch").toIntOrNull() ?: return null
        val dateStr = "${parts[2]}_${parts[3]}"
        val startTime = try {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(dateStr)?.time ?: 0
        } catch (_: Exception) { 0L }

        val dataSize = file.length() - 44 // Subtract WAV header
        val durationMs = (dataSize.toDouble() / (SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8) * 1000).toLong()

        return RecordingInfo(
            file = file,
            type = type,
            channelNumber = channelNumber,
            channelName = "", // Not stored in filename
            startTime = startTime,
            durationMs = durationMs,
            sizeBytes = file.length()
        )
    }

    // --- Activity Log ---

    fun logEvent(type: String, message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            logFile.appendText("$timestamp | $type | $message\n")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write activity log", e)
        }
    }

    fun getActivityLog(maxLines: Int = 100): List<String> {
        if (!logFile.exists()) return emptyList()
        return logFile.readLines().takeLast(maxLines)
    }

    fun clearActivityLog() {
        logFile.delete()
    }

    // --- Storage Management ---

    /**
     * Get total recording storage used in bytes.
     */
    fun getStorageUsedBytes(): Long {
        return recordingsDir.listFiles()?.sumOf { it.length() } ?: 0
    }

    /**
     * Get total recording count.
     */
    fun getRecordingCount(): Int {
        return recordingsDir.listFiles { f -> f.extension == "wav" }?.size ?: 0
    }

    /**
     * Clean up old recordings based on retention policy.
     */
    fun enforceStorageLimits() {
        // Delete recordings older than maxRetentionDays
        val cutoff = System.currentTimeMillis() - (maxRetentionDays * 24 * 60 * 60 * 1000L)
        recordingsDir.listFiles { f -> f.extension == "wav" }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach {
                it.delete()
                Log.d(TAG, "Auto-deleted old recording: ${it.name}")
            }

        // Delete oldest recordings if total size exceeds maxStorageMb
        val maxBytes = maxStorageMb * 1024L * 1024L
        val allFiles = recordingsDir.listFiles { f -> f.extension == "wav" }
            ?.sortedBy { it.lastModified() }
            ?.toMutableList() ?: return

        var totalSize = allFiles.sumOf { it.length() }
        while (totalSize > maxBytes && allFiles.isNotEmpty()) {
            val oldest = allFiles.removeAt(0)
            totalSize -= oldest.length()
            oldest.delete()
            Log.d(TAG, "Auto-deleted for storage limit: ${oldest.name}")
        }
    }

    // --- WAV Utilities ---

    private fun createWavHeader(dataSize: Long): ByteArray {
        val totalSize = dataSize + 36
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalSize.toInt())
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // PCM chunk size
        buffer.putShort(1) // PCM format
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(BITS_PER_SAMPLE.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataSize.toInt())
        return buffer.array()
    }

    private fun updateWavHeader(file: File, dataSize: Long) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val totalSize = dataSize + 36
                // Update RIFF chunk size (offset 4)
                raf.seek(4)
                raf.write(intToLittleEndian(totalSize.toInt()))
                // Update data chunk size (offset 40)
                raf.seek(40)
                raf.write(intToLittleEndian(dataSize.toInt()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WAV header", e)
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }
}
