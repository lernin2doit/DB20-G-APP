package com.db20g.controller.ui

import android.app.Application
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.db20g.controller.audio.AudioRecorder
import com.db20g.controller.protocol.*
import com.db20g.controller.protocol.DcsPolarity
import com.db20g.controller.repeater.CallsignManager
import com.db20g.controller.repeater.GmrsRepeater
import com.db20g.controller.repeater.RepeaterDatabase
import com.db20g.controller.serial.MultiRadioManager
import com.db20g.controller.serial.UsbSerialManager
import com.db20g.controller.transport.BluetoothRadioTransport
import com.db20g.controller.transport.RadioTransport
import com.db20g.controller.transport.UsbRadioTransport
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.launch
import java.io.File

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val serialManager = UsbSerialManager(application)
    private val btTransport = BluetoothRadioTransport(application)

    /** The currently active transport — either USB or BT. */
    private var activeTransport: RadioTransport = UsbRadioTransport(serialManager)
    private var protocol = DB20GProtocol(activeTransport)

    // Multi-radio support
    val multiRadioManager = MultiRadioManager(application)
    private val _radioProfiles = MutableLiveData<List<MultiRadioManager.RadioProfile>>(emptyList())
    val radioProfiles: LiveData<List<MultiRadioManager.RadioProfile>> = _radioProfiles
    private val _activeRadioProfile = MutableLiveData<MultiRadioManager.RadioProfile?>()
    val activeRadioProfile: LiveData<MultiRadioManager.RadioProfile?> = _activeRadioProfile

    // Connection state
    private val _connectionState = MutableLiveData(ConnectionState.DISCONNECTED)
    val connectionState: LiveData<ConnectionState> = _connectionState

    // Available USB devices
    private val _availableDevices = MutableLiveData<List<UsbSerialDriver>>(emptyList())
    val availableDevices: LiveData<List<UsbSerialDriver>> = _availableDevices

    // Active transport type
    private val _transportType = MutableLiveData(TransportType.USB)
    val transportType: LiveData<TransportType> = _transportType

    // Available BT devices (paired)
    private val _btDevices = MutableLiveData<List<android.bluetooth.BluetoothDevice>>(emptyList())
    val btDevices: LiveData<List<android.bluetooth.BluetoothDevice>> = _btDevices

    // Channels
    private val _channels = MutableLiveData<List<RadioChannel>>(emptyList())
    val channels: LiveData<List<RadioChannel>> = _channels

    // Settings
    private val _settings = MutableLiveData<RadioSettings?>()
    val settings: LiveData<RadioSettings?> = _settings

    // Status / progress
    private val _statusMessage = MutableLiveData("")
    val statusMessage: LiveData<String> = _statusMessage

    private val _progress = MutableLiveData(0)
    val progress: LiveData<Int> = _progress

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // Radio ident
    private val _radioIdent = MutableLiveData("")
    val radioIdent: LiveData<String> = _radioIdent

    // Live operation state
    private val _pttActive = MutableLiveData(false)
    val pttActive: LiveData<Boolean> = _pttActive

    private val _rxActive = MutableLiveData(false)
    val rxActive: LiveData<Boolean> = _rxActive

    private val _activeChannelIndex = MutableLiveData(0)
    val activeChannelIndex: LiveData<Int> = _activeChannelIndex

    // Repeater
    val repeaterDatabase = RepeaterDatabase(application)
    private val _nearestRepeater = MutableLiveData<GmrsRepeater?>()
    val nearestRepeater: LiveData<GmrsRepeater?> = _nearestRepeater
    private val _nearestRepeaterDistance = MutableLiveData<Double?>()
    val nearestRepeaterDistance: LiveData<Double?> = _nearestRepeaterDistance

    // Callsign
    val callsignManager = CallsignManager(application)

    fun scanDevices() {
        val devices = serialManager.findDevices()
        _availableDevices.value = devices
        if (devices.isEmpty()) {
            _statusMessage.value = "No USB serial devices found. Check cable connection."
        } else {
            _statusMessage.value = "Found ${devices.size} USB serial device(s)"
        }
    }

    // --- Bluetooth transport ---

    fun scanBluetoothDevices() {
        val devices = btTransport.pairedDevices()
        _btDevices.value = devices
        if (devices.isEmpty()) {
            _statusMessage.value = "No paired Bluetooth devices. Pair DB20G-Interface in system settings."
        } else {
            _statusMessage.value = "Found ${devices.size} paired BT device(s)"
        }
    }

    @Suppress("MissingPermission") // Callers must check BLUETOOTH_CONNECT
    fun connectBluetooth(device: android.bluetooth.BluetoothDevice) {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                _statusMessage.value = "Connecting via Bluetooth to ${device.name}..."

                val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    btTransport.connect(device)
                }

                if (success) {
                    activeTransport = btTransport
                    protocol = DB20GProtocol(activeTransport)
                    _transportType.value = TransportType.BLUETOOTH
                    _connectionState.value = ConnectionState.CONNECTED
                    _statusMessage.value = "Connected via Bluetooth to ${device.name}"
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _error.value = "Bluetooth connection failed. Is the board powered on?"
                }
            } catch (e: Exception) {
                Log.e(TAG, "BT connection failed", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _error.value = "Bluetooth failed: ${e.message}"
            }
        }
    }

    fun hasUsbPermission(driver: UsbSerialDriver): Boolean {
        val usbManager = getApplication<Application>()
            .getSystemService(Application.USB_SERVICE) as UsbManager
        return usbManager.hasPermission(driver.device)
    }

    fun connect(driver: UsbSerialDriver) {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                _statusMessage.value = "Connecting to ${driver.device.deviceName}..."

                if (serialManager.connect(driver, baudRate = 9600)) {
                    activeTransport = UsbRadioTransport(serialManager)
                    protocol = DB20GProtocol(activeTransport)
                    _transportType.value = TransportType.USB
                    _connectionState.value = ConnectionState.CONNECTED
                    _statusMessage.value = "Connected to ${driver.device.deviceName}"
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _error.value = "Failed to open USB device. Check permissions."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _error.value = "Connection failed: ${e.message}"
            }
        }
    }

    fun disconnect() {
        activeTransport.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _statusMessage.value = "Disconnected"
    }

    fun downloadFromRadio() {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.BUSY
                _statusMessage.value = "Downloading from radio..."
                _progress.value = 0

                protocol.download { current, total ->
                    val pct = (current * 100) / total
                    _progress.postValue(pct)
                }

                _radioIdent.postValue(protocol.radioIdent)
                _statusMessage.postValue("Parsing channels...")

                val channels = protocol.parseChannels()
                _channels.postValue(channels)

                val settings = protocol.parseSettings()
                _settings.postValue(settings)

                _connectionState.postValue(ConnectionState.CONNECTED)
                _statusMessage.postValue(
                    "Downloaded ${channels.count { !it.isEmpty }} channels from radio"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _connectionState.postValue(ConnectionState.CONNECTED)
                _error.postValue("Download failed: ${e.message}")
            }
        }
    }

    // Validation
    private val _validationResults = MutableLiveData<Map<Int, List<ValidationIssue>>>(emptyMap())
    val validationResults: LiveData<Map<Int, List<ValidationIssue>>> = _validationResults

    fun updateSettings(settings: RadioSettings) {
        _settings.value = settings
    }

    fun uploadToRadio() {
        val image = protocol.getMemoryImage()
        if (image == null) {
            _error.value = "No data to upload. Download first or load a file."
            return
        }

        // Pre-upload validation — block on errors
        val currentChannels = _channels.value ?: emptyList()
        if (ChannelValidator.hasBlockingErrors(currentChannels)) {
            val report = ChannelValidator.generateReport(currentChannels)
            _error.value = "Upload blocked — fix errors first:\n$report"
            return
        }

        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.BUSY
                _statusMessage.value = "Uploading to radio..."
                _progress.value = 0

                // Re-encode current channels and settings into the memory image
                val currentChannels = _channels.value ?: emptyList()
                val currentSettings = _settings.value ?: RadioSettings()
                var data = protocol.encodeChannels(currentChannels, image)
                data = protocol.encodeSettings(currentSettings, data)

                protocol.upload(data) { current, total ->
                    val pct = (current * 100) / total
                    _progress.postValue(pct)
                }

                _connectionState.postValue(ConnectionState.CONNECTED)
                _statusMessage.postValue("Upload complete!")
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                _connectionState.postValue(ConnectionState.CONNECTED)
                _error.postValue("Upload failed: ${e.message}")
            }
        }
    }

    fun updateChannel(channel: RadioChannel) {
        val list = _channels.value?.toMutableList() ?: return
        val index = list.indexOfFirst { it.number == channel.number }
        if (index >= 0) {
            list[index] = channel
            _channels.value = list
        }
    }

    fun deleteChannel(channelNumber: Int) {
        val list = _channels.value?.toMutableList() ?: return
        val index = list.indexOfFirst { it.number == channelNumber }
        if (index >= 0) {
            list[index] = RadioChannel(number = channelNumber, isEmpty = true)
            _channels.value = list
        }
    }

    /**
     * Replace the entire channel list (used by undo/redo and bulk edits).
     */
    fun setChannels(channels: List<RadioChannel>) {
        _channels.value = channels.toList()
    }

    fun addChannel(channel: RadioChannel) {
        val list = _channels.value?.toMutableList() ?: MutableList(128) {
            RadioChannel(number = it, isEmpty = true)
        }
        val index = list.indexOfFirst { it.number == channel.number }
        if (index >= 0) {
            list[index] = channel
        }
        _channels.value = list
    }

    /**
     * Save memory image to a file for backup.
     */
    fun saveToFile(file: File) {
        val image = protocol.getMemoryImage()
        if (image == null) {
            _error.value = "No data to save"
            return
        }
        try {
            file.writeBytes(image)
            _statusMessage.value = "Saved to ${file.name}"
        } catch (e: Exception) {
            _error.value = "Save failed: ${e.message}"
        }
    }

    /**
     * Load a memory image from file.
     */
    fun loadFromFile(file: File) {
        try {
            val data = file.readBytes()
            if (data.size < DB20GProtocol.MEM_TOTAL) {
                _error.value = "Invalid file: too small (${data.size} bytes)"
                return
            }
            protocol.setMemoryImage(data)
            val channels = protocol.parseChannels(data)
            _channels.value = channels
            val settings = protocol.parseSettings(data)
            _settings.value = settings
            _statusMessage.value = "Loaded ${channels.count { !it.isEmpty }} channels from file"
        } catch (e: Exception) {
            _error.value = "Load failed: ${e.message}"
        }
    }

    /**
     * Export channels to CHIRP-compatible CSV.
     */
    fun exportChirpCsv(output: java.io.OutputStream) {
        val channels = _channels.value ?: emptyList()
        try {
            ChirpCsvManager.exportCsv(channels, output)
            _statusMessage.value = "Exported ${channels.count { !it.isEmpty }} channels to CHIRP CSV"
        } catch (e: Exception) {
            _error.value = "CSV export failed: ${e.message}"
        }
    }

    /**
     * Import channels from a CHIRP CSV or .img file.
     * Returns the import result for preview before applying.
     */
    fun importChirpFile(file: File): ChirpCsvManager.ImportResult {
        return ChirpCsvManager.detectAndImport(file, protocol)
    }

    /**
     * Apply imported channels into the current channel list.
     * Merges into existing slots based on channel number.
     */
    fun applyImportedChannels(imported: List<RadioChannel>) {
        val current = _channels.value?.toMutableList()
            ?: MutableList(128) { RadioChannel(number = it, isEmpty = true) }

        for (ch in imported) {
            val slot = ch.number
            if (slot in current.indices) {
                current[slot] = ch
            }
        }
        _channels.value = current
        validateChannels()
        _statusMessage.value = "Applied ${imported.size} imported channels"
    }

    // --- Live operation controls ---

    fun pttDown() {
        activeTransport.pttDown()
        _pttActive.value = true
        callsignManager.onPttDown()
        startRecording(AudioRecorder.RecordingType.TX)
        logRadioEvent("PTT_DOWN", "TX started on Ch ${_activeChannelIndex.value}")
    }

    fun pttUp() {
        activeTransport.pttUp()
        _pttActive.value = false
        callsignManager.onPttUp()
        stopRecording()
        logRadioEvent("PTT_UP", "TX ended on Ch ${_activeChannelIndex.value}")
    }

    fun configurePtt(line: UsbSerialManager.PttLine, inverted: Boolean) {
        serialManager.configurePtt(line, inverted)
    }

    fun setActiveChannel(channelNumber: Int) {
        val prev = _activeChannelIndex.value
        _activeChannelIndex.value = channelNumber
        if (prev != channelNumber) {
            logRadioEvent("CH_CHANGE", "Channel changed from $prev to $channelNumber")
        }
    }

    /**
     * Poll CTS line to detect receive activity (carrier/squelch open).
     */
    fun pollRxStatus() {
        // CTS polling only available over USB transport
        val active = if (_transportType.value == TransportType.USB) serialManager.readCts() else false
        val wasActive = _rxActive.value ?: false
        _rxActive.postValue(active)

        // Start/stop RX recording on squelch transitions
        if (active && !wasActive) {
            startRecording(AudioRecorder.RecordingType.RX)
            logRadioEvent("RX_OPEN", "Squelch opened on Ch ${_activeChannelIndex.value}")
        } else if (!active && wasActive) {
            stopRecording()
            logRadioEvent("RX_CLOSE", "Squelch closed on Ch ${_activeChannelIndex.value}")
        }
    }

    // --- Repeater controls ---

    fun loadRepeaterDatabase() {
        viewModelScope.launch {
            repeaterDatabase.load()
            _statusMessage.postValue("Loaded ${repeaterDatabase.count} repeaters")
        }
    }

    fun findNearestRepeater(latitude: Double, longitude: Double) {
        val nearest = repeaterDatabase.findNearest(latitude, longitude, limit = 1)
        if (nearest.isNotEmpty()) {
            val result = nearest[0]
            _nearestRepeater.value = result.repeater
            _nearestRepeaterDistance.value = result.distanceMiles
        } else {
            _nearestRepeater.value = null
            _nearestRepeaterDistance.value = null
        }
    }

    /**
     * Program the nearest repeater's frequency/tone into a channel and switch to it.
     */
    fun connectToRepeater(repeater: GmrsRepeater, channelNumber: Int = 0) {
        val list = _channels.value?.toMutableList() ?: return
        if (channelNumber >= list.size) return

        val toneValue = if (repeater.ctcssTone > 0.0) {
            ToneValue.CTCSS(repeater.ctcssTone)
        } else if (repeater.dcsCode > 0) {
            ToneValue.DCS(repeater.dcsCode, DcsPolarity.NORMAL)
        } else {
            ToneValue.None
        }

        val channel = RadioChannel(
            number = channelNumber,
            name = repeater.callsign.take(10),
            rxFrequency = repeater.outputFrequency,
            txFrequency = repeater.inputFrequency,
            txTone = toneValue,
            rxTone = toneValue,
            isEmpty = false
        )
        list[channelNumber] = channel
        _channels.value = list
        _activeChannelIndex.value = channelNumber
        _statusMessage.value = "Connected to ${repeater.callsign} on Ch ${channelNumber + 1}"

        // Quick-reprogram: upload just this channel
        viewModelScope.launch {
            try {
                val image = protocol.getMemoryImage() ?: return@launch
                val data = protocol.encodeChannels(list, image)
                protocol.upload(data) { _, _ -> }
                _statusMessage.postValue("Repeater ${repeater.callsign} programmed to Ch ${channelNumber + 1}")
            } catch (e: Exception) {
                Log.w(TAG, "Quick reprogram failed, changes are local only", e)
            }
        }

        // Trigger callsign ID on connect
        callsignManager.transmitId()
    }

    // --- Audio Recording Integration ---

    private var audioRecorder: AudioRecorder? = null
    private var recordingEnabled = false

    fun setAudioRecorder(recorder: AudioRecorder) {
        audioRecorder = recorder
        recordingEnabled = recorder.isEnabled
    }

    fun setRecordingEnabled(enabled: Boolean) {
        recordingEnabled = enabled
    }

    /**
     * Called by audio engine's onAudioData callback to feed the recorder.
     * Direction (RX/TX) must be determined by the caller.
     */
    fun feedRecorder(samples: ShortArray, count: Int) {
        audioRecorder?.writeAudioData(samples, count)
    }

    /**
     * Start recording for the given type on the current active channel.
     */
    fun startRecording(type: AudioRecorder.RecordingType) {
        if (!recordingEnabled) return
        val chIndex = _activeChannelIndex.value ?: 0
        val chName = _channels.value?.getOrNull(chIndex)?.name ?: "Ch $chIndex"
        audioRecorder?.startRecording(type, chIndex, chName)
    }

    /**
     * Stop any active recording.
     */
    fun stopRecording() {
        audioRecorder?.stopRecording()
    }

    /**
     * Log a radio event to the activity log.
     */
    fun logRadioEvent(type: String, message: String) {
        audioRecorder?.logEvent(type, message)
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Run validation on all channels and update LiveData with results.
     * Call after channel edits, downloads, or imports.
     */
    fun validateChannels() {
        val channels = _channels.value ?: return
        _validationResults.value = ChannelValidator.validateAll(channels)
    }

    /**
     * Get validation report string for display.
     */
    fun getValidationReport(): String {
        val channels = _channels.value ?: return "No channels loaded"
        return ChannelValidator.generateReport(channels)
    }

    // --- Multi-Radio Support ---

    fun initMultiRadio() {
        multiRadioManager.setListener(object : MultiRadioManager.RadioEventListener {
            override fun onRadioConnected(profile: MultiRadioManager.RadioProfile) {
                _radioProfiles.postValue(multiRadioManager.allRadios)
                logRadioEvent("RADIO_CONNECT", "Connected: ${profile.label}")
            }

            override fun onRadioDisconnected(radioId: String) {
                _radioProfiles.postValue(multiRadioManager.allRadios)
                logRadioEvent("RADIO_DISCONNECT", "Disconnected: $radioId")
                if (multiRadioManager.connectedRadios.isEmpty()) {
                    _connectionState.postValue(ConnectionState.DISCONNECTED)
                }
            }

            override fun onActiveRadioChanged(profile: MultiRadioManager.RadioProfile?) {
                _activeRadioProfile.postValue(profile)
                if (profile != null) {
                    _statusMessage.postValue("Active radio: ${profile.label}")
                }
            }

            override fun onRadioListChanged(radios: List<MultiRadioManager.RadioProfile>) {
                _radioProfiles.postValue(radios)
            }
        })
    }

    fun connectMultiRadio(driver: UsbSerialDriver, label: String = "") {
        viewModelScope.launch {
            try {
                _statusMessage.value = "Connecting ${label.ifEmpty { driver.device.deviceName }}..."
                val profile = multiRadioManager.connectRadio(driver, label)
                if (profile != null) {
                    _connectionState.value = ConnectionState.CONNECTED
                    _statusMessage.value = "Connected: ${profile.label}"
                } else {
                    _error.value = "Failed to connect radio"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Multi-radio connect failed", e)
                _error.value = "Connect failed: ${e.message}"
            }
        }
    }

    fun disconnectMultiRadio(radioId: String) {
        multiRadioManager.disconnectRadio(radioId)
    }

    fun switchActiveRadio(radioId: String) {
        multiRadioManager.setActiveRadio(radioId)
    }

    fun renameRadio(radioId: String, newLabel: String) {
        multiRadioManager.renameRadio(radioId, newLabel)
    }

    fun getRadioInventory(): String = multiRadioManager.getRadioInventory()

    fun syncRadioChannels(sourceId: String, targetId: String) {
        multiRadioManager.syncChannelsToRadio(sourceId, targetId) { success, msg ->
            if (success) {
                _statusMessage.postValue(msg)
            } else {
                _error.postValue(msg)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        callsignManager.release()
        multiRadioManager.disconnectAll()
        activeTransport.disconnect()
        btTransport.disconnect()
        serialManager.disconnect()
    }

    companion object {
        private const val TAG = "RadioViewModel"
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    BUSY
}

enum class TransportType {
    USB,
    BLUETOOTH
}
