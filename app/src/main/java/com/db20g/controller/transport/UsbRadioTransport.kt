package com.db20g.controller.transport

import com.db20g.controller.serial.UsbSerialManager

/**
 * [RadioTransport] backed by the existing [UsbSerialManager] (USB-serial via CP2102N).
 * This is a thin adapter so that the protocol layer can work against [RadioTransport]
 * instead of the concrete USB manager.
 */
class UsbRadioTransport(private val usb: UsbSerialManager) : RadioTransport {

    private val listeners = mutableListOf<RadioTransport.Listener>()

    override val isConnected: Boolean get() = usb.isConnected

    override fun write(data: ByteArray, timeout: Int) = usb.write(data, timeout)

    override fun read(size: Int, timeout: Int): ByteArray = usb.read(size, timeout)

    override fun flushInput() = usb.flushInput()

    override fun disconnect() {
        usb.disconnect()
        listeners.forEach { it.onDisconnected() }
    }

    // USB transport supports hardware PTT via RTS/DTR
    override val supportsPttControl: Boolean get() = true
    override val isPttActive: Boolean get() = usb.isPttActive
    override fun pttDown() = usb.pttDown()
    override fun pttUp() = usb.pttUp()

    override fun addListener(listener: RadioTransport.Listener) { listeners += listener }
    override fun removeListener(listener: RadioTransport.Listener) { listeners -= listener }
}
