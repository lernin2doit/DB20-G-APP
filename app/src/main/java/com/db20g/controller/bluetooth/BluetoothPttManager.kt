package com.db20g.controller.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages Bluetooth PTT buttons and audio routing for hands-free radio operation.
 *
 * Supports:
 * - BLE HID button mapping (standard media keys used by common PTT buttons)
 * - Configurable button actions (PTT, channel up/down, emergency)
 * - Audio routing through Bluetooth headset/speaker
 * - Multiple simultaneous Bluetooth devices (PTT button + audio headset)
 *
 * Common BT PTT buttons use standard HID key codes:
 * - KEYCODE_MEDIA_PLAY / KEYCODE_MEDIA_PLAY_PAUSE → PTT toggle
 * - KEYCODE_VOLUME_UP → Channel up
 * - KEYCODE_VOLUME_DOWN → Channel down
 * - KEYCODE_CAMERA / KEYCODE_HEADSETHOOK → PTT (some buttons)
 */
class BluetoothPttManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothPttManager"
        private const val PREFS_NAME = "bt_ptt_prefs"
        private const val KEY_ENABLED = "bt_ptt_enabled"
        private const val KEY_AUDIO_ROUTING = "bt_audio_routing"
        private const val KEY_BUTTON_MAPPINGS = "bt_button_mappings"
        private const val KEY_KNOWN_DEVICES = "bt_known_devices"
    }

    // --- Public State ---

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (value) startListening() else stopListening()
        }

    var audioRoutingEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_ROUTING, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUDIO_ROUTING, value).apply()
            if (value) enableBluetoothAudio() else disableBluetoothAudio()
        }

    var isPttActive: Boolean = false
        private set

    // --- Callbacks ---

    var onPttDown: (() -> Unit)? = null
    var onPttUp: (() -> Unit)? = null
    var onChannelUp: (() -> Unit)? = null
    var onChannelDown: (() -> Unit)? = null
    var onEmergencyActivate: (() -> Unit)? = null
    var onDeviceConnected: ((BluetoothDeviceInfo) -> Unit)? = null
    var onDeviceDisconnected: ((BluetoothDeviceInfo) -> Unit)? = null

    // --- Internal State ---

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val connectedDevices = mutableListOf<BluetoothDeviceInfo>()
    private var buttonMappings = mutableMapOf<Int, ButtonAction>()
    private var isListening = false

    // --- Enums & Data Classes ---

    enum class ButtonAction {
        PTT,
        CHANNEL_UP,
        CHANNEL_DOWN,
        EMERGENCY,
        NONE
    }

    data class BluetoothDeviceInfo(
        val address: String,
        val name: String,
        val type: DeviceType,
        var isConnected: Boolean = false,
        var isPttDevice: Boolean = false,
        var isAudioDevice: Boolean = false
    )

    enum class DeviceType {
        PTT_BUTTON,
        AUDIO_HEADSET,
        SPEAKER,
        UNKNOWN
    }

    // --- Initialization ---

    fun initialize() {
        loadButtonMappings()
        loadKnownDevices()

        if (isEnabled) {
            startListening()
        }

        // Set default button mappings if none configured
        if (buttonMappings.isEmpty()) {
            setDefaultMappings()
        }
    }

    fun shutdown() {
        stopListening()
        disableBluetoothAudio()
    }

    // --- Key Event Processing ---

    /**
     * Process a key event from the system. Call this from Activity.onKeyDown/onKeyUp
     * or from a media button BroadcastReceiver.
     *
     * @return true if the event was consumed by this manager
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!isEnabled) return false

        val action = buttonMappings[event.keyCode] ?: return false
        if (action == ButtonAction.NONE) return false

        // Only handle events from Bluetooth devices
        if (event.device == null) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> handleButtonDown(action, event.keyCode)
            KeyEvent.ACTION_UP -> handleButtonUp(action, event.keyCode)
        }

        return true
    }

    private fun handleButtonDown(action: ButtonAction, keyCode: Int) {
        Log.d(TAG, "BT button down: keyCode=$keyCode action=$action")
        when (action) {
            ButtonAction.PTT -> {
                if (!isPttActive) {
                    isPttActive = true
                    onPttDown?.invoke()
                }
            }
            ButtonAction.CHANNEL_UP -> onChannelUp?.invoke()
            ButtonAction.CHANNEL_DOWN -> onChannelDown?.invoke()
            ButtonAction.EMERGENCY -> onEmergencyActivate?.invoke()
            ButtonAction.NONE -> {}
        }
    }

    private fun handleButtonUp(action: ButtonAction, keyCode: Int) {
        Log.d(TAG, "BT button up: keyCode=$keyCode action=$action")
        when (action) {
            ButtonAction.PTT -> {
                if (isPttActive) {
                    isPttActive = false
                    onPttUp?.invoke()
                }
            }
            // Other actions are press-only (triggered on ACTION_DOWN)
            else -> {}
        }
    }

    // --- Button Mapping Configuration ---

    fun getButtonMapping(keyCode: Int): ButtonAction =
        buttonMappings[keyCode] ?: ButtonAction.NONE

    fun setButtonMapping(keyCode: Int, action: ButtonAction) {
        buttonMappings[keyCode] = action
        saveButtonMappings()
    }

    fun getAllMappings(): Map<Int, ButtonAction> = buttonMappings.toMap()

    fun getKeyCodeName(keyCode: Int): String = KeyEvent.keyCodeToString(keyCode)

    /**
     * Set the default button mappings for common BT PTT buttons.
     */
    private fun setDefaultMappings() {
        buttonMappings[KeyEvent.KEYCODE_MEDIA_PLAY] = ButtonAction.PTT
        buttonMappings[KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE] = ButtonAction.PTT
        buttonMappings[KeyEvent.KEYCODE_HEADSETHOOK] = ButtonAction.PTT
        buttonMappings[KeyEvent.KEYCODE_CAMERA] = ButtonAction.PTT
        buttonMappings[KeyEvent.KEYCODE_MEDIA_PREVIOUS] = ButtonAction.CHANNEL_DOWN
        buttonMappings[KeyEvent.KEYCODE_MEDIA_NEXT] = ButtonAction.CHANNEL_UP
        buttonMappings[KeyEvent.KEYCODE_MEDIA_STOP] = ButtonAction.EMERGENCY
        saveButtonMappings()
    }

    fun resetToDefaults() {
        buttonMappings.clear()
        setDefaultMappings()
    }

    // --- Audio Routing ---

    /**
     * Route audio through Bluetooth SCO (Synchronous Connection Oriented) link.
     * This enables two-way audio through a Bluetooth headset.
     */
    @Suppress("DEPRECATION")
    private fun enableBluetoothAudio() {
        if (!audioRoutingEnabled) return

        val hasBluetoothAudio = getConnectedAudioDevices().isNotEmpty()
        if (!hasBluetoothAudio) {
            Log.d(TAG, "No Bluetooth audio devices connected")
            return
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
        Log.i(TAG, "Bluetooth SCO audio enabled")
    }

    @Suppress("DEPRECATION")
    private fun disableBluetoothAudio() {
        if (audioManager.isBluetoothScoOn) {
            audioManager.isBluetoothScoOn = false
            audioManager.stopBluetoothSco()
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.i(TAG, "Bluetooth SCO audio disabled")
        }
    }

    /**
     * Get list of connected Bluetooth audio devices (headsets, speakers).
     */
    fun getConnectedAudioDevices(): List<AudioDeviceInfo> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            }
    }

    /**
     * Check whether Bluetooth audio is currently active.
     */
    @Suppress("DEPRECATION")
    fun isBluetoothAudioActive(): Boolean = audioManager.isBluetoothScoOn

    // --- Device Management ---

    @SuppressLint("MissingPermission")
    fun getConnectedDevices(): List<BluetoothDeviceInfo> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return emptyList()

        val result = mutableListOf<BluetoothDeviceInfo>()

        // Check HID (input) devices — PTT buttons
        try {
            @Suppress("DEPRECATION")
            bluetoothManager?.getConnectedDevices(4 /* BluetoothProfile.INPUT_DEVICE / HID Host */)?.forEach { device ->
                result.add(mapToDeviceInfo(device, DeviceType.PTT_BUTTON))
            }
        } catch (_: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission for HID devices")
        }

        // Check headset/A2DP devices — audio
        try {
            bluetoothManager?.getConnectedDevices(BluetoothProfile.HEADSET)?.forEach { device ->
                result.add(mapToDeviceInfo(device, DeviceType.AUDIO_HEADSET))
            }
        } catch (_: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission for headset devices")
        }

        connectedDevices.clear()
        connectedDevices.addAll(result)
        return result.toList()
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDeviceInfo> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return emptyList()

        return try {
            bluetoothAdapter.bondedDevices
                ?.map { mapToDeviceInfo(it, classifyDevice(it)) }
                ?: emptyList()
        } catch (_: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun mapToDeviceInfo(device: BluetoothDevice, type: DeviceType): BluetoothDeviceInfo {
        val name = try {
            device.name ?: "Unknown"
        } catch (_: SecurityException) {
            "Unknown"
        }
        return BluetoothDeviceInfo(
            address = device.address,
            name = name,
            type = type,
            isConnected = true,
            isPttDevice = type == DeviceType.PTT_BUTTON,
            isAudioDevice = type == DeviceType.AUDIO_HEADSET || type == DeviceType.SPEAKER
        )
    }

    @SuppressLint("MissingPermission")
    private fun classifyDevice(device: BluetoothDevice): DeviceType {
        val btClass = try {
            device.bluetoothClass
        } catch (_: SecurityException) {
            return DeviceType.UNKNOWN
        }
        return when {
            btClass == null -> DeviceType.UNKNOWN
            btClass.majorDeviceClass == 0x0500 -> DeviceType.PTT_BUTTON  // Peripheral
            btClass.majorDeviceClass == 0x0400 -> DeviceType.AUDIO_HEADSET // Audio/Video
            else -> DeviceType.UNKNOWN
        }
    }

    // --- Bluetooth Connection Monitoring ---

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { handleDeviceConnected(it) }
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { handleDeviceDisconnected(it) }
                }

                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    Log.d(TAG, "SCO audio state: $state")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceConnected(device: BluetoothDevice) {
        val type = classifyDevice(device)
        val info = mapToDeviceInfo(device, type)
        connectedDevices.removeAll { it.address == device.address }
        connectedDevices.add(info)
        saveKnownDevices()

        Log.i(TAG, "BT device connected: ${info.name} (${info.type})")
        onDeviceConnected?.invoke(info)

        if (audioRoutingEnabled && (type == DeviceType.AUDIO_HEADSET || type == DeviceType.SPEAKER)) {
            enableBluetoothAudio()
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceDisconnected(device: BluetoothDevice) {
        val info = connectedDevices.find { it.address == device.address }
        connectedDevices.removeAll { it.address == device.address }

        if (info != null) {
            Log.i(TAG, "BT device disconnected: ${info.name}")
            info.isConnected = false
            onDeviceDisconnected?.invoke(info)
        }

        // Release PTT if the PTT device disconnected
        if (info?.isPttDevice == true && isPttActive) {
            isPttActive = false
            onPttUp?.invoke()
        }

        // Disable SCO if no audio devices remain
        if (getConnectedAudioDevices().isEmpty()) {
            disableBluetoothAudio()
        }
    }

    private fun startListening() {
        if (isListening) return
        isListening = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(bluetoothReceiver, filter)
        }

        Log.d(TAG, "BT PTT manager listening")
    }

    private fun stopListening() {
        if (!isListening) return
        isListening = false
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver not registered
        }
        Log.d(TAG, "BT PTT manager stopped")
    }

    // --- Persistence ---

    private fun loadButtonMappings() {
        buttonMappings.clear()
        val json = prefs.getString(KEY_BUTTON_MAPPINGS, null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val keyCode = obj.getInt("keyCode")
                val action = ButtonAction.valueOf(obj.getString("action"))
                buttonMappings[keyCode] = action
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load button mappings", e)
        }
    }

    private fun saveButtonMappings() {
        val arr = JSONArray()
        buttonMappings.forEach { (keyCode, action) ->
            val obj = JSONObject().apply {
                put("keyCode", keyCode)
                put("action", action.name)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_BUTTON_MAPPINGS, arr.toString()).apply()
    }

    private fun loadKnownDevices() {
        val json = prefs.getString(KEY_KNOWN_DEVICES, null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                connectedDevices.add(
                    BluetoothDeviceInfo(
                        address = obj.getString("address"),
                        name = obj.getString("name"),
                        type = DeviceType.valueOf(obj.getString("type")),
                        isConnected = false,
                        isPttDevice = obj.optBoolean("isPtt", false),
                        isAudioDevice = obj.optBoolean("isAudio", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load known devices", e)
        }
    }

    private fun saveKnownDevices() {
        val arr = JSONArray()
        connectedDevices.forEach { dev ->
            val obj = JSONObject().apply {
                put("address", dev.address)
                put("name", dev.name)
                put("type", dev.type.name)
                put("isPtt", dev.isPttDevice)
                put("isAudio", dev.isAudioDevice)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_KNOWN_DEVICES, arr.toString()).apply()
    }
}
