package com.db20g.controller.protocol

import android.content.Context
import android.location.Location
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Text messaging protocol over AFSK radio audio.
 *
 * Packet format:
 *   [type:1][msgId:4][fromCall:10][toCall:10][flags:1][payloadLen:2][payload:N][crc:2]
 *
 * Types: TEXT=0x01, ACK=0x02, POSITION=0x03, QUICK_MSG=0x04
 * Flags: bit0=needsAck, bit1=isRetransmit, bit2=isGpsAttached
 *
 * Features:
 * - Automatic CRC-16 error detection
 * - Message acknowledgment with configurable retries (0-5)
 * - GPS position sharing as encoded bursts
 * - Quick message presets
 * - Thread-based conversation history with JSON persistence
 */
class TextMessageProtocol(private val context: Context) {

    companion object {
        private const val TAG = "TextMsgProtocol"

        // Packet types
        const val TYPE_TEXT: Byte = 0x01
        const val TYPE_ACK: Byte = 0x02
        const val TYPE_POSITION: Byte = 0x03
        const val TYPE_QUICK_MSG: Byte = 0x04

        // Flags
        const val FLAG_NEEDS_ACK = 0x01
        const val FLAG_RETRANSMIT = 0x02
        const val FLAG_GPS_ATTACHED = 0x04

        // Protocol constants
        const val MAX_PAYLOAD = 256
        const val CALLSIGN_LEN = 10
        const val HEADER_SIZE = 1 + 4 + 10 + 10 + 1 + 2  // 28 bytes
        const val CRC_SIZE = 2

        // Retry defaults
        const val DEFAULT_RETRIES = 3
        const val RETRY_DELAY_MS = 5000L
        const val MAX_HISTORY_SIZE = 1000
    }

    private val modem = AfskModem()
    private val messageHistory = Collections.synchronizedList(mutableListOf<TextMessage>())
    private val pendingAcks = ConcurrentHashMap<Int, PendingMessage>()
    private var listener: MessageListener? = null
    private var localCallsign = ""
    private var maxRetries = DEFAULT_RETRIES
    private val historyFile = File(context.filesDir, "text_messages.json")

    private fun addToHistory(msg: TextMessage) {
        messageHistory.add(msg)
        if (messageHistory.size > MAX_HISTORY_SIZE) {
            messageHistory.removeAt(0)
        }
    }

    // Quick message presets
    val quickMessages = listOf(
        QuickMessage(0, "En route", "On my way to the destination"),
        QuickMessage(1, "At destination", "I have arrived at the destination"),
        QuickMessage(2, "Need assistance", "I need help at my current location"),
        QuickMessage(3, "All clear", "Everything is OK, no issues"),
        QuickMessage(4, "Standing by", "I'm monitoring this channel"),
        QuickMessage(5, "Copy that", "Message received and understood"),
        QuickMessage(6, "Negative", "No / Cannot comply"),
        QuickMessage(7, "Affirmative", "Yes / Will comply"),
        QuickMessage(8, "Break break", "Emergency traffic incoming"),
        QuickMessage(9, "Going QRT", "Shutting down / Going off air")
    )

    interface MessageListener {
        fun onMessageReceived(message: TextMessage)
        fun onAckReceived(messageId: Int)
        fun onAckTimeout(messageId: Int, retriesLeft: Int)
        fun onPositionReceived(callsign: String, latitude: Double, longitude: Double)
        fun onSendProgress(messageId: Int, status: String)
    }

    fun setListener(listener: MessageListener) {
        this.listener = listener
    }

    fun setCallsign(callsign: String) {
        localCallsign = callsign.take(CALLSIGN_LEN)
    }

    fun setMaxRetries(retries: Int) {
        maxRetries = retries.coerceIn(0, 5)
    }

    init {
        loadHistory()
        modem.setDemodulateCallback { rawData ->
            processReceivedPacket(rawData)
        }
    }

    // ======================== SEND ========================

