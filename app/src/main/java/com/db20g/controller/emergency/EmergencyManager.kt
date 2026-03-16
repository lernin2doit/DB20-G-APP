package com.db20g.controller.emergency

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Emergency Features Manager.
 * Handles SOS beacon, dead man's switch, GPS coordinate voice readout,
 * emergency channel quick-tuning, emergency contact tracking, and net check-in.
 */
class EmergencyManager(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyManager"
        private const val PREFS_NAME = "emergency_prefs"

        /** Unofficial GMRS emergency channel (Channel 20: 462.675 MHz) */
        const val EMERGENCY_CHANNEL_20_FREQ = 462.675

        /** GMRS calling channel (Channel 16: 462.5625 MHz — interstitial) */
        const val CALLING_CHANNEL_16_FREQ = 462.5625

        /** Common repeater output frequencies for emergency use */
        val EMERGENCY_REPEATER_FREQS = listOf(
            462.675,  // Ch 20 — most common "emergency" channel
            462.550,  // Ch 15R
            462.700,  // Ch 21R
            462.725   // Ch 22R
        )

        /** Standard CTCSS tones commonly used on emergency-oriented repeaters */
        val COMMON_EMERGENCY_TONES = listOf(141.3, 136.5, 100.0, 110.9)

        /** Default dead man's switch timeout in minutes */
        const val DEFAULT_DMS_TIMEOUT_MINUTES = 30
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // TTS engine for voice readouts
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Dead man's switch
    private var dmsTimer: CountDownTimer? = null
    private var dmsCallback: (() -> Unit)? = null
    private var dmsTimeoutMs: Long = DEFAULT_DMS_TIMEOUT_MINUTES * 60 * 1000L
    var isDmsActive: Boolean = false
        private set

    // Emergency contacts
    private val contacts = mutableListOf<EmergencyContact>()

    // SOS beacon state
    var isSosActive: Boolean = false
        private set
    private var sosCallback: ((String) -> Unit)? = null
    private var sosTimer: CountDownTimer? = null

    // --- Initialization ---

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.85f) // Slightly slower for clarity
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
        loadContacts()
    }

    fun shutdown() {
        stopSosBeacon()
        stopDeadManSwitch()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    // --- GPS Coordinate Voice Readout ---

    /**
     * Generate phonetic GPS coordinate string for voice readout.
     * Uses NATO-style precision readable by SAR teams.
     * Example: "Position: North 39 degrees 45 point 123 minutes, West 104 degrees 59 point 456 minutes"
     */
    fun formatGpsForVoice(location: Location): String {
        val latDir = if (location.latitude >= 0) "North" else "South"
        val lonDir = if (location.longitude >= 0) "East" else "West"

        val absLat = Math.abs(location.latitude)
        val absLon = Math.abs(location.longitude)

        val latDeg = absLat.toInt()
        val latMin = (absLat - latDeg) * 60

        val lonDeg = absLon.toInt()
        val lonMin = (absLon - lonDeg) * 60

        return "Position: $latDir $latDeg degrees ${"%.3f".format(latMin)} minutes, " +
                "$lonDir $lonDeg degrees ${"%.3f".format(lonMin)} minutes"
    }

    /**
     * Format GPS coordinates for concise display.
     */
    fun formatGpsForDisplay(location: Location): String {
        val latDir = if (location.latitude >= 0) "N" else "S"
        val lonDir = if (location.longitude >= 0) "E" else "W"
        return "${"%.5f".format(Math.abs(location.latitude))}°$latDir  " +
                "${"%.5f".format(Math.abs(location.longitude))}°$lonDir"
    }

    /**
     * Speak GPS coordinates using TTS (for transmission over radio via audio coupling).
     */
    fun speakGpsCoordinates(location: Location) {
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready")
            return
        }
        val text = formatGpsForVoice(location)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gps_readout")
    }

    /**
     * Speak an arbitrary emergency message via TTS.
     */
    fun speakMessage(message: String) {
        if (!ttsReady) return
        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "emergency_msg")
    }

    // --- SOS Beacon ---

    /**
     * Start SOS beacon: periodically calls back with the message to transmit.
     * The caller is responsible for keying the radio (PTT) and playing the audio.
     *
     * @param location Current GPS location
     * @param callsign Operator's callsign
     * @param intervalMs Interval between beacon transmissions (default 30 seconds)
     * @param onTransmit Callback invoked each time the beacon should transmit (passes the message text)
     */
    fun startSosBeacon(
        location: Location?,
        callsign: String,
        intervalMs: Long = 30_000,
        locationProvider: (() -> Location?)? = null,
        onTransmit: (String) -> Unit
    ) {
        isSosActive = true
        sosCallback = onTransmit

        val gpsStr = if (location != null) formatGpsForVoice(location) else "Position unknown"
        val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date())

        val message = buildString {
            append("Mayday, Mayday, Mayday. ")
            append("This is $callsign. ")
            append("Requesting emergency assistance. ")
            append("$gpsStr. ")
            append("Time: $timeStr UTC. ")
            append("Repeating. ")
        }

        // Immediately send first transmission
        onTransmit(message)
        if (ttsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "sos_beacon")
        }

        // Set up repeating timer
        sosTimer?.cancel()
        sosTimer = object : CountDownTimer(Long.MAX_VALUE, intervalMs) {
            override fun onTick(millisUntilFinished: Long) {
                if (isSosActive) {
                    // Update GPS if available
                    val currentLoc = locationProvider?.invoke() ?: location
                    val updatedMsg = buildString {
                        append("Mayday. $callsign. ")
                        if (currentLoc != null) append("${formatGpsForVoice(currentLoc)}. ")
                        append("Still requesting assistance. ")
                    }
                    sosCallback?.invoke(updatedMsg)
                    if (ttsReady) {
                        tts?.speak(updatedMsg, TextToSpeech.QUEUE_FLUSH, null, "sos_repeat")
                    }
                }
            }
            override fun onFinish() {}
        }.start()

        Log.i(TAG, "SOS beacon started: $message")
    }

    /**
     * Stop the SOS beacon.
     */
    fun stopSosBeacon() {
        isSosActive = false
        sosCallback = null
        sosTimer?.cancel()
        sosTimer = null
        tts?.stop()
        Log.i(TAG, "SOS beacon stopped")
    }

    // --- Dead Man's Switch ---

    /**
     * Start the dead man's switch. If the user doesn't call resetDeadManSwitch()
     * within the timeout, the callback fires (to auto-transmit a distress message).
     */
    fun startDeadManSwitch(
        timeoutMinutes: Int = DEFAULT_DMS_TIMEOUT_MINUTES,
        onTimeout: () -> Unit
    ) {
        isDmsActive = true
        dmsCallback = onTimeout
        dmsTimeoutMs = timeoutMinutes * 60 * 1000L
        resetDeadManSwitch()
        Log.i(TAG, "Dead man's switch started: ${timeoutMinutes}min timeout")
    }

    /**
     * Reset the dead man's switch timer (user activity detected).
     */
    fun resetDeadManSwitch() {
        if (!isDmsActive) return
        dmsTimer?.cancel()
        dmsTimer = object : CountDownTimer(dmsTimeoutMs, dmsTimeoutMs) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                Log.w(TAG, "Dead man's switch triggered!")
                dmsCallback?.invoke()
            }
        }.start()
    }

    /**
     * Stop the dead man's switch.
     */
    fun stopDeadManSwitch() {
        isDmsActive = false
        dmsCallback = null
        dmsTimer?.cancel()
        dmsTimer = null
    }

    // --- Emergency Net Check-In ---

    /**
     * Generate a structured net check-in report.
     * Standard format: Callsign, Status, Location, Persons, Needs
     */
    fun generateNetCheckIn(
        callsign: String,
        status: CheckInStatus,
        location: Location?,
        personsCount: Int,
        needs: String
    ): String {
        val locStr = if (location != null) formatGpsForDisplay(location) else "Unknown"
        val statusStr = when (status) {
            CheckInStatus.OK -> "All OK, no assistance needed"
            CheckInStatus.NEED_INFO -> "Need information"
            CheckInStatus.NEED_SUPPLIES -> "Need supplies"
            CheckInStatus.NEED_MEDICAL -> "Need medical assistance"
            CheckInStatus.NEED_EVACUATION -> "Need evacuation"
            CheckInStatus.EMERGENCY -> "Emergency, immediate assistance needed"
        }

        return buildString {
            appendLine("NET CHECK-IN REPORT")
            appendLine("Callsign: $callsign")
            appendLine("Status: $statusStr")
            appendLine("Location: $locStr")
            appendLine("Persons: $personsCount")
            if (needs.isNotBlank()) appendLine("Needs: $needs")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
        }
    }

    /**
     * Generate a voice-readable net check-in for TTS.
     */
    fun speakNetCheckIn(
        callsign: String,
        status: CheckInStatus,
        location: Location?,
        personsCount: Int
    ) {
        if (!ttsReady) return

        val statusSpoken = when (status) {
            CheckInStatus.OK -> "all okay, no assistance needed"
            CheckInStatus.NEED_INFO -> "need information"
            CheckInStatus.NEED_SUPPLIES -> "need supplies"
            CheckInStatus.NEED_MEDICAL -> "need medical assistance"
            CheckInStatus.NEED_EVACUATION -> "need evacuation"
            CheckInStatus.EMERGENCY -> "emergency, immediate assistance needed"
        }

        val message = buildString {
            append("$callsign checking in. ")
            append("Status: $statusSpoken. ")
            if (location != null) append("${formatGpsForVoice(location)}. ")
            append("$personsCount persons. ")
            append("$callsign, over. ")
        }
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "net_checkin")
    }

    // --- Emergency Contacts ---

    data class EmergencyContact(
        val callsign: String,
        val name: String,
        var lastCheckIn: Long = 0L,
        var lastStatus: CheckInStatus = CheckInStatus.OK,
        var lastLocation: String = ""
    )

    fun addContact(callsign: String, name: String) {
        contacts.removeAll { it.callsign.equals(callsign, ignoreCase = true) }
        contacts.add(EmergencyContact(callsign, name))
        saveContacts()
    }

    fun removeContact(callsign: String) {
        contacts.removeAll { it.callsign.equals(callsign, ignoreCase = true) }
        saveContacts()
    }

    fun updateContactCheckIn(callsign: String, status: CheckInStatus, location: String = "") {
        val contact = contacts.find { it.callsign.equals(callsign, ignoreCase = true) }
        if (contact != null) {
            contact.lastCheckIn = System.currentTimeMillis()
            contact.lastStatus = status
            contact.lastLocation = location
            saveContacts()
        }
    }

    fun getContacts(): List<EmergencyContact> = contacts.toList()

    /**
     * Get contacts that haven't checked in within the given threshold.
     */
    fun getOverdueContacts(thresholdMinutes: Int = 60): List<EmergencyContact> {
        val threshold = System.currentTimeMillis() - (thresholdMinutes * 60 * 1000L)
        return contacts.filter { it.lastCheckIn > 0 && it.lastCheckIn < threshold }
    }

    private fun loadContacts() {
        contacts.clear()
        val count = prefs.getInt("contact_count", 0)
        for (i in 0 until count) {
            val callsign = prefs.getString("contact_${i}_callsign", "") ?: ""
            val name = prefs.getString("contact_${i}_name", "") ?: ""
            val lastCheckIn = prefs.getLong("contact_${i}_lastcheckin", 0)
            val statusOrd = prefs.getInt("contact_${i}_status", 0)
            val location = prefs.getString("contact_${i}_location", "") ?: ""
            if (callsign.isNotBlank()) {
                contacts.add(
                    EmergencyContact(
                        callsign = callsign,
                        name = name,
                        lastCheckIn = lastCheckIn,
                        lastStatus = CheckInStatus.entries.getOrElse(statusOrd) { CheckInStatus.OK },
                        lastLocation = location
                    )
                )
            }
        }
    }

    private fun saveContacts() {
        prefs.edit().apply {
            putInt("contact_count", contacts.size)
            contacts.forEachIndexed { i, c ->
                putString("contact_${i}_callsign", c.callsign)
                putString("contact_${i}_name", c.name)
                putLong("contact_${i}_lastcheckin", c.lastCheckIn)
                putInt("contact_${i}_status", c.lastStatus.ordinal)
                putString("contact_${i}_location", c.lastLocation)
            }
            apply()
        }
    }

    // --- Quick-Tune Channels ---

    /**
     * Get the list of pre-defined emergency quick-tune channel configurations.
     */
    fun getEmergencyQuickTunes(): List<QuickTuneChannel> = listOf(
        QuickTuneChannel(
            name = "Ch 20 Emergency",
            frequency = EMERGENCY_CHANNEL_20_FREQ,
            description = "Unofficial GMRS emergency/calling channel"
        ),
        QuickTuneChannel(
            name = "Ch 16 Calling",
            frequency = CALLING_CHANNEL_16_FREQ,
            description = "GMRS interstitial calling channel"
        ),
        QuickTuneChannel(
            name = "Ch 19 Road",
            frequency = 462.650,
            description = "Common road/travel channel"
        ),
        QuickTuneChannel(
            name = "Ch 20R Repeater",
            frequency = 462.675,
            description = "Common emergency repeater output",
            tone = 141.3
        )
    )

    data class QuickTuneChannel(
        val name: String,
        val frequency: Double,
        val description: String,
        val tone: Double = 0.0
    )

    // --- Enums ---

    enum class CheckInStatus {
        OK,
        NEED_INFO,
        NEED_SUPPLIES,
        NEED_MEDICAL,
        NEED_EVACUATION,
        EMERGENCY
    }
}
