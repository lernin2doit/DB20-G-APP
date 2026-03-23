package com.db20g.controller.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * [RadioTransport] backed by Bluetooth Classic SPP (Serial Port Profile).
 *
 * Talks to the ESP32-WROOM-32E interface board over the standard SPP UUID.
 * The ESP32 firmware exposes a virtual serial port that replaces the old
 * CP2102N USB-serial bridge, carrying the same 9600-baud programming
 * protocol data as well as PTT / relay control commands.
 *
 * Command framing (ESP32 firmware side):
 *   0x01 <len_hi> <len_lo> <payload>   → raw serial data forwarded to radio UART
 *   0x02 0x01                           → PTT down
 *   0x02 0x00                           → PTT up
 *   0x03 0x01                           → relay to serial mode
 *   0x03 0x00                           → relay to audio mode
 */
@SuppressLint("MissingPermission")   // Callers must check BLUETOOTH_CONNECT
class BluetoothRadioTransport(private val context: Context) : RadioTransport {

    companion object {
        private const val TAG = "BtRadioTransport"

        /** Standard SPP UUID used by the ESP32 Bluetooth Serial library. */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Command type bytes for the ESP32 control protocol
        private const val CMD_SERIAL_DATA: Byte = 0x01
        private const val CMD_PTT: Byte = 0x02
        private const val CMD_RELAY: Byte = 0x03
    }

    private val listeners = mutableListOf<RadioTransport.Listener>()
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var _pttActive = false

    override val isConnected: Boolean get() = socket?.isConnected == true

    // ---- Discovery ----

    /**
     * Return the paired Bluetooth devices that might be an ESP32 board.
     * The user picks one in the UI; we don't auto-connect.
     */
    fun pairedDevices(): List<BluetoothDevice> {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    // ---- Connection ----

    /**
     * Open an SPP connection to the given device.
     * This is a blocking call — run on a background thread / coroutine.
     */
    fun connect(device: BluetoothDevice): Boolean {
        disconnect()  // clean up any previous session

        return try {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            // Cancel discovery to speed up the connection
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter?.cancelDiscovery()

            s.connect()
            socket = s
            inputStream = s.inputStream
            outputStream = s.outputStream
            Log.i(TAG, "Connected to ${device.name} (${device.address})")
            listeners.forEach { it.onConnected() }
            true
        } catch (e: IOException) {
            Log.e(TAG, "SPP connect failed", e)
            listeners.forEach { it.onError("Connect failed: ${e.message}") }
            false
        }
    }

    // ---- RadioTransport ----

    override fun write(data: ByteArray, timeout: Int) {
        val os = outputStream ?: throw IOException("Not connected")
        // Wrap payload in a CMD_SERIAL_DATA frame
        val frame = ByteArray(3 + data.size)
        frame[0] = CMD_SERIAL_DATA
        frame[1] = ((data.size shr 8) and 0xFF).toByte()
        frame[2] = (data.size and 0xFF).toByte()
        System.arraycopy(data, 0, frame, 3, data.size)
        os.write(frame)
        os.flush()
    }

    override fun read(size: Int, timeout: Int): ByteArray {
        val ins = inputStream ?: throw IOException("Not connected")
        val s = socket ?: throw IOException("Not connected")

        val buffer = ByteArray(size)
        var offset = 0
        val deadline = System.currentTimeMillis() + timeout

        while (offset < size && System.currentTimeMillis() < deadline) {
            if (ins.available() > 0) {
                val n = ins.read(buffer, offset, size - offset)
                if (n < 0) break
                offset += n
            } else {
                Thread.sleep(10)
            }
        }
        return buffer.copyOf(offset)
    }

    override fun flushInput() {
        try {
            val ins = inputStream ?: return
            while (ins.available() > 0) {
                ins.skip(ins.available().toLong())
            }
        } catch (_: IOException) {
            // ignore flush errors
        }
    }

    override fun disconnect() {
        if (_pttActive) pttUp()
        try { inputStream?.close() } catch (_: IOException) {}
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        inputStream = null
        outputStream = null
        socket = null
        _pttActive = false
        listeners.forEach { it.onDisconnected() }
    }

    // ---- PTT over BT ----

    override val supportsPttControl: Boolean get() = true
    override val isPttActive: Boolean get() = _pttActive

    override fun pttDown() {
        sendControlCommand(CMD_PTT, 0x01)
        _pttActive = true
    }

    override fun pttUp() {
        sendControlCommand(CMD_PTT, 0x00)
        _pttActive = false
    }

    /** Switch the DPDT relay: serial mode (true) or audio pass-through (false). */
    fun setRelayMode(serialMode: Boolean) {
        sendControlCommand(CMD_RELAY, if (serialMode) 0x01 else 0x00)
    }

    // ---- Listener ----

    override fun addListener(listener: RadioTransport.Listener) { listeners += listener }
    override fun removeListener(listener: RadioTransport.Listener) { listeners -= listener }

    // ---- Internals ----

    private fun sendControlCommand(type: Byte, value: Byte) {
        try {
            val os = outputStream ?: return
            os.write(byteArrayOf(type, value))
            os.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Control command failed", e)
            listeners.forEach { it.onError("Control command failed: ${e.message}") }
        }
    }
}
