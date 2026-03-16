package com.db20g.controller.auto

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Parses and executes voice commands for hands-free radio operation.
 * Designed for Android Auto and general voice control.
 *
 * Supported commands:
 * - "Change to channel [number]" / "Go to channel [number]" / "Switch to channel [number]"
 * - "Key up" / "Transmit" / "PTT" (activate PTT)
 * - "Key down" / "Stop transmitting" / "Release" (release PTT)
 * - "What channel am I on?" / "Current channel" (report status)
 * - "Channel up" / "Next channel"
 * - "Channel down" / "Previous channel"
 * - "Scan" / "Start scanning"
 * - "Stop scan" / "Stop scanning"
 * - "Emergency" (activate emergency mode)
 */
class VoiceCommandHandler(private val context: Context) {

    companion object {
        private const val TAG = "VoiceCommandHandler"
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listener: VoiceCommandListener? = null

    interface VoiceCommandListener {
        fun onChangeChannel(channelNumber: Int)
        fun onPttActivate()
        fun onPttRelease()
        fun onChannelUp()
        fun onChannelDown()
        fun onRequestStatus()
        fun onStartScan()
        fun onStopScan()
        fun onEmergency()
    }

    fun setListener(listener: VoiceCommandListener) {
        this.listener = listener
    }

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
            }
        }
    }

    /**
     * Parse a voice command string and execute the appropriate action.
     * Returns a VoiceCommandResult describing what happened.
     */
    fun processCommand(rawInput: String): VoiceCommandResult {
        val input = rawInput.trim().lowercase(Locale.US)
        Log.d(TAG, "Processing voice command: '$input'")

        // Channel change: "change to channel 19", "go to channel 5", "switch channel 12"
        val channelPattern = Regex("""(?:change|go|switch|tune|set)\s+(?:to\s+)?channel\s+(\d+)""")
        channelPattern.find(input)?.let { match ->
            val ch = match.groupValues[1].toIntOrNull()
            if (ch != null && ch in 1..128) {
                listener?.onChangeChannel(ch - 1) // Zero-indexed internally
                val response = "Switching to channel $ch"
                speak(response)
                return VoiceCommandResult(true, CommandType.CHANGE_CHANNEL, response)
            }
        }

        // Also handle "channel 19" by itself
        val simpleChannelPattern = Regex("""^channel\s+(\d+)$""")
        simpleChannelPattern.find(input)?.let { match ->
            val ch = match.groupValues[1].toIntOrNull()
            if (ch != null && ch in 1..128) {
                listener?.onChangeChannel(ch - 1)
                val response = "Switching to channel $ch"
                speak(response)
                return VoiceCommandResult(true, CommandType.CHANGE_CHANNEL, response)
            }
        }

        // PTT activate: "key up", "transmit", "ptt", "push to talk"
        if (input.matches(Regex("""key\s*up|transmit|ptt|push\s+to\s+talk|start\s+transmit(?:ting)?"""))) {
            listener?.onPttActivate()
            return VoiceCommandResult(true, CommandType.PTT_ACTIVATE, "Transmitting")
        }

        // PTT release: "key down", "stop transmitting", "release", "clear"
        if (input.matches(Regex("""key\s*down|stop\s+transmit(?:ting)?|release|clear"""))) {
            listener?.onPttRelease()
            return VoiceCommandResult(true, CommandType.PTT_RELEASE, "Transmission ended")
        }

        // Status query: "what channel am I on?", "current channel", "status", "what channel"
        if (input.matches(Regex("""what\s+channel.*|current\s+channel|status|report"""))) {
            listener?.onRequestStatus()
            return VoiceCommandResult(true, CommandType.STATUS_QUERY, "")
        }

        // Channel up: "channel up", "next channel", "up"
        if (input.matches(Regex("""channel\s+up|next\s+channel|next|up"""))) {
            listener?.onChannelUp()
            return VoiceCommandResult(true, CommandType.CHANNEL_UP, "Channel up")
        }

        // Channel down: "channel down", "previous channel", "down", "back"
        if (input.matches(Regex("""channel\s+down|prev(?:ious)?\s+channel|previous|down|back"""))) {
            listener?.onChannelDown()
            return VoiceCommandResult(true, CommandType.CHANNEL_DOWN, "Channel down")
        }

        // Scan: "scan", "start scanning", "start scan"
        if (input.matches(Regex("""scan|start\s+scan(?:ning)?"""))) {
            listener?.onStartScan()
            val response = "Starting scan"
            speak(response)
            return VoiceCommandResult(true, CommandType.START_SCAN, response)
        }

        // Stop scan: "stop scan", "stop scanning"
        if (input.matches(Regex("""stop\s+scan(?:ning)?"""))) {
            listener?.onStopScan()
            val response = "Scan stopped"
            speak(response)
            return VoiceCommandResult(true, CommandType.STOP_SCAN, response)
        }

        // Emergency: "emergency", "mayday", "help"
        if (input.matches(Regex("""emergency|mayday|help"""))) {
            listener?.onEmergency()
            val response = "Activating emergency mode"
            speak(response)
            return VoiceCommandResult(true, CommandType.EMERGENCY, response)
        }

        Log.d(TAG, "Unrecognized command: '$input'")
        return VoiceCommandResult(false, CommandType.UNKNOWN, "I didn't understand that command")
    }

    /**
     * Speak text through TTS (for status responses and confirmations).
     */
    fun speak(text: String) {
        if (ttsReady && text.isNotEmpty()) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "radio_voice_${System.currentTimeMillis()}")
        }
    }

    /**
     * Announce current radio status via TTS.
     */
    fun announceStatus(channelNumber: Int, channelName: String, frequency: String, isTransmitting: Boolean) {
        val status = buildString {
            append("You are on channel ${channelNumber + 1}")
            if (channelName.isNotEmpty()) {
                append(", $channelName")
            }
            append(", frequency $frequency megahertz")
            if (isTransmitting) {
                append(". Currently transmitting.")
            }
        }
        speak(status)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        listener = null
    }

    data class VoiceCommandResult(
        val recognized: Boolean,
        val type: CommandType,
        val responseText: String
    )

    enum class CommandType {
        CHANGE_CHANNEL,
        PTT_ACTIVATE,
        PTT_RELEASE,
        STATUS_QUERY,
        CHANNEL_UP,
        CHANNEL_DOWN,
        START_SCAN,
        STOP_SCAN,
        EMERGENCY,
        UNKNOWN
    }
}
