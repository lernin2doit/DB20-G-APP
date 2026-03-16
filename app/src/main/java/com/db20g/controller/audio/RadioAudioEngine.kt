package com.db20g.controller.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Manages audio capture (microphone) and playback (speaker) for live radio operation.
 *
 * Audio paths:
 * - TX: Phone mic → AudioRecord → (optional processing) → audio output to radio
 * - RX: Radio audio input → AudioTrack → phone speaker
 *
 * When using the phone's built-in mic/speaker (no separate audio cable),
 * this provides VOX detection, audio level metering, and hands-free operation.
 *
 * When a USB audio adapter is connected to the radio's mic/speaker jack,
 * this routes audio through the Android audio system to that device.
 */
class RadioAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "RadioAudioEngine"
        const val SAMPLE_RATE = 44100
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // VOX defaults
        const val DEFAULT_VOX_THRESHOLD = 1500   // raw PCM amplitude
        const val DEFAULT_VOX_DELAY_MS = 800L    // hold TX open after voice stops
    }

    // Audio components
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    // State
    private var capturing = false
    private var playing = false
    private var captureThread: Thread? = null

    // VOX
    var voxEnabled = false
    var voxThreshold = DEFAULT_VOX_THRESHOLD
    var voxDelayMs = DEFAULT_VOX_DELAY_MS
    private var lastVoiceTime = 0L

    // Callbacks
    var onAudioLevel: ((rms: Float, peak: Int) -> Unit)? = null
    var onVoxTriggered: ((active: Boolean) -> Unit)? = null
    var onAudioData: ((ShortArray, Int) -> Unit)? = null

    // Buffer sizes
    private val minRecordBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
    private val minPlayBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)

    val hasRecordPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Start capturing audio from the microphone.
     * Delivers audio level callbacks and VOX events.
     */
    fun startCapture() {
        if (capturing) return
        if (!hasRecordPermission) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        val bufferSize = maxOf(minRecordBufferSize * 2, 4096)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_IN,
            ENCODING,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return
        }

        // Route to USB audio device if configured
        preferredInputDevice?.let { audioRecord?.preferredDevice = it }

        capturing = true
        audioRecord?.startRecording()

        captureThread = Thread({
            val buffer = ShortArray(1024)
            var wasVoxActive = false

            while (capturing) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read <= 0) continue

                // Calculate audio levels
                var sumSquares = 0.0
                var peak = 0
                for (i in 0 until read) {
                    val sample = abs(buffer[i].toInt())
                    sumSquares += sample.toDouble() * sample.toDouble()
                    if (sample > peak) peak = sample
                }
                val rms = sqrt(sumSquares / read).toFloat()

                onAudioLevel?.invoke(rms, peak)
                onAudioData?.invoke(buffer, read)

                // VOX detection
                if (voxEnabled) {
                    val voiceDetected = peak > voxThreshold
                    if (voiceDetected) {
                        lastVoiceTime = System.currentTimeMillis()
                        if (!wasVoxActive) {
                            wasVoxActive = true
                            onVoxTriggered?.invoke(true)
                        }
                    } else if (wasVoxActive) {
                        if (System.currentTimeMillis() - lastVoiceTime > voxDelayMs) {
                            wasVoxActive = false
                            onVoxTriggered?.invoke(false)
                        }
                    }
                }
            }
        }, "AudioCapture")
        captureThread?.start()
        Log.i(TAG, "Audio capture started")
    }

    /**
     * Stop audio capture.
     */
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
        Log.i(TAG, "Audio capture stopped")
    }

    /**
     * Initialize the audio output (speaker/earpiece) for RX playback.
     */
    fun startPlayback() {
        if (playing) return

        val bufferSize = maxOf(minPlayBufferSize * 2, 4096)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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

        audioTrack?.play()
        // Route to USB audio device if configured
        preferredOutputDevice?.let { audioTrack?.preferredDevice = it }
        playing = true
        Log.i(TAG, "Audio playback started")
    }

    /**
     * Write audio samples to the speaker for RX playback.
     */
    fun writePlayback(samples: ShortArray, offset: Int = 0, count: Int = samples.size) {
        if (!playing) return
        audioTrack?.write(samples, offset, count)
    }

    /**
     * Stop audio playback.
     */
    fun stopPlayback() {
        playing = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack", e)
        }
        audioTrack = null
        Log.i(TAG, "Audio playback stopped")
    }

    /**
     * Enable speakerphone mode for hands-free operation.
     */
    fun setSpeakerphone(enabled: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = enabled
    }

    /**
     * Set the audio stream volume (0.0 to 1.0).
     */
    fun setVolume(volume: Float) {
        audioTrack?.setVolume(volume.coerceIn(0f, 1f))
    }

    /**
     * Release all audio resources.
     */
    fun release() {
        stopCapture()
        stopPlayback()
    }

    // --- USB Audio Device Routing ---

    private var preferredInputDevice: AudioDeviceInfo? = null
    private var preferredOutputDevice: AudioDeviceInfo? = null

    /**
     * Find connected USB audio devices (e.g., CM108 sound card in the interface box).
     */
    fun findUsbAudioDevices(): List<AudioDeviceInfo> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val allDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        return allDevices.filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
    }

    /**
     * Get the names of available USB audio devices for display.
     */
    fun getUsbAudioDeviceNames(): List<String> {
        return findUsbAudioDevices().map { device ->
            val name = device.productName?.toString() ?: "USB Audio"
            val direction = when {
                device.isSource && device.isSink -> "(In/Out)"
                device.isSource -> "(Input)"
                device.isSink -> "(Output)"
                else -> ""
            }
            "$name $direction"
        }
    }

    /**
     * Route audio capture and playback through a USB audio device.
     * Call this before startCapture() / startPlayback().
     *
     * @return true if a USB audio device was found and selected
     */
    fun useUsbAudioDevice(): Boolean {
        val devices = findUsbAudioDevices()
        if (devices.isEmpty()) {
            Log.w(TAG, "No USB audio devices found, using default")
            preferredInputDevice = null
            preferredOutputDevice = null
            return false
        }

        preferredInputDevice = devices.firstOrNull { it.isSource }
        preferredOutputDevice = devices.firstOrNull { it.isSink }

        Log.i(TAG, "USB audio: input=${preferredInputDevice?.productName}, output=${preferredOutputDevice?.productName}")

        // Apply to already-running audio components
        audioRecord?.preferredDevice = preferredInputDevice
        audioTrack?.preferredDevice = preferredOutputDevice

        return true
    }

    /**
     * Clear USB audio routing and revert to default (phone mic/speaker).
     */
    fun useDefaultAudioDevice() {
        preferredInputDevice = null
        preferredOutputDevice = null
        audioRecord?.preferredDevice = null
        audioTrack?.preferredDevice = null
        Log.i(TAG, "Reverted to default audio devices")
    }

    /** Whether a USB audio input device is currently selected */
    val isUsbInputActive: Boolean get() = preferredInputDevice != null

    /** Whether a USB audio output device is currently selected */
    val isUsbOutputActive: Boolean get() = preferredOutputDevice != null
}
