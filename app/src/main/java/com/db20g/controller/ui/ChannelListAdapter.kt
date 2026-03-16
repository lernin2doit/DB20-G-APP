package com.db20g.controller.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.databinding.ItemChannelBinding
import com.db20g.controller.protocol.RadioChannel
import com.db20g.controller.protocol.ToneValue
import com.db20g.controller.protocol.PowerLevel

class ChannelListAdapter(
    private val onEditClick: (RadioChannel) -> Unit,
    private val onFavoriteClick: (RadioChannel) -> Unit = {},
    private val onSelectionChanged: (Set<Int>) -> Unit = {},
    private val onLongClick: (RadioChannel) -> Unit = {}
) : ListAdapter<RadioChannel, ChannelListAdapter.ChannelViewHolder>(ChannelDiffCallback()) {

    var multiSelectMode = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) selectedChannels.clear()
                notifyDataSetChanged()
            }
        }

    val selectedChannels = mutableSetOf<Int>()
    var favorites = setOf<Int>()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun toggleSelection(channelNumber: Int) {
        if (channelNumber in selectedChannels) {
            selectedChannels.remove(channelNumber)
        } else {
            selectedChannels.add(channelNumber)
        }
        onSelectionChanged(selectedChannels.toSet())
        notifyDataSetChanged()
    }

    fun selectAll() {
        currentList.forEach { selectedChannels.add(it.number) }
        onSelectionChanged(selectedChannels.toSet())
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedChannels.clear()
        multiSelectMode = false
        onSelectionChanged(emptySet())
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val binding = ItemChannelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChannelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChannelViewHolder(
        private val binding: ItemChannelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(channel: RadioChannel) {
            binding.tvChannelNumber.text = String.format("%02d", channel.number + 1)
            binding.tvChannelName.text = channel.name.ifBlank {
                if (channel.isEmpty) "—Empty—" else "CH ${channel.number + 1}"
            }

            // Selection checkbox
            binding.cbSelect.visibility = if (multiSelectMode) View.VISIBLE else View.GONE
            binding.cbSelect.isChecked = channel.number in selectedChannels
            binding.cbSelect.setOnClickListener { toggleSelection(channel.number) }

            // Favorite star
            val isFav = channel.number in favorites
            binding.btnFavorite.setImageResource(
                if (isFav) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            binding.btnFavorite.setOnClickListener { onFavoriteClick(channel) }

            if (channel.isEmpty) {
                binding.tvRxFreq.text = "—"
                binding.tvDuplex.visibility = View.GONE
                binding.tvTone.visibility = View.GONE
                binding.tvPower.visibility = View.GONE
                binding.tvBandwidth.text = ""
                binding.tvScan.text = ""
            } else {
                binding.tvRxFreq.text = channel.formattedRxFreq
                binding.tvPower.visibility = View.VISIBLE
                binding.tvPower.text = when (channel.power) {
                    PowerLevel.HIGH -> "HIGH"
                    PowerLevel.MEDIUM -> "MID"
                    PowerLevel.LOW -> "LOW"
                }

                // Duplex/offset
                val duplex = channel.duplexMode
                if (duplex == com.db20g.controller.protocol.DuplexMode.SIMPLEX) {
                    binding.tvDuplex.visibility = View.GONE
                } else {
                    binding.tvDuplex.visibility = View.VISIBLE
                    val offset = channel.txFrequency - channel.rxFrequency
                    binding.tvDuplex.text = String.format("%+.3f", offset)
                }

                // Tone display
                val toneText = buildToneDisplay(channel)
                if (toneText.isNotEmpty()) {
                    binding.tvTone.visibility = View.VISIBLE
                    binding.tvTone.text = toneText
                } else {
                    binding.tvTone.visibility = View.GONE
                }

                binding.tvBandwidth.text = if (!channel.wideband) "Narrow" else "Wide"
                binding.tvScan.text = if (channel.scan) "Scan" else ""
            }

            binding.btnEditChannel.setOnClickListener {
                onEditClick(channel)
            }

            binding.root.setOnClickListener {
                if (multiSelectMode) {
                    toggleSelection(channel.number)
                } else {
                    onEditClick(channel)
                }
            }

            binding.root.setOnLongClickListener {
                onLongClick(channel)
                true
            }
        }

        private fun buildToneDisplay(channel: RadioChannel): String {
            val parts = mutableListOf<String>()
            when (val tx = channel.txTone) {
                is ToneValue.CTCSS -> parts.add("T:${tx.frequency}")
                is ToneValue.DCS -> parts.add("D:${tx.code}${if (tx.polarity == com.db20g.controller.protocol.DcsPolarity.INVERTED) "I" else "N"}")
                ToneValue.None -> {}
            }
            when (val rx = channel.rxTone) {
                is ToneValue.CTCSS -> parts.add("R:${rx.frequency}")
                is ToneValue.DCS -> parts.add("R:D${rx.code}${if (rx.polarity == com.db20g.controller.protocol.DcsPolarity.INVERTED) "I" else "N"}")
                ToneValue.None -> {}
            }
            return parts.joinToString(" ")
        }
    }
}

class ChannelDiffCallback : DiffUtil.ItemCallback<RadioChannel>() {
    override fun areItemsTheSame(oldItem: RadioChannel, newItem: RadioChannel): Boolean {
        return oldItem.number == newItem.number
    }

    override fun areContentsTheSame(oldItem: RadioChannel, newItem: RadioChannel): Boolean {
        return oldItem == newItem
    }
}
