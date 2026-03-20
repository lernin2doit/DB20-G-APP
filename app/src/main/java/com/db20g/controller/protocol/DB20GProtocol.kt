package com.db20g.controller.protocol

import android.util.Log
import com.db20g.controller.serial.UsbSerialManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Implements the Radioddity DB20-G serial programming protocol.
 *
 * Protocol (derived from CHIRP open-source GA-510 family driver):
 *   - Baud: 9600, 8N1
 *   - Reset: send 'E', flush
 *   - Enter program mode: send magic -> ACK 0x06 -> send 'F' -> 8-byte ident
 *   - Read: 'R' + uint16be(addr) + uint8(len=0x40) -> response: 'R' + uint16be + uint8 + data
 *   - Write: 'W' + uint16be(addr) + uint8(len=0x20) + data -> ACK 0x06
 *   - Memory: 0x0000-0x1C00 (channels + names + settings)
 */
class DB20GProtocol(private val serial: UsbSerialManager) {

    companion object {
        private const val TAG = "DB20GProtocol"

        // Magic strings for the GA-510 / DB20-G family
        // The DB20-G uses the same magic as the GA-510
        private val MAGIC_GA510 = "PROGROMBFHU".toByteArray()

        // Memory layout constants
        const val MEM_START = 0x0000
        const val MEM_END = 0x1C00
        const val MEM_TOTAL = 0x1C40
        const val READ_BLOCK_SIZE = 0x40
        const val WRITE_BLOCK_SIZE = 0x20

        // Channel memory: 128 clone-mode slots * 16 bytes = 0x0800
        // Note: The radio has 500 total channels (30 GMRS + 9 repeater + 454 programmable + 7 NOAA).
        // Clone mode exposes 128 programmable slots; fixed GMRS/NOAA channels are in firmware.
        const val CHANNEL_MEM_START = 0x0000
        const val CHANNEL_SIZE = 16
        const val MAX_CHANNELS = 128

        // Channel names: offset 0x0C00, 128 * 16 bytes
        const val NAMES_START = 0x0C00
        const val NAME_SIZE = 16
        const val NAME_LENGTH = 10

        // Settings start at 0x1A00
        const val SETTINGS_START = 0x1A00
    }

    private var memoryImage: ByteArray? = null
    var radioIdent: String = ""
        private set

    /**
     * Reset the radio to exit any active mode.
     */
    private fun reset() {
        try {
            serial.write(byteArrayOf('E'.code.toByte()))
        } catch (e: Exception) {
            Log.w(TAG, "Reset failed: ${e.message}")
        }
    }

    /**
     * Enter programming mode and identify the radio.
     */
    private fun startProgram(): String {
        reset()
        Thread.sleep(100)
        serial.flushInput()

        // Send magic string
        serial.write(MAGIC_GA510)
        Thread.sleep(200)

        val ack = serial.read(256, 3000)
        if (ack.isEmpty() || ack.last() != 0x06.toByte()) {
            Log.e(TAG, "No ACK from radio. Got: ${ack.toHexString()}")
            throw IOException("Radio did not respond to clone request")
        }

        // Send 'F' to request ident
        serial.write(byteArrayOf('F'.code.toByte()))
        Thread.sleep(100)

        val ident = serial.read(8, 2000)
        radioIdent = ident.decodeToString().trim()
        Log.i(TAG, "Radio identified as: $radioIdent (${ident.toHexString()})")

        return radioIdent
    }

