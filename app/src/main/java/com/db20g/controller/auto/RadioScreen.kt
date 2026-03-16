package com.db20g.controller.auto

import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.db20g.controller.service.RadioService

/**
 * Main driving-safe screen for Android Auto.
 *
 * Provides:
 * - Large PTT button (primary action)
 * - Current channel / frequency display
 * - Channel up/down navigation
 * - Scan toggle
 * - Voice command integration
 * - Status display (TX/RX/Idle)
 */
class RadioScreen(
    carContext: CarContext,
    private val voiceCommandHandler: VoiceCommandHandler,
    private val audioFocusManager: AudioFocusManager,
    private val mediaSessionManager: MediaSessionManager
) : Screen(carContext) {

    companion object {
        private const val TAG = "RadioScreen"
        private const val PREFS_NAME = "radio_auto_prefs"
    }

    private val prefs: SharedPreferences =
        carContext.getSharedPreferences(PREFS_NAME, CarContext.MODE_PRIVATE)

    // Radio state — persisted via SharedPreferences for cross-process access
    private var currentChannel: Int
        get() = prefs.getInt("current_channel", 0)
        set(value) { prefs.edit().putInt("current_channel", value).apply() }

    private var currentChannelName: String
        get() = prefs.getString("channel_name", "Channel 1") ?: "Channel 1"
        set(value) { prefs.edit().putString("channel_name", value).apply() }

    private var currentFrequency: String
        get() = prefs.getString("frequency", "462.5625") ?: "462.5625"
        set(value) { prefs.edit().putString("frequency", value).apply() }

    private var isTransmitting = false
    private var isReceiving = false
    private var isScanning = false
    private var callsign: String
        get() = prefs.getString("callsign", "") ?: ""
        set(value) { prefs.edit().putString("callsign", value).apply() }

    // GMRS channel table (22 standard channels)
    private val gmrsChannels = arrayOf(
        "462.5625", "462.5875", "462.6125", "462.6375", "462.6625",
        "462.6875", "462.7125", "467.5625", "467.5875", "467.6125",
        "467.6375", "467.6625", "467.6875", "467.7125", "462.5500",
        "462.5750", "462.6000", "462.6250", "462.6500", "462.6750",
        "462.7000", "462.7250"
    )

    init {
        setupVoiceCommands()
        setupMediaSession()

        // Request audio focus for radio operations
        audioFocusManager.requestFocus()
        audioFocusManager.setListener(object : AudioFocusManager.AudioFocusListener {
            override fun onFocusGained() {
                Log.d(TAG, "Audio focus gained in car")
            }
            override fun onFocusDucked() {
                Log.d(TAG, "Audio ducked (nav prompt)")
            }
            override fun onFocusPaused() {
                // Auto-release PTT if transmitting during nav prompt
                if (isTransmitting) {
                    isTransmitting = false
                    invalidate()
                }
            }
            override fun onFocusLost() {
                isTransmitting = false
                invalidate()
            }
        })

        // Route audio through car Bluetooth
        if (audioFocusManager.isBluetoothAudioConnected()) {
            audioFocusManager.startBluetoothSco()
        }
    }

    override fun onGetTemplate(): Template {
        val channelDisplay = if (currentChannelName.isNotEmpty()) {
            "Ch ${currentChannel + 1}: $currentChannelName"
        } else {
            "Channel ${currentChannel + 1}"
        }

        val frequencyDisplay = "$currentFrequency MHz"

        val statusText = when {
            isTransmitting -> "TRANSMITTING"
            isReceiving -> "RECEIVING"
            isScanning -> "SCANNING..."
            else -> "IDLE — Ready"
        }

        val pttText = if (isTransmitting) "RELEASE PTT" else "PUSH TO TALK"

        // Build the Pane with radio info and PTT action
        val paneBuilder = Pane.Builder()

        // Channel info row
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(channelDisplay)
                .addText(frequencyDisplay)
                .build()
        )

        // Status row
        paneBuilder.addRow(
            Row.Builder()
                .setTitle(statusText)
                .addText(if (callsign.isNotEmpty()) "Callsign: $callsign" else "No callsign set")
                .build()
        )

        // Connection info row
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("DB20-G GMRS Radio")
                .addText(if (audioFocusManager.isBluetoothAudioConnected())
                    "Audio: Car Bluetooth" else "Audio: Phone Speaker")
                .build()
        )

        // PTT button — the primary action (large, prominent)
        val pttAction = Action.Builder()
            .setTitle(pttText)
            .setBackgroundColor(if (isTransmitting) CarColor.RED else CarColor.GREEN)
            .setOnClickListener {
                togglePtt()
            }
            .build()
        paneBuilder.addAction(pttAction)

        // Scan toggle button
        val scanAction = Action.Builder()
            .setTitle(if (isScanning) "STOP SCAN" else "SCAN")
            .setOnClickListener {
                toggleScan()
            }
            .build()
        paneBuilder.addAction(scanAction)

        // Build action strip for channel navigation (header area)
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("CH ▼")
                    .setOnClickListener { channelDown() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("CH ▲")
                    .setOnClickListener { channelUp() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("DB20-G Radio")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun togglePtt() {
        isTransmitting = !isTransmitting
        if (isTransmitting) {
            sendRadioAction(RadioService.ACTION_PTT_DOWN)
            audioFocusManager.requestTransmitFocus()
            mediaSessionManager.updateState(MediaSessionManager.RadioState.TRANSMITTING)
            Log.d(TAG, "PTT activated from car")
        } else {
            sendRadioAction(RadioService.ACTION_PTT_UP)
            audioFocusManager.requestFocus() // Back to receive focus
            mediaSessionManager.updateState(MediaSessionManager.RadioState.IDLE)
            Log.d(TAG, "PTT released from car")
        }
        invalidate()
    }

    private fun channelUp() {
        currentChannel = (currentChannel + 1) % gmrsChannels.size
        updateChannelDisplay()
        sendRadioAction(RadioService.ACTION_CHANNEL_UP)
        voiceCommandHandler.speak("Channel ${currentChannel + 1}")
        invalidate()
    }

    private fun channelDown() {
        currentChannel = if (currentChannel > 0) currentChannel - 1 else gmrsChannels.size - 1
        updateChannelDisplay()
        sendRadioAction(RadioService.ACTION_CHANNEL_DOWN)
        voiceCommandHandler.speak("Channel ${currentChannel + 1}")
        invalidate()
    }

    private fun toggleScan() {
        isScanning = !isScanning
        if (isScanning) {
            mediaSessionManager.updateState(MediaSessionManager.RadioState.SCANNING)
            voiceCommandHandler.speak("Scanning started")
        } else {
            mediaSessionManager.updateState(MediaSessionManager.RadioState.IDLE)
            voiceCommandHandler.speak("Scanning stopped")
        }
        invalidate()
    }

    private fun updateChannelDisplay() {
        if (currentChannel < gmrsChannels.size) {
            currentFrequency = gmrsChannels[currentChannel]
        }
        currentChannelName = "GMRS ${currentChannel + 1}"

        mediaSessionManager.updateRadioState(
            channelName = currentChannelName,
            frequency = currentFrequency,
            callsign = callsign,
            state = when {
                isTransmitting -> MediaSessionManager.RadioState.TRANSMITTING
                isReceiving -> MediaSessionManager.RadioState.RECEIVING
                isScanning -> MediaSessionManager.RadioState.SCANNING
                else -> MediaSessionManager.RadioState.IDLE
            },
            channelNumber = currentChannel
        )
    }

    private fun setupVoiceCommands() {
        voiceCommandHandler.setListener(object : VoiceCommandHandler.VoiceCommandListener {
            override fun onChangeChannel(channelNumber: Int) {
                if (channelNumber in gmrsChannels.indices) {
                    currentChannel = channelNumber
                    updateChannelDisplay()
                    invalidate()
                }
            }

            override fun onPttActivate() {
                if (!isTransmitting) {
                    togglePtt()
                }
            }

            override fun onPttRelease() {
                if (isTransmitting) {
                    togglePtt()
                }
            }

            override fun onChannelUp() {
                channelUp()
            }

            override fun onChannelDown() {
                channelDown()
            }

            override fun onRequestStatus() {
                voiceCommandHandler.announceStatus(
                    currentChannel,
                    currentChannelName,
                    currentFrequency,
                    isTransmitting
                )
            }

            override fun onStartScan() {
                if (!isScanning) toggleScan()
            }

            override fun onStopScan() {
                if (isScanning) toggleScan()
            }

            override fun onEmergency() {
                // Switch to GMRS emergency channel 20 (462.675)
                currentChannel = 19
                updateChannelDisplay()
                voiceCommandHandler.speak("Emergency mode active. Channel 20.")
                invalidate()
            }
        })
    }

    private fun setupMediaSession() {
        mediaSessionManager.setListener(object : MediaSessionManager.MediaSessionListener {
            override fun onPttToggle() {
                togglePtt()
            }

            override fun onChannelUp() {
                channelUp()
            }

            override fun onChannelDown() {
                channelDown()
            }

            override fun onScanToggle() {
                toggleScan()
            }

            override fun onEmergency() {
                currentChannel = 19
                updateChannelDisplay()
                invalidate()
            }

            override fun onVoiceCommand(query: String) {
                voiceCommandHandler.processCommand(query)
                invalidate()
            }
        })

        // Set initial state
        updateChannelDisplay()
    }

    private fun sendRadioAction(action: String) {
        val intent = Intent(carContext, RadioService::class.java).apply {
            this.action = action
        }
        carContext.startService(intent)
    }
}