    /**
     * Send a text message to a callsign (or broadcast if empty).
     */
    fun sendTextMessage(toCallsign: String, text: String, needsAck: Boolean = true): TextMessage {
        val msgId = generateMessageId()
        val payload = text.toByteArray(StandardCharsets.UTF_8).take(MAX_PAYLOAD).toByteArray()

        var flags = 0
        if (needsAck && toCallsign.isNotEmpty()) flags = flags or FLAG_NEEDS_ACK

        val packet = buildPacket(TYPE_TEXT, msgId, localCallsign, toCallsign, flags, payload)

        val message = TextMessage(
            id = msgId,
            fromCallsign = localCallsign,
            toCallsign = toCallsign,
            text = text,
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.SENDING
        )

        addToHistory(message)
        saveHistory()

        // Transmit
        Thread {
            listener?.onSendProgress(msgId, "Transmitting...")
            modem.transmit(packet)

            if (needsAck && toCallsign.isNotEmpty()) {
                // Queue for ACK tracking
                pendingAcks[msgId] = PendingMessage(message, packet, maxRetries)
                listener?.onSendProgress(msgId, "Awaiting acknowledgment...")
                scheduleRetry(msgId)
            } else {
                updateMessageStatus(msgId, MessageStatus.SENT)
                listener?.onSendProgress(msgId, "Sent (no ACK requested)")
            }
        }.start()

        return message
    }

    /**
     * Send a quick/preset message.
     */
    fun sendQuickMessage(toCallsign: String, quickMsgIndex: Int): TextMessage? {
        val qm = quickMessages.getOrNull(quickMsgIndex) ?: return null
        val msgId = generateMessageId()
        val payload = byteArrayOf(quickMsgIndex.toByte())

        val packet = buildPacket(TYPE_QUICK_MSG, msgId, localCallsign, toCallsign,
            FLAG_NEEDS_ACK, payload)

        val message = TextMessage(
            id = msgId,
            fromCallsign = localCallsign,
            toCallsign = toCallsign,
            text = "[Quick] ${qm.label}: ${qm.text}",
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.SENDING
        )

        addToHistory(message)
        saveHistory()

        Thread {
            modem.transmit(packet)
            pendingAcks[msgId] = PendingMessage(message, packet, maxRetries)
            scheduleRetry(msgId)
        }.start()

        return message
    }

    /**
     * Share GPS position as an encoded audio burst.
     */
    fun sendPosition(location: Location, toCallsign: String = "") {
        val msgId = generateMessageId()

        // Encode lat/lon as 4-byte fixed-point each (enough precision)
        val payload = ByteBuffer.allocate(8)
            .putFloat(location.latitude.toFloat())
            .putFloat(location.longitude.toFloat())
            .array()

        val flags = FLAG_GPS_ATTACHED or (if (toCallsign.isNotEmpty()) FLAG_NEEDS_ACK else 0)
        val packet = buildPacket(TYPE_POSITION, msgId, localCallsign, toCallsign, flags, payload)

        val message = TextMessage(
            id = msgId,
            fromCallsign = localCallsign,
            toCallsign = toCallsign,
            text = "[Position] ${location.latitude}, ${location.longitude}",
            timestamp = System.currentTimeMillis(),
            direction = MessageDirection.OUTGOING,
            status = MessageStatus.SENDING,
            latitude = location.latitude,
            longitude = location.longitude
        )

        addToHistory(message)
        saveHistory()

        Thread {
            modem.transmit(packet)
            updateMessageStatus(msgId, MessageStatus.SENT)
        }.start()
    }

    // ======================== RECEIVE ========================

    fun startListening() {
        modem.startReceiving()
    }

    fun stopListening() {
        modem.stopReceiving()
    }