    /**
     * Download the entire memory image from the radio.
     */
    suspend fun download(onProgress: (Int, Int) -> Unit = { _, _ -> }): ByteArray =
        withContext(Dispatchers.IO) {
            startProgram()

            val data = ByteArray(MEM_TOTAL)
            var offset = 0
            val totalBlocks = MEM_TOTAL / READ_BLOCK_SIZE

            for (addr in 0 until MEM_TOTAL step READ_BLOCK_SIZE) {
                val cmd = ByteBuffer.allocate(4).apply {
                    order(ByteOrder.BIG_ENDIAN)
                    put('R'.code.toByte())
                    putShort(addr.toShort())
                    put(READ_BLOCK_SIZE.toByte())
                }.array()

                serial.write(cmd)
                Thread.sleep(50)

                val response = serial.read(READ_BLOCK_SIZE + 4, 3000)
                if (response.size < READ_BLOCK_SIZE + 4) {
                    throw IOException(
                        "Short read at 0x%04X: got %d bytes, expected %d".format(
                            addr, response.size, READ_BLOCK_SIZE + 4
                        )
                    )
                }

                // Parse response header
                val rAddr = ((response[1].toInt() and 0xFF) shl 8) or
                        (response[2].toInt() and 0xFF)
                val rLen = response[3].toInt() and 0xFF

                if (rAddr != addr) {
                    throw IOException(
                        "Address mismatch: expected 0x%04X, got 0x%04X".format(addr, rAddr)
                    )
                }
                if (rLen != READ_BLOCK_SIZE) {
                    throw IOException(
                        "Length mismatch: expected 0x%02X, got 0x%02X".format(READ_BLOCK_SIZE, rLen)
                    )
                }

                System.arraycopy(response, 4, data, offset, READ_BLOCK_SIZE)
                offset += READ_BLOCK_SIZE

                onProgress(addr / READ_BLOCK_SIZE + 1, totalBlocks)
            }

            reset()
            memoryImage = data
            data
        }

    /**
     * Upload a memory image to the radio.
     */
    suspend fun upload(data: ByteArray, onProgress: (Int, Int) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
            startProgram()

            val uploadEnd = 0x1C20 // Factory software only uploads up to 0x1C20
            val totalBlocks = uploadEnd / WRITE_BLOCK_SIZE

            for (addr in 0 until uploadEnd step WRITE_BLOCK_SIZE) {
                val cmd = ByteBuffer.allocate(4).apply {
                    order(ByteOrder.BIG_ENDIAN)
                    put('W'.code.toByte())
                    putShort(addr.toShort())
                    put(WRITE_BLOCK_SIZE.toByte())
                }.array()

                serial.write(cmd)
                serial.write(data.copyOfRange(addr, addr + WRITE_BLOCK_SIZE))
                Thread.sleep(50)

                val ack = serial.read(1, 2000)
                if (ack.isEmpty() || ack[0] != 0x06.toByte()) {
                    throw IOException("Radio refused block at 0x%04X".format(addr))
                }

                onProgress(addr / WRITE_BLOCK_SIZE + 1, totalBlocks)
            }

            reset()
            memoryImage = data
        }

    /**
     * Parse the downloaded memory image into channel objects.
     */
    fun parseChannels(data: ByteArray = memoryImage ?: throw IllegalStateException("No memory image")): List<RadioChannel> {
        val channels = mutableListOf<RadioChannel>()

        for (i in 0 until MAX_CHANNELS) {
            val memOffset = CHANNEL_MEM_START + i * CHANNEL_SIZE
            val nameOffset = NAMES_START + i * NAME_SIZE

            val mem = data.copyOfRange(memOffset, memOffset + CHANNEL_SIZE)

            // Check if channel is empty (all 0xFF in rxfreq)
            val rxRaw = decodeBcdFreq(mem, 0)
            if (rxRaw == 166666665L || mem.all { it == 0xFF.toByte() }) {
                channels.add(RadioChannel(number = i, isEmpty = true))
                continue
            }

            val txRaw = decodeBcdFreq(mem, 4)
            val rxTone = decodeTone(((mem[9].toInt() and 0xFF) shl 8) or (mem[8].toInt() and 0xFF))
            val txTone = decodeTone(((mem[11].toInt() and 0xFF) shl 8) or (mem[10].toInt() and 0xFF))
            val signal = mem[12].toInt() and 0xFF
            val pttid = (mem[13].toInt() and 0x03)
            val power = (mem[14].toInt() and 0x03)
            val narrow = (mem[15].toInt() and 0x40) != 0
            val bcl = (mem[15].toInt() and 0x04) != 0
            val scan = (mem[15].toInt() and 0x02) != 0
            val fhss = (mem[15].toInt() and 0x01) != 0

            // Parse name
            val nameBytes = data.copyOfRange(nameOffset, nameOffset + NAME_LENGTH)
            val name = nameBytes
                .takeWhile { it != 0xFF.toByte() && it.toInt() != 0 }
                .toByteArray()
                .decodeToString()
                .trim()

            // TX frequency: check for TX inhibit (all 0xFF)
            val txInhibit = mem.copyOfRange(4, 8).all { it == 0xFF.toByte() }
            val txFreq = if (txInhibit) 0.0 else txRaw * 10.0 / 1_000_000.0

            channels.add(
                RadioChannel(
                    number = i,
                    rxFrequency = rxRaw * 10.0 / 1_000_000.0,
                    txFrequency = txFreq,
                    rxTone = rxTone,
                    txTone = txTone,
                    signalCode = signal,
                    pttId = PttIdMode.entries[pttid.coerceIn(0, 3)],
                    power = PowerLevel.entries[power.coerceIn(0, 2)],
                    wideband = !narrow,
                    bcl = bcl,
                    scan = scan,
                    fhss = fhss,
                    name = name,
                    isEmpty = false
                )
            )
        }

        return channels
    }

