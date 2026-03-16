package com.db20g.controller.repeater

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.db20g.controller.audio.MorseCodeGenerator

/**
 * Manages automatic callsign identification as required by FCC Part 95 (GMRS).
 *
 * FCC requires GMRS stations to transmit their callsign:
 * - At the end of each communication
 * - At least every 15 minutes during a communication
 *
 * This manager tracks PTT usage and triggers CW or voice callsign ID
 * at the required intervals.
 */
class CallsignManager(private val context: Context) {

    companion object {
        private const val TAG = "CallsignManager"
        private const val PREFS_NAME = "callsign_prefs"
        private const val KEY_CALLSIGN = "callsign"
        private const val KEY_AUTO_ID = "auto_id_enabled"
        private const val KEY_INTERVAL_MIN = "id_interval_minutes"
        private const val KEY_CW_SPEED = "cw_speed_wpm"
        private const val KEY_ID_METHOD = "id_method"
        const val DEFAULT_INTERVAL_MINUTES = 15
        const val DEFAULT_CW_SPEED = 20
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val morseGenerator = MorseCodeGenerator()

    // State tracking
    private var lastIdTimeMs: Long = 0
    private var firstPttTimeMs: Long = 0
    private var pttActive = false
    private var hasTransmittedSinceLastId = false

    // Callbacks
    var onIdRequired: (() -> Unit)? = null
    var onIdTransmitting: ((String) -> Unit)? = null
    var onIdComplete: (() -> Unit)? = null

    // --- Persistent settings ---

    var callsign: String
        get() = prefs.getString(KEY_CALLSIGN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CALLSIGN, value.uppercase().trim()).apply()

    var autoIdEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ID, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ID, value).apply()

    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL_MIN, DEFAULT_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_INTERVAL_MIN, value.coerceIn(1, 30)).apply()

    var cwSpeedWpm: Int
        get() = prefs.getInt(KEY_CW_SPEED, DEFAULT_CW_SPEED)
        set(value) {
            prefs.edit().putInt(KEY_CW_SPEED, value.coerceIn(10, 30)).apply()
            morseGenerator.wordsPerMinute = value
        }

    var idMethod: IdMethod
        get() = try {
            IdMethod.valueOf(prefs.getString(KEY_ID_METHOD, IdMethod.CW.name) ?: IdMethod.CW.name)
        } catch (_: Exception) { IdMethod.CW }
        set(value) = prefs.edit().putString(KEY_ID_METHOD, value.name).apply()

    val isCallsignSet: Boolean get() = callsign.isNotEmpty()

    val intervalMs: Long get() = intervalMinutes * 60_000L

    /** Time remaining until next required ID, in milliseconds. -1 if not tracking. */
    val timeUntilNextIdMs: Long
        get() {
            if (!hasTransmittedSinceLastId || lastIdTimeMs == 0L) return -1
            val elapsed = System.currentTimeMillis() - lastIdTimeMs
            return (intervalMs - elapsed).coerceAtLeast(0)
        }

    /**
     * Call when PTT is pressed.
     */
    fun onPttDown() {
        pttActive = true
        hasTransmittedSinceLastId = true
        if (firstPttTimeMs == 0L) {
            firstPttTimeMs = System.currentTimeMillis()
        }
    }

    /**
     * Call when PTT is released. Checks if ID is needed.
     */
    fun onPttUp() {
        pttActive = false
        if (!autoIdEnabled || !isCallsignSet) return

        checkAndTransmitId()
    }

    /**
     * Check if enough time has passed to require an ID transmission.
     * Call periodically (e.g., every second) during active communication.
     */
    fun checkAndTransmitId(): Boolean {
        if (!autoIdEnabled || !isCallsignSet || !hasTransmittedSinceLastId) return false

        val now = System.currentTimeMillis()
        if (lastIdTimeMs == 0L) {
            // First transmission session — don't ID immediately, start the timer
            lastIdTimeMs = now
            return false
        }

        if (now - lastIdTimeMs >= intervalMs) {
            transmitId()
            return true
        }
        return false
    }

    /**
     * Manually trigger a callsign ID transmission.
     */
    fun transmitId() {
        if (!isCallsignSet) return

        val cs = callsign
        Log.i(TAG, "Transmitting callsign ID: $cs via ${idMethod.name}")
        onIdTransmitting?.invoke(cs)

        when (idMethod) {
            IdMethod.CW -> {
                morseGenerator.wordsPerMinute = cwSpeedWpm
                morseGenerator.playAsync(cs) {
                    lastIdTimeMs = System.currentTimeMillis()
                    hasTransmittedSinceLastId = false
                    onIdComplete?.invoke()
                }
            }
            IdMethod.VOICE_TTS -> {
                // TTS handled by the caller (needs Activity context for TextToSpeech)
                onIdRequired?.invoke()
                lastIdTimeMs = System.currentTimeMillis()
                hasTransmittedSinceLastId = false
            }
        }
    }

    /**
     * Reset the ID timer (e.g., when starting a new session).
     */
    fun resetTimer() {
        lastIdTimeMs = 0
        firstPttTimeMs = 0
        hasTransmittedSinceLastId = false
    }

    fun release() {
        morseGenerator.release()
    }

    enum class IdMethod {
        CW,         // Morse code CW tones
        VOICE_TTS   // Android text-to-speech
    }
}