    private fun processReceivedPacket(rawData: ByteArray) {
        if (rawData.size < HEADER_SIZE + CRC_SIZE) {
            Log.w(TAG, "Packet too short: ${rawData.size}")
            return
        }

        // Verify CRC
        val packetData = rawData.copyOfRange(0, rawData.size - CRC_SIZE)
        val receivedCrc = ((rawData[rawData.size - 2].toInt() and 0xFF) shl 8) or
                (rawData[rawData.size - 1].toInt() and 0xFF)
        val calculatedCrc = crc16(packetData)

        if (receivedCrc != calculatedCrc) {
            Log.w(TAG, "CRC mismatch: received=$receivedCrc, calculated=$calculatedCrc")
            return
        }

        // Parse header
        val type = rawData[0]
        val msgId = ByteBuffer.wrap(rawData, 1, 4).int
        val fromCallsign = String(rawData, 5, CALLSIGN_LEN, StandardCharsets.UTF_8).trim('\u0000')
        val toCallsign = String(rawData, 15, CALLSIGN_LEN, StandardCharsets.UTF_8).trim('\u0000')
        val flags = rawData[25].toInt() and 0xFF
        val payloadLen = ((rawData[26].toInt() and 0xFF) shl 8) or (rawData[27].toInt() and 0xFF)
        if (HEADER_SIZE + payloadLen > rawData.size - CRC_SIZE) {
            Log.w(TAG, "Payload length $payloadLen exceeds packet bounds")
            return
        }
        val payload = rawData.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadLen)

        // Filter: only process if addressed to us or broadcast
        if (toCallsign.isNotEmpty() && !toCallsign.equals(localCallsign, ignoreCase = true)) {
            return
        }