    /**
     * Encode channels back into the memory image for upload.
     */
    fun encodeChannels(channels: List<RadioChannel>, data: ByteArray = memoryImage ?: ByteArray(MEM_TOTAL) { 0xFF.toByte() }): ByteArray {
        val result = data.copyOf()

        for (ch in channels) {
            val memOffset = CHANNEL_MEM_START + ch.number * CHANNEL_SIZE
            val nameOffset = NAMES_START + ch.number * NAME_SIZE

            if (ch.isEmpty) {
                for (j in 0 until CHANNEL_SIZE) result[memOffset + j] = 0xFF.toByte()
                for (j in 0 until NAME_SIZE) result[nameOffset + j] = 0xFF.toByte()
                continue
            }

            // Encode RX frequency as BCD
            encodeBcdFreq(result, memOffset, (ch.rxFrequency * 1_000_000.0 / 10.0).toLong())

            // Encode TX frequency
            if (ch.txFrequency == 0.0) {
                for (j in 4..7) result[memOffset + j] = 0xFF.toByte()
            } else {
                encodeBcdFreq(result, memOffset + 4, (ch.txFrequency * 1_000_000.0 / 10.0).toLong())
            }

            // Tones (little-endian 16-bit)
            val rxToneVal = encodeTone(ch.rxTone)
            result[memOffset + 8] = (rxToneVal and 0xFF).toByte()
            result[memOffset + 9] = ((rxToneVal shr 8) and 0xFF).toByte()

            val txToneVal = encodeTone(ch.txTone)
            result[memOffset + 10] = (txToneVal and 0xFF).toByte()
            result[memOffset + 11] = ((txToneVal shr 8) and 0xFF).toByte()

            result[memOffset + 12] = ch.signalCode.toByte()
            result[memOffset + 13] = (result[memOffset + 13].toInt() and 0xFC or ch.pttId.ordinal).toByte()
            result[memOffset + 14] = (result[memOffset + 14].toInt() and 0xFC or ch.power.ordinal).toByte()

            // Preserve unknown flag bits (0x08, 0x10, 0x20, 0x80) from existing data
            val existingFlags = result[memOffset + 15].toInt() and 0xFF
            var flags = existingFlags and 0xB8.toInt() // keep bits 3,4,5,7
            if (!ch.wideband) flags = flags or 0x40
            if (ch.bcl) flags = flags or 0x04
            if (ch.scan) flags = flags or 0x02
            if (ch.fhss) flags = flags or 0x01
            result[memOffset + 15] = flags.toByte()

            // Encode name
            val nameBytes = ch.name.padEnd(NAME_LENGTH).toByteArray().copyOf(NAME_LENGTH)
            System.arraycopy(nameBytes, 0, result, nameOffset, NAME_LENGTH)
            // Pad remaining 6 bytes of name block with 0xFF
            for (j in NAME_LENGTH until NAME_SIZE) result[nameOffset + j] = 0xFF.toByte()
        }

        memoryImage = result
        return result
    }

