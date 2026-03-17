package com.db20g.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.R
import com.db20g.controller.databinding.ActivityTextMessagingBinding
import com.db20g.controller.protocol.TextMessageProtocol
import com.db20g.controller.repeater.CallsignManager
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Activity for AFSK text messaging over radio.
 * Provides conversation thread view, quick messages, GPS sharing, and message history.
 */
class TextMessagingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextMessagingBinding
    private lateinit var protocol: TextMessageProtocol
    private lateinit var callsignManager: CallsignManager
    private lateinit var messageAdapter: MessageListAdapter
    private var isListening = false
    private var lastLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)
        binding = ActivityTextMessagingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        protocol = TextMessageProtocol(this)
        callsignManager = CallsignManager(this)

        val callsign = callsignManager.callsign
        protocol.setCallsign(callsign)
        binding.tvCallsign.text = "Callsign: ${callsign.ifEmpty { "NOT SET" }}"

        setupToolbar()
        setupMessageList()
        setupQuickMessages()
        setupCompose()
        setupListenToggle()
        setupGpsSharing()
        setupProtocolListener()
        requestLocation()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupMessageList() {
        messageAdapter = MessageListAdapter(callsignManager.callsign)
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerMessages.adapter = messageAdapter

        // Load existing history
        refreshMessages()
    }

    private fun setupQuickMessages() {
        for (qm in protocol.quickMessages) {
            val chip = Chip(this).apply {
                text = qm.label
                isClickable = true
                isCheckable = false
                setOnClickListener {
                    val to = binding.etToCallsign.text?.toString()?.trim() ?: ""
                    protocol.sendQuickMessage(to, qm.index)
                    Snackbar.make(binding.root, "Sending: ${qm.label}", Snackbar.LENGTH_SHORT).show()
                    refreshMessages()
                }
            }
            binding.quickMessageBar.addView(chip)
        }
    }

    private fun setupCompose() {
        binding.btnSend.setOnClickListener {
            val to = binding.etToCallsign.text?.toString()?.trim() ?: ""
            val message = binding.etMessage.text?.toString()?.trim() ?: ""
            if (message.isEmpty()) {
                Snackbar.make(binding.root, "Enter a message", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            protocol.sendTextMessage(to, message, needsAck = to.isNotEmpty())
            binding.etMessage.text?.clear()
            refreshMessages()
            Snackbar.make(binding.root, "Sending message...", Snackbar.LENGTH_SHORT).show()
        }

        // Retry slider
        binding.sliderRetries.addOnChangeListener { _, value, _ ->
            val retries = value.toInt()
            binding.tvRetryCount.text = retries.toString()
            protocol.setMaxRetries(retries)
        }
    }

    private fun setupListenToggle() {
        binding.btnListen.setOnClickListener {
            isListening = !isListening
            if (isListening) {
                protocol.startListening()
                binding.btnListen.text = "STOP"
                binding.tvListenStatus.text = "● Listening"
                binding.tvListenStatus.setTextColor(0xFF4CAF50.toInt())
            } else {
                protocol.stopListening()
                binding.btnListen.text = "LISTEN"
                binding.tvListenStatus.text = "● Idle"
                binding.tvListenStatus.setTextColor(0xFF888888.toInt())
            }
        }
    }

    private fun setupGpsSharing() {
        binding.btnShareGps.setOnClickListener {
            val location = lastLocation
            if (location != null) {
                val to = binding.etToCallsign.text?.toString()?.trim() ?: ""
                protocol.sendPosition(location, to)
                refreshMessages()
                Snackbar.make(binding.root, "Sending GPS position...", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "No GPS fix available", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupProtocolListener() {
        protocol.setListener(object : TextMessageProtocol.MessageListener {
            override fun onMessageReceived(message: TextMessageProtocol.TextMessage) {
                runOnUiThread {
                    refreshMessages()
                    Snackbar.make(binding.root, "Message from ${message.fromCallsign}: ${message.text}",
                        Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onAckReceived(messageId: Int) {
                runOnUiThread {
                    refreshMessages()
                }
            }

            override fun onAckTimeout(messageId: Int, retriesLeft: Int) {
                runOnUiThread {
                    refreshMessages()
                    if (retriesLeft == 0) {
                        Snackbar.make(binding.root, "Message delivery failed", Snackbar.LENGTH_LONG).show()
                    }
                }
            }

            override fun onPositionReceived(callsign: String, latitude: Double, longitude: Double) {
                runOnUiThread {
                    refreshMessages()
                    Snackbar.make(binding.root, "Position from $callsign: $latitude, $longitude",
                        Snackbar.LENGTH_LONG).show()
                }
            }

            override fun onSendProgress(messageId: Int, status: String) {
                runOnUiThread {
                    binding.tvListenStatus.text = status
                }
            }
        })
    }

    private fun requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            fusedClient.lastLocation.addOnSuccessListener { location ->
                lastLocation = location
            }
        }
    }

    private fun refreshMessages() {
        val messages = protocol.getMessageHistory()
        messageAdapter.submitList(messages.toList())
        if (messages.isNotEmpty()) {
            binding.recyclerMessages.scrollToPosition(messages.size - 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        protocol.stopListening()
        protocol.release()
    }

    // ======================== Message List Adapter ========================

    class MessageListAdapter(
        private val localCallsign: String
    ) : RecyclerView.Adapter<MessageListAdapter.MessageViewHolder>() {

        private var messages = emptyList<TextMessageProtocol.TextMessage>()

        fun submitList(newMessages: List<TextMessageProtocol.TextMessage>) {
            messages = newMessages
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val layout = if (viewType == 0) R.layout.item_message_outgoing else R.layout.item_message_incoming
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return MessageViewHolder(view)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            holder.bind(messages[position])
        }

        override fun getItemCount() = messages.size

        override fun getItemViewType(position: Int): Int {
            return if (messages[position].direction == TextMessageProtocol.MessageDirection.OUTGOING) 0 else 1
        }

        class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvCallsign: TextView = view.findViewById(R.id.tvMsgCallsign)
            private val tvMessage: TextView = view.findViewById(R.id.tvMsgText)
            private val tvTime: TextView = view.findViewById(R.id.tvMsgTime)
            private val tvStatus: TextView = view.findViewById(R.id.tvMsgStatus)

            fun bind(msg: TextMessageProtocol.TextMessage) {
                tvCallsign.text = if (msg.direction == TextMessageProtocol.MessageDirection.OUTGOING) {
                    "To: ${msg.toCallsign.ifEmpty { "BROADCAST" }}"
                } else {
                    "From: ${msg.fromCallsign}"
                }
                tvMessage.text = msg.text
                tvTime.text = msg.formattedTime()
                tvStatus.text = msg.status.name
                tvStatus.setTextColor(when (msg.status) {
                    TextMessageProtocol.MessageStatus.ACKNOWLEDGED -> 0xFF4CAF50.toInt()
                    TextMessageProtocol.MessageStatus.FAILED -> 0xFFF44336.toInt()
                    TextMessageProtocol.MessageStatus.SENDING -> 0xFFFF9800.toInt()
                    else -> 0xFF888888.toInt()
                })
            }
        }
    }
}
