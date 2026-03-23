package com.db20g.controller.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log

/**
 * Bridges audio between the Android phone and the ESP32 board over Bluetooth.
 *
 * With the v10 hardware (ESP32-WROOM-32E), radio audio flows through the
 * ESP32's DAC/ADC pins. The ESP32 firmware uses Bluetooth A2DP Sink (RX from
 * phone → DAC → radio mic) and A2DP Source / HFP (ADC → radio speaker → phone).
 *
 * On the Android side this class:
 *  1. Routes mic capture to the BT SCO/A2DP audio output (TX to radio)
 *  2. Routes BT audio input to the phone speaker (RX from radio)
 *  3. Provides audio level metering and VOX detection
 *
 * For SCO (Synchronous Connection-Oriented) link:
 *  - 8 kHz / 16 kHz mono, voice-grade — matches radio audio quality
 *  - Lower latency than A2DP, better for PTT two-way audio
 */
class BluetoothAudioBridge(private val context: Context) {

    companion object {
        private const val TAG = "BtAudioBridge"

        // SCO operates at 8 or 16 kHz — use 8 kHz for voice-grade radio audio
        const val SAMPLE_RATE = 8000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Audio components
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null

    // State
    private var capturing = false
    private var playing = false
    private var scoActive = false

    // Callbacks
    var onAudioLevel: ((rms: Float, peak: Int) -> Unit)? = null
    var onVoxTriggered: ((active: Boolean) -> Unit)? = null

    // VOX
    var voxEnabled = false
    var voxThreshold = 1500
    var voxDelayMs = 800L
    private var lastVoiceTime = 0L

    /**
     * Start the Bluetooth SCO audio link.
     * Must be called before [startCapture] / [startPlayback].
     */
    fun startSco() {
        if (scoActive) return
        @Suppress("DEPRECATION")
        audioManager.startBluetoothSco()
        @Suppress("DEPRECATION")
        audioManager.isBluetoothScoOn = true
        scoActive = true
        Log.i(TAG, "BT SCO started")
    }

    /**
     * Stop the Bluetooth SCO audio link.
     */
    fun stopSco() {
        if (!scoActive) return
        @Suppress("DEPRECATION")
        audioManager.isBluetoothScoOn = false
        @Suppress("DEPRECATION")
        audioManager.stopBluetoothSco()
        scoActive = false
        Log.i(TAG, "BT SCO stopped")
    }

    /**
     * Start capturing audio from the phone mic and routing it through BT SCO
     * to the ESP32 board (which feeds it to the radio's mic input via DAC).
     */
    fun startCapture() {
        if (capturing) return

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufferSize = maxOf(minBuf * 2, 2048)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_IN,
            ENCODING,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed")
            audioRecord?.release()
            audioRecord = null
            return
        }

        // Route to BT SCO device if available
        findBluetoothScoDevice(isInput = true)?.let { audioRecord?.preferredDevice = it }

        capturing = true
        audioRecord?.startRecording()

        captureThread = Thread({
            val buffer = ShortArray(512)
            var wasVoxActive = false

            while (capturing) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read <= 0) continue

                // Metering
                var sumSquares = 0.0
                var peak = 0
                for (i in 0 until read) {
                    val sample = kotlin.math.abs(buffer[i].toInt())
                    sumSquares += sample.toDouble() * sample.toDouble()
                    if (sample > peak) peak = sample
                }
                val rms = kotlin.math.sqrt(sumSquares / read).toFloat()
                onAudioLevel?.invoke(rms, peak)

                // VOX
                if (voxEnabled) {
                    val voiceDetected = peak > voxThreshold
                    if (voiceDetected) {
                        lastVoiceTime = System.currentTimeMillis()
                        if (!wasVoxActive) {
                            wasVoxActive = true
                            onVoxTriggered?.invoke(true)
                        }
                    } else if (wasVoxActive &&
                        System.currentTimeMillis() - lastVoiceTime > voxDelayMs
                    ) {
                        wasVoxActive = false
                        onVoxTriggered?.invoke(false)
                    }
                }
            }
        }, "BtAudioCapture")
        captureThread?.start()
        Log.i(TAG, "Capture started (SCO ${if (scoActive) "on" else "off"})")
    }

    fun stopCapture() {
        capturing = false
        captureThread?.join(1000)
        captureThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    /**
     * Start playback — receives radio audio from the ESP32 (via BT SCO)
     * and plays it through the phone speaker or connected BT headset.
     */
    fun startPlayback() {
        if (playing) return

        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val bufferSize = maxOf(minBuf * 2, 2048)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        findBluetoothScoDevice(isInput = false)?.let { audioTrack?.preferredDevice = it }
        audioTrack?.play()
        playing = true
        Log.i(TAG, "Playback started")
    }

    /** Write PCM samples received from the ESP32 to the phone speaker. */
    fun writePlaybackData(samples: ShortArray, count: Int) {
        audioTrack?.write(samples, 0, count)
    }

    fun stopPlayback() {
        playing = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }
        audioTrack = null
    }

    /** Release everything. */
    fun shutdown() {
        stopCapture()
        stopPlayback()
        stopSco()
    }

    // ---- Internals ----

    private fun findBluetoothScoDevice(isInput: Boolean): AudioDeviceInfo? {
        val devices = audioManager.getDevices(
            if (isInput) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS
        )
        return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    }
}