    /**
     * Parse radio settings from the memory image.
     */
    fun parseSettings(data: ByteArray = memoryImage ?: throw IllegalStateException("No memory image")): RadioSettings {
        val s = data.copyOfRange(SETTINGS_START, SETTINGS_START + 32)
        return RadioSettings(
            squelch = s[0].toInt() and 0xFF,
            saveMode = SaveMode.entries.getOrElse(s[1].toInt() and 0xFF) { SaveMode.OFF },
            vox = s[2].toInt() and 0xFF,
            backlight = s[3].toInt() and 0xFF,
            tdr = (s[4].toInt() and 0xFF) != 0,
            timeoutTimer = s[5].toInt() and 0xFF,
            beep = (s[6].toInt() and 0xFF) != 0,
            voice = (s[7].toInt() and 0xFF) != 0,
            language = Language.entries.getOrElse(s[8].toInt() and 0xFF) { Language.ENGLISH },
            dtmfSideTone = DtmfSideTone.entries.getOrElse(s[9].toInt() and 0xFF) { DtmfSideTone.OFF },
            scanMode = ScanMode.entries.getOrElse(s[10].toInt() and 0xFF) { ScanMode.TIME_OPERATED },
            pttId = PttIdMode.entries.getOrElse(s[11].toInt() and 0xFF) { PttIdMode.OFF },
            pttDelay = s[12].toInt() and 0xFF,
            channelADisplay = ChannelDisplay.entries.getOrElse(s[13].toInt() and 0xFF) { ChannelDisplay.CH_NAME },
            channelBDisplay = ChannelDisplay.entries.getOrElse(s[14].toInt() and 0xFF) { ChannelDisplay.CH_NAME },
            bcl = (s[15].toInt() and 0xFF) != 0,
            autoLock = (s[16].toInt() and 0xFF) != 0,
            alarmMode = AlarmMode.entries.getOrElse(s[17].toInt() and 0xFF) { AlarmMode.SITE },
            alarmSound = (s[18].toInt() and 0xFF) != 0,
            txUnderTdr = TxUnderTdr.entries.getOrElse(s[19].toInt() and 0xFF) { TxUnderTdr.OFF },
            tailNoiseClear = (s[20].toInt() and 0xFF) != 0,
            rptNoiseClear = s[21].toInt() and 0xFF,
            rptNoiseDetect = s[22].toInt() and 0xFF,
            roger = (s[23].toInt() and 0xFF) != 0,
            fmRadioDisabled = (s[24].toInt() and 0xFF) != 0,
            workMode = if ((s[25].toInt() and 0xFF) == 0) WorkMode.CHANNEL else WorkMode.VFO,
            keyLock = (s[26].toInt() and 0xFF) != 0,
        )
    }

