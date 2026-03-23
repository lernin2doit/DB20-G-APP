package com.db20g.controller.transport

/**
 * Abstraction for the physical data transport between the Android app and the
 * DB20-G interface board.  Two implementations exist:
 *
 *  - **UsbRadioTransport**: original USB-serial path (CP2102N via OTG cable)
 *  - **BluetoothRadioTransport**: new BT-SPP path (ESP32-WROOM-32E board)
 *
 * The [com.db20g.controller.protocol.DB20GProtocol] class should depend on
 * this interface instead of the concrete UsbSerialManager, allowing the user
 * to programme their radio over either transport without changing the protocol
 * implementation.
 */
interface RadioTransport {

    /** `true` when a link is open and ready for data. */
    val isConnected: Boolean

    /** Write raw bytes.  Throws [java.io.IOException] on failure. */
    fun write(data: ByteArray, timeout: Int = 1000)

    /** Read up to [size] bytes with the given [timeout]. Returns the bytes actually read. */
    fun read(size: Int, timeout: Int = 2000): ByteArray

    /** Drain/flush any pending input bytes. */
    fun flushInput()

    /** Close the link and release any underlying resources. */
    fun disconnect()

    // ---- PTT helpers (optional for transports that support it) ----

    /** `true` if this transport has built-in PTT control (e.g. RTS/DTR line). */
    val supportsPttControl: Boolean get() = false

    /** Key the radio's PTT via the transport. No-op if unsupported. */
    fun pttDown() {}

    /** Release the radio's PTT via the transport. No-op if unsupported. */
    fun pttUp() {}

    /** Current PTT state, if controllable. */
    val isPttActive: Boolean get() = false

    // ---- Listener ----

    /** Callback for transport lifecycle events. */
    interface Listener {
        fun onConnected() {}
        fun onDisconnected(reason: String? = null) {}
        fun onError(message: String) {}
    }

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}