        when (type) {
            TYPE_TEXT -> {
                val text = String(payload, StandardCharsets.UTF_8)
                val message = TextMessage(
                    id = msgId,
                    fromCallsign = fromCallsign,
                    toCallsign = toCallsign,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    direction = MessageDirection.INCOMING,
                    status = MessageStatus.RECEIVED
                )
                addToHistory(message)
                saveHistory()
                listener?.onMessageReceived(message)

                // Send ACK if requested
                if (flags and FLAG_NEEDS_ACK != 0) {
                    sendAck(msgId, fromCallsign)
                }
            }

            TYPE_ACK -> {
                val ackedMsgId = ByteBuffer.wrap(payload, 0, 4).int
                Log.d(TAG, "ACK received for message $ackedMsgId")
                pendingAcks.remove(ackedMsgId)
                updateMessageStatus(ackedMsgId, MessageStatus.ACKNOWLEDGED)
                listener?.onAckReceived(ackedMsgId)
            }

            TYPE_POSITION -> {
                if (payload.size >= 8) {
                    val lat = ByteBuffer.wrap(payload, 0, 4).float.toDouble()
                    val lon = ByteBuffer.wrap(payload, 4, 4).float.toDouble()

                    val message = TextMessage(
                        id = msgId,
                        fromCallsign = fromCallsign,
                        toCallsign = toCallsign,
                        text = "[Position] $fromCallsign at $lat, $lon",
                        timestamp = System.currentTimeMillis(),
                        direction = MessageDirection.INCOMING,
                        status = MessageStatus.RECEIVED,
                        latitude = lat,
                        longitude = lon
                    )
                    addToHistory(message)
                    saveHistory()
                    listener?.onPositionReceived(fromCallsign, lat, lon)
                    listener?.onMessageReceived(message)

                    if (flags and FLAG_NEEDS_ACK != 0) {
                        sendAck(msgId, fromCallsign)
                    }
                }
            }

            TYPE_QUICK_MSG -> {
                val qmIndex = payload[0].toInt() and 0xFF
                val qm = quickMessages.getOrNull(qmIndex)
                val text = qm?.let { "[Quick] ${it.label}: ${it.text}" } ?: "[Quick] Unknown #$qmIndex"

                val message = TextMessage(
                    id = msgId,
                    fromCallsign = fromCallsign,
                    toCallsign = toCallsign,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    direction = MessageDirection.INCOMING,
                    status = MessageStatus.RECEIVED
                )
                addToHistory(message)
                saveHistory()
                listener?.onMessageReceived(message)

                if (flags and FLAG_NEEDS_ACK != 0) {
                    sendAck(msgId, fromCallsign)
                }
            }
        }
    }

    private fun sendAck(originalMsgId: Int, toCallsign: String) {
        val payload = ByteBuffer.allocate(4).putInt(originalMsgId).array()
        val packet = buildPacket(TYPE_ACK, generateMessageId(), localCallsign, toCallsign, 0, payload)
        Thread {
            modem.transmit(packet)
            Log.d(TAG, "ACK sent for message $originalMsgId")
        }.start()
    }

    // ======================== PACKET BUILDING ========================

    private fun buildPacket(type: Byte, msgId: Int, from: String, to: String,
                            flags: Int, payload: ByteArray): ByteArray {
        val payloadLen = payload.size.coerceAtMost(MAX_PAYLOAD)
        val packet = ByteArray(HEADER_SIZE + payloadLen + CRC_SIZE)

        // Header
        packet[0] = type
        ByteBuffer.wrap(packet, 1, 4).putInt(msgId)

        // Callsigns (padded to CALLSIGN_LEN with nulls)
        val fromBytes = from.toByteArray(StandardCharsets.UTF_8)
        val toBytes = to.toByteArray(StandardCharsets.UTF_8)
        System.arraycopy(fromBytes, 0, packet, 5, minOf(fromBytes.size, CALLSIGN_LEN))
        System.arraycopy(toBytes, 0, packet, 15, minOf(toBytes.size, CALLSIGN_LEN))

        packet[25] = flags.toByte()
        packet[26] = ((payloadLen shr 8) and 0xFF).toByte()
        packet[27] = (payloadLen and 0xFF).toByte()

        // Payload
        System.arraycopy(payload, 0, packet, HEADER_SIZE, payloadLen)

        // CRC-16 over everything except CRC bytes
        val crc = crc16(packet.copyOfRange(0, HEADER_SIZE + payloadLen))
        packet[HEADER_SIZE + payloadLen] = ((crc shr 8) and 0xFF).toByte()
        packet[HEADER_SIZE + payloadLen + 1] = (crc and 0xFF).toByte()

        return packet
    }

    /**
     * CRC-16-CCITT for error detection.
     */
    private fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            var b = byte.toInt() and 0xFF
            for (i in 0 until 8) {
                val mix = (crc xor b) and 1
                crc = crc shr 1
                if (mix != 0) crc = crc xor 0x8408
                b = b shr 1
            }
        }
        return crc xor 0xFFFF
    }

    // ======================== RETRY LOGIC ========================

    private val retryExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()

    private fun scheduleRetry(msgId: Int) {
        retryExecutor.schedule({
            val pending = pendingAcks[msgId] ?: return@schedule

            if (pending.retriesLeft > 0) {
                pending.retriesLeft--
                Log.d(TAG, "Retrying message $msgId (${pending.retriesLeft} retries left)")
                listener?.onAckTimeout(msgId, pending.retriesLeft)
                listener?.onSendProgress(msgId, "Retransmitting (${pending.retriesLeft} left)...")

                // Set retransmit flag
                pending.packet[25] = (pending.packet[25].toInt() or FLAG_RETRANSMIT).toByte()
                modem.transmit(pending.packet)

                scheduleRetry(msgId) // Schedule next retry
            } else {
                // All retries exhausted
                pendingAcks.remove(msgId)
                updateMessageStatus(msgId, MessageStatus.FAILED)
                listener?.onAckTimeout(msgId, 0)
                listener?.onSendProgress(msgId, "Failed — no acknowledgment received")
                Log.w(TAG, "Message $msgId failed after all retries")
            }
        }, RETRY_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private var messageIdCounter = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

    @Synchronized
    private fun generateMessageId(): Int {
        return messageIdCounter++
    }

    // ======================== HISTORY ========================

    fun getMessageHistory(): List<TextMessage> = messageHistory.toList()

    fun getConversation(callsign: String): List<TextMessage> {
        return messageHistory.filter {
            it.fromCallsign.equals(callsign, ignoreCase = true) ||
            it.toCallsign.equals(callsign, ignoreCase = true)
        }.sortedBy { it.timestamp }
    }

    fun getConversationThreads(): List<ConversationThread> {
        val threads = mutableMapOf<String, MutableList<TextMessage>>()
        for (msg in messageHistory) {
            val partner = if (msg.direction == MessageDirection.OUTGOING) msg.toCallsign else msg.fromCallsign
            val key = partner.uppercase(Locale.US).ifEmpty { "BROADCAST" }
            threads.getOrPut(key) { mutableListOf() }.add(msg)
        }
        return threads.map { (callsign, messages) ->
            ConversationThread(
                callsign = callsign,
                messages = messages.sortedBy { it.timestamp },
                lastMessage = messages.maxByOrNull { it.timestamp },
                unreadCount = messages.count {
                    it.direction == MessageDirection.INCOMING && it.status == MessageStatus.RECEIVED
                }
            )
        }.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
    }

    fun clearHistory() {
        messageHistory.clear()
        saveHistory()
    }

    private fun updateMessageStatus(msgId: Int, status: MessageStatus) {
        messageHistory.find { it.id == msgId }?.let {
            it.status = status
            saveHistory()
        }
    }

    // ======================== PERSISTENCE ========================

    private fun saveHistory() {
        try {
            val arr = JSONArray()
            for (msg in messageHistory.takeLast(1000)) { // Keep last 1000 messages
                arr.put(JSONObject().apply {
                    put("id", msg.id)
                    put("fromCallsign", msg.fromCallsign)
                    put("toCallsign", msg.toCallsign)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    put("direction", msg.direction.name)
                    put("status", msg.status.name)
                    put("latitude", msg.latitude)
                    put("longitude", msg.longitude)
                })
            }
            historyFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message history", e)
        }
    }

    private fun loadHistory() {
        try {
            if (!historyFile.exists()) return
            val arr = JSONArray(historyFile.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                messageHistory.add(TextMessage(
                    id = obj.getInt("id"),
                    fromCallsign = obj.getString("fromCallsign"),
                    toCallsign = obj.getString("toCallsign"),
                    text = obj.getString("text"),
                    timestamp = obj.getLong("timestamp"),
                    direction = MessageDirection.valueOf(obj.getString("direction")),
                    status = MessageStatus.valueOf(obj.getString("status")),
                    latitude = obj.optDouble("latitude", 0.0),
                    longitude = obj.optDouble("longitude", 0.0)
                ))
            }
            Log.d(TAG, "Loaded ${messageHistory.size} messages from history")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load message history", e)
        }
    }

    fun release() {
        modem.release()
        pendingAcks.clear()
        retryExecutor.shutdownNow()
    }

    // ======================== DATA CLASSES ========================

    data class TextMessage(
        val id: Int,
        val fromCallsign: String,
        val toCallsign: String,
        val text: String,
        val timestamp: Long,
        val direction: MessageDirection,
        var status: MessageStatus,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    ) {
        fun formattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
            return sdf.format(Date(timestamp))
        }
    }

    enum class MessageDirection { INCOMING, OUTGOING }

    enum class MessageStatus { SENDING, SENT, ACKNOWLEDGED, RECEIVED, FAILED }

    data class QuickMessage(val index: Int, val label: String, val text: String)

    data class ConversationThread(
        val callsign: String,
        val messages: List<TextMessage>,
        val lastMessage: TextMessage?,
        val unreadCount: Int
    )

    class PendingMessage(
        val message: TextMessage,
        val packet: ByteArray,
        var retriesLeft: Int
    )
}