    fun encodeSettings(settings: RadioSettings, data: ByteArray = memoryImage ?: ByteArray(MEM_TOTAL) { 0xFF.toByte() }): ByteArray {
        val result = data.copyOf()
        val base = SETTINGS_START
        result[base + 0] = (settings.squelch and 0xFF).toByte()
        result[base + 1] = (settings.saveMode.ordinal and 0xFF).toByte()
        result[base + 2] = (settings.vox and 0xFF).toByte()
        result[base + 3] = (settings.backlight and 0xFF).toByte()
        result[base + 4] = (if (settings.tdr) 1 else 0).toByte()
        result[base + 5] = (settings.timeoutTimer and 0xFF).toByte()
        result[base + 6] = (if (settings.beep) 1 else 0).toByte()
        result[base + 7] = (if (settings.voice) 1 else 0).toByte()
        result[base + 8] = (settings.language.ordinal and 0xFF).toByte()
        result[base + 9] = (settings.dtmfSideTone.ordinal and 0xFF).toByte()
        result[base + 10] = (settings.scanMode.ordinal and 0xFF).toByte()
        result[base + 11] = (settings.pttId.ordinal and 0xFF).toByte()
        result[base + 12] = (settings.pttDelay and 0xFF).toByte()
        result[base + 13] = (settings.channelADisplay.ordinal and 0xFF).toByte()
        result[base + 14] = (settings.channelBDisplay.ordinal and 0xFF).toByte()
        result[base + 15] = (if (settings.bcl) 1 else 0).toByte()
        result[base + 16] = (if (settings.autoLock) 1 else 0).toByte()
        result[base + 17] = (settings.alarmMode.ordinal and 0xFF).toByte()
        result[base + 18] = (if (settings.alarmSound) 1 else 0).toByte()
        result[base + 19] = (settings.txUnderTdr.ordinal and 0xFF).toByte()
        result[base + 20] = (if (settings.tailNoiseClear) 1 else 0).toByte()
        result[base + 21] = (settings.rptNoiseClear and 0xFF).toByte()
        result[base + 22] = (settings.rptNoiseDetect and 0xFF).toByte()
        result[base + 23] = (if (settings.roger) 1 else 0).toByte()
        result[base + 24] = (if (settings.fmRadioDisabled) 1 else 0).toByte()
        result[base + 25] = (if (settings.workMode == WorkMode.VFO) 1 else 0).toByte()
        result[base + 26] = (if (settings.keyLock) 1 else 0).toByte()
        memoryImage = result
        return result
    }

    // --- BCD frequency encoding/decoding ---

    /**
     * Decode 4-byte little-endian BCD frequency.
     * Each nibble is one BCD digit, yielding an 8-digit number.
     */
    private fun decodeBcdFreq(data: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 4) {
            val b = data[offset + i].toInt() and 0xFF
            val lo = b and 0x0F
            val hi = (b shr 4) and 0x0F
            result = result + lo * pow10(i * 2) + hi * pow10(i * 2 + 1)
        }
        return result
    }

    private fun encodeBcdFreq(data: ByteArray, offset: Int, freq: Long) {
        var f = freq
        for (i in 0 until 4) {
            val lo = (f % 10).toInt()
            f /= 10
            val hi = (f % 10).toInt()
            f /= 10
            data[offset + i] = ((hi shl 4) or lo).toByte()
        }
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }

    // --- Tone encoding/decoding ---

    private fun decodeTone(value: Int): ToneValue {
        if (value == 0 || value == 0xFFFF) return ToneValue.None

        return if (value < 670) {
            // DCS code
            val adjusted = value - 1
            val index = adjusted % GmrsConstants.DCS_CODES.size
            val polarity = if (adjusted >= GmrsConstants.DCS_CODES.size) {
                DcsPolarity.INVERTED
            } else {
                DcsPolarity.NORMAL
            }
            if (index in GmrsConstants.DCS_CODES.indices) {
                ToneValue.DCS(GmrsConstants.DCS_CODES[index], polarity)
            } else {
                ToneValue.None
            }
        } else {
            // CTCSS tone (value is tone * 10)
            ToneValue.CTCSS(value / 10.0)
        }
    }

    private fun encodeTone(tone: ToneValue): Int {
        return when (tone) {
            is ToneValue.None -> 0x0000
            is ToneValue.CTCSS -> (tone.frequency * 10).toInt()
            is ToneValue.DCS -> {
                val index = GmrsConstants.DCS_CODES.indexOf(tone.code)
                if (index < 0) 0x0000
                else {
                    val base = index + 1
                    if (tone.polarity == DcsPolarity.INVERTED) {
                        base + GmrsConstants.DCS_CODES.size
                    } else {
                        base
                    }
                }
            }
        }
    }

    fun getMemoryImage(): ByteArray? = memoryImage

    fun setMemoryImage(data: ByteArray) {
        memoryImage = data
    }
}

private fun ByteArray.toHexString(): String =
    joinToString(" ") { "%02X".format(it) }
