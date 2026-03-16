package com.db20g.controller.serial

import android.content.Context
import android.hardware.usb.UsbDevice
import com.db20g.controller.protocol.DB20GProtocol
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MultiRadioManager(private val context: Context) {

    data class RadioProfile(
        val id: String,
        val label: String,
        val usbDevice: UsbDevice,
        val driver: UsbSerialDriver,
        val serialManager: UsbSerialManager,
        var isConnected: Boolean = false,
        var firmwareVersion: String = "",
        var serialNumber: String = "",
        val connectedAt: Long = System.currentTimeMillis()
    )

    interface RadioEventListener {
        fun onRadioConnected(profile: RadioProfile)
        fun onRadioDisconnected(radioId: String)
        fun onActiveRadioChanged(profile: RadioProfile?)
        fun onRadioListChanged(radios: List<RadioProfile>)
    }

    private val radios = mutableMapOf<String, RadioProfile>()
    private var activeRadioId: String? = null
    private var listener: RadioEventListener? = null
    private var radioCounter = 0

    val connectedRadios: List<RadioProfile> get() = radios.values.filter { it.isConnected }
    val allRadios: List<RadioProfile> get() = radios.values.toList()

    val activeRadio: RadioProfile? get() = activeRadioId?.let { radios[it] }
    val activeSerialManager: UsbSerialManager? get() = activeRadio?.serialManager

    fun setListener(l: RadioEventListener?) {
        listener = l
    }

    fun discoverDevices(): List<UsbSerialDriver> {
        val manager = UsbSerialManager(context)
        return manager.findDevices()
    }

    fun connectRadio(driver: UsbSerialDriver, label: String = "", baudRate: Int = 9600): RadioProfile? {
        val device = driver.device
        val deviceId = buildDeviceId(device)

        // Already connected?
        if (radios[deviceId]?.isConnected == true) {
            return radios[deviceId]
        }

        val serialManager = UsbSerialManager(context)
        val success = serialManager.connect(driver, baudRate)
        if (!success) return null

        radioCounter++
        val displayLabel = label.ifEmpty { "Radio $radioCounter" }

        val profile = RadioProfile(
            id = deviceId,
            label = displayLabel,
            usbDevice = device,
            driver = driver,
            serialManager = serialManager,
            isConnected = true
        )

        radios[deviceId] = profile

        // Auto-select first radio as active
        if (activeRadioId == null) {
            setActiveRadio(deviceId)
        }

        listener?.onRadioConnected(profile)
        listener?.onRadioListChanged(allRadios)
        return profile
    }

    fun disconnectRadio(radioId: String) {
        val profile = radios[radioId] ?: return
        profile.serialManager.disconnect()
        profile.isConnected = false

        if (activeRadioId == radioId) {
            // Switch to another connected radio or null
            val next = connectedRadios.firstOrNull { it.id != radioId }
            setActiveRadio(next?.id)
        }

        listener?.onRadioDisconnected(radioId)
        listener?.onRadioListChanged(allRadios)
    }

    fun disconnectAll() {
        for (profile in radios.values) {
            if (profile.isConnected) {
                profile.serialManager.disconnect()
                profile.isConnected = false
            }
        }
        activeRadioId = null
        listener?.onActiveRadioChanged(null)
        listener?.onRadioListChanged(allRadios)
    }

    fun setActiveRadio(radioId: String?) {
        activeRadioId = radioId
        listener?.onActiveRadioChanged(activeRadio)
    }

    fun removeRadio(radioId: String) {
        val profile = radios[radioId] ?: return
        if (profile.isConnected) {
            profile.serialManager.disconnect()
        }
        radios.remove(radioId)
        if (activeRadioId == radioId) {
            setActiveRadio(connectedRadios.firstOrNull()?.id)
        }
        listener?.onRadioListChanged(allRadios)
    }

    fun renameRadio(radioId: String, newLabel: String) {
        val profile = radios[radioId] ?: return
        radios[radioId] = profile.copy(label = newLabel)
        listener?.onRadioListChanged(allRadios)
    }

    fun updateRadioInfo(radioId: String, firmware: String, serial: String) {
        val profile = radios[radioId] ?: return
        profile.firmwareVersion = firmware
        profile.serialNumber = serial
    }

    fun syncChannelsToRadio(sourceRadioId: String, targetRadioId: String, onResult: (Boolean, String) -> Unit) {
        val source = radios[sourceRadioId]
        val target = radios[targetRadioId]

        if (source == null || target == null) {
            onResult(false, "Radio not found")
            return
        }
        if (!source.isConnected || !target.isConnected) {
            onResult(false, "Both radios must be connected")
            return
        }

        // Download codeplug from source radio, then upload to target
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sourceProtocol = DB20GProtocol(source.serialManager)
                val targetProtocol = DB20GProtocol(target.serialManager)

                val codeplug = sourceProtocol.download { current, total ->
                    // Progress callback for download phase
                }

                targetProtocol.upload(codeplug) { current, total ->
                    // Progress callback for upload phase
                }

                onResult(true, "Synced channels: ${source.label} → ${target.label}")
            } catch (e: Exception) {
                onResult(false, "Sync failed: ${e.message}")
            }
        }
    }

    fun getRadioInventory(): String {
        val sb = StringBuilder()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        sb.appendLine("Radio Inventory — ${connectedRadios.size} connected / ${radios.size} total")
        sb.appendLine("─".repeat(50))

        for (radio in radios.values) {
            val status = if (radio.isConnected) "● CONNECTED" else "○ Disconnected"
            val active = if (radio.id == activeRadioId) " [ACTIVE]" else ""
            sb.appendLine("${radio.label}$active")
            sb.appendLine("  Status: $status")
            sb.appendLine("  Device: ${radio.usbDevice.productName ?: "Unknown"}")
            sb.appendLine("  VID/PID: ${"%04X".format(radio.usbDevice.vendorId)}:${"%04X".format(radio.usbDevice.productId)}")
            if (radio.firmwareVersion.isNotEmpty()) sb.appendLine("  Firmware: ${radio.firmwareVersion}")
            if (radio.serialNumber.isNotEmpty()) sb.appendLine("  Serial: ${radio.serialNumber}")
            sb.appendLine("  Connected: ${sdf.format(Date(radio.connectedAt))}")
            sb.appendLine()
        }

        return sb.toString()
    }

    private fun buildDeviceId(device: UsbDevice): String {
        return "${device.vendorId}:${device.productId}:${device.deviceId}"
    }
}
