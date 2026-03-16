package com.db20g.controller.serial

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

/**
 * Manages USB serial connection to the DB20-G programming cable.
 * Handles device discovery, connection lifecycle, and raw I/O.
 */
class UsbSerialManager(private val context: Context) {

    private var port: UsbSerialPort? = null
    private var driver: UsbSerialDriver? = null

    val isConnected: Boolean get() = port?.isOpen == true

    /**
     * Find all compatible USB serial devices attached.
     */
    fun findDevices(): List<UsbSerialDriver> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
    }

    /**
     * Connect to the specified USB serial driver at the given baud rate.
     */
    fun connect(selectedDriver: UsbSerialDriver, baudRate: Int = 9600): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = usbManager.openDevice(selectedDriver.device)
            ?: return false

        driver = selectedDriver
        port = selectedDriver.ports[0].also {
            it.open(connection)
            it.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            it.dtr = false
            it.rts = pttInverted  // Idle state: inverted=true means RTS high is idle
        }
        return true
    }

    /**
     * Write raw bytes to the serial port.
     */
    fun write(data: ByteArray, timeout: Int = 1000) {
        port?.write(data, timeout)
            ?: throw IOException("Serial port not connected")
    }

    /**
     * Read raw bytes from the serial port with timeout.
     */
    fun read(size: Int, timeout: Int = 2000): ByteArray {
        val buffer = ByteArray(size)
        val bytesRead = port?.read(buffer, timeout)
            ?: throw IOException("Serial port not connected")
        return buffer.copyOf(bytesRead)
    }

    /**
     * Drain/flush the input buffer.
     */
    fun flushInput() {
        try {
            val buf = ByteArray(256)
            port?.read(buf, 100)
        } catch (_: Exception) {
            // Ignore timeout on flush
        }
    }

    // --- PTT Control via serial control lines ---

    /** Which serial control line to use for PTT keying */
    enum class PttLine { RTS, DTR }

    private var pttLine = PttLine.RTS
    private var pttInverted = false  // Some cables use active-low PTT
    private var _pttActive = false

    val isPttActive: Boolean get() = _pttActive

    fun configurePtt(line: PttLine, inverted: Boolean = false) {
        pttLine = line
        pttInverted = inverted
    }

    /**
     * Key the PTT (begin transmitting).
     * Toggles the configured serial control line (RTS or DTR).
     */
    fun pttDown() {
        val p = port ?: return
        val activeState = !pttInverted
        when (pttLine) {
            PttLine.RTS -> p.rts = activeState
            PttLine.DTR -> p.dtr = activeState
        }
        _pttActive = true
    }

    /**
     * Release PTT (stop transmitting).
     */
    fun pttUp() {
        val p = port ?: return
        val inactiveState = pttInverted
        when (pttLine) {
            PttLine.RTS -> p.rts = inactiveState
            PttLine.DTR -> p.dtr = inactiveState
        }
        _pttActive = false
    }

    /**
     * Read CTS state — can be used to detect carrier/squelch open on some cables.
     */
    fun readCts(): Boolean {
        return try { port?.cts == true } catch (_: Exception) { false }
    }

    /**
     * Disconnect and release the USB serial port.
     */
    fun disconnect() {
        if (_pttActive) pttUp()
        try {
            port?.close()
        } catch (_: IOException) {
        }
        port = null
        driver = null
        _pttActive = false
    }

    fun getDevice(): UsbDevice? = driver?.device
}
