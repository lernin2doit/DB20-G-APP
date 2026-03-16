package com.db20g.controller.auto

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages audio focus for radio operations, especially in vehicle environments.
 * Handles navigation prompt interruptions gracefully by ducking or pausing radio audio.
 */
class AudioFocusManager(context: Context) {

    companion object {
        private const val TAG = "AudioFocusManager"
        private const val FOCUS_LOSS_NONE = 0
        private const val FOCUS_LOSS_TRANSIENT = 1
        private const val FOCUS_LOSS_TRANSIENT_DUCK = 2
        private const val FOCUS_LOSS_PERMANENT = 3
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var listener: AudioFocusListener? = null

    // Track what caused the focus loss so we can resume appropriately
    private var wasTransmitting = false
    private var focusLossType = FOCUS_LOSS_NONE

    interface AudioFocusListener {
        /** Called when audio focus is gained — resume full radio audio */
        fun onFocusGained()

        /** Called when focus is lost transiently (e.g., nav voice) — duck/lower volume */
        fun onFocusDucked()

        /** Called when focus is lost transiently — pause radio audio */
        fun onFocusPaused()

        /** Called when focus is permanently lost — stop radio audio */
        fun onFocusLost()
    }

    fun setListener(listener: AudioFocusListener) {
        this.listener = listener
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasFocus = true
                focusLossType = FOCUS_LOSS_NONE
                listener?.onFocusGained()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost permanently")
                hasFocus = false
                focusLossType = FOCUS_LOSS_PERMANENT
                listener?.onFocusLost()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently (nav prompt?)")
                hasFocus = false
                focusLossType = FOCUS_LOSS_TRANSIENT
                listener?.onFocusPaused()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost — ducking")
                hasFocus = false
                focusLossType = FOCUS_LOSS_TRANSIENT_DUCK
                listener?.onFocusDucked()
            }
        }
    }

    /**
     * Request audio focus for radio communication.
     * Uses USAGE_VOICE_COMMUNICATION for proper priority in vehicle environments.
     * Returns true if focus was granted.
     */
    fun requestFocus(): Boolean {
        // Abandon previous focus request to avoid leak
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false) // We handle ducking ourselves
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        focusRequest = request

        val result = audioManager.requestAudioFocus(request)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Focus request result: $result, granted: $hasFocus")
        return hasFocus
    }

    /**
     * Request transient audio focus for transmission.
     * This signals to other apps (music, nav) that we're briefly taking over audio.
     */
    fun requestTransmitFocus(): Boolean {
        // Abandon previous focus request to avoid leak
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        focusRequest = request
        wasTransmitting = true

        val result = audioManager.requestAudioFocus(request)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "Transmit focus request: $result, granted: $hasFocus")
        return hasFocus
    }

    /**
     * Release audio focus when radio operations stop.
     */
    fun abandonFocus() {
        focusRequest?.let { request ->
            audioManager.abandonAudioFocusRequest(request)
            Log.d(TAG, "Audio focus abandoned")
        }
        hasFocus = false
        wasTransmitting = false
        focusRequest = null
    }

    fun hasFocus(): Boolean = hasFocus

    /**
     * Route audio to the vehicle's Bluetooth SCO (hands-free) channel.
     * Call this when the app detects it's running in a car environment.
     */
    fun startBluetoothSco() {
        if (audioManager.isBluetoothScoAvailableOffCall) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            Log.d(TAG, "Bluetooth SCO started for car audio routing")
        } else {
            Log.w(TAG, "Bluetooth SCO not available off-call")
        }
    }

    fun stopBluetoothSco() {
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        Log.d(TAG, "Bluetooth SCO stopped")
    }

    /**
     * Check if audio is currently routed through Bluetooth (car head unit).
     */
    fun isBluetoothAudioConnected(): Boolean {
        return audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
    }

    /**
     * Set speakerphone mode — useful when not connected to car audio.
     */
    fun setSpeakerphone(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    /**
     * Get the current stream volume for voice communication (0.0-1.0).
     */
    fun getVolume(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        return if (max > 0) current.toFloat() / max else 0f
    }

    fun release() {
        abandonFocus()
        listener = null
    }
}
