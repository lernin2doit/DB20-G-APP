package com.db20g.controller.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.audio.AudioRecorder
import com.db20g.controller.databinding.ItemRecordingBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter(
    private val onPlay: (AudioRecorder.RecordingInfo) -> Unit,
    private val onDelete: (AudioRecorder.RecordingInfo) -> Unit
) : ListAdapter<AudioRecorder.RecordingInfo, RecordingAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemRecordingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(info: AudioRecorder.RecordingInfo) {
            binding.tvRecType.text = info.type.name
            binding.tvRecType.setTextColor(
                if (info.type == AudioRecorder.RecordingType.RX) 0xFF1B5E20.toInt()
                else 0xFFFF6F00.toInt()
            )

            binding.tvRecChannel.text = "Ch ${info.channelNumber}"

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            binding.tvRecTime.text = dateFormat.format(Date(info.startTime))

            val totalSeconds = info.durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            binding.tvRecDuration.text = "$minutes:${"%02d".format(seconds)}"

            val sizeKb = info.sizeBytes / 1024
            binding.tvRecSize.text = if (sizeKb > 1024) {
                "${"%.1f".format(sizeKb / 1024.0)} MB"
            } else {
                "$sizeKb KB"
            }

            binding.btnPlay.setOnClickListener { onPlay(info) }
            binding.btnDelete.setOnClickListener { onDelete(info) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<AudioRecorder.RecordingInfo>() {
        override fun areItemsTheSame(
            oldItem: AudioRecorder.RecordingInfo,
            newItem: AudioRecorder.RecordingInfo
        ): Boolean = oldItem.file.absolutePath == newItem.file.absolutePath

        override fun areContentsTheSame(
            oldItem: AudioRecorder.RecordingInfo,
            newItem: AudioRecorder.RecordingInfo
        ): Boolean = oldItem == newItem
    }
}
