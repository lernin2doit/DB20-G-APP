package com.db20g.controller.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.R
import com.db20g.controller.repeater.GmrsRepeater
import com.db20g.controller.repeater.RepeaterStatus
import com.google.android.material.button.MaterialButton

class RepeaterResultAdapter(
    private val onProgramClick: (GmrsRepeater) -> Unit
) : ListAdapter<RepeaterResultAdapter.RepeaterItem, RepeaterResultAdapter.ViewHolder>(DIFF) {

    data class RepeaterItem(
        val repeater: GmrsRepeater,
        val distanceMiles: Double? = null
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_repeater_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCallsign: TextView = itemView.findViewById(R.id.tvCallsign)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvFrequency: TextView = itemView.findViewById(R.id.tvFrequency)
        private val tvChannel: TextView = itemView.findViewById(R.id.tvChannel)
        private val tvTone: TextView = itemView.findViewById(R.id.tvTone)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val btnProgram: MaterialButton = itemView.findViewById(R.id.btnProgramRepeater)

        fun bind(item: RepeaterItem) {
            val r = item.repeater

            tvCallsign.text = r.callsign.ifEmpty { "Unknown" }

            if (item.distanceMiles != null) {
                tvDistance.visibility = View.VISIBLE
                tvDistance.text = if (item.distanceMiles < 1.0) {
                    "%.1f mi".format(item.distanceMiles)
                } else {
                    "%.0f mi".format(item.distanceMiles)
                }
            } else {
                tvDistance.visibility = View.GONE
            }

            tvLocation.text = buildString {
                if (r.city.isNotEmpty()) append(r.city)
                if (r.county.isNotEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(r.county)
                }
                if (r.state.isNotEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(r.state)
                }
            }

            tvFrequency.text = r.frequencyDisplay

            if (r.gmrsChannel.isNotEmpty()) {
                tvChannel.visibility = View.VISIBLE
                tvChannel.text = "Ch ${r.gmrsChannel}"
            } else {
                tvChannel.visibility = View.GONE
            }

            tvTone.text = "Tone: ${r.toneDisplay}"

            when (r.status) {
                RepeaterStatus.ON_AIR -> {
                    tvStatus.text = "● On Air"
                    tvStatus.setTextColor(0xFF4CAF50.toInt())
                }
                RepeaterStatus.OFF_AIR -> {
                    tvStatus.text = "● Off Air"
                    tvStatus.setTextColor(0xFFF44336.toInt())
                }
                RepeaterStatus.TESTING -> {
                    tvStatus.text = "● Testing"
                    tvStatus.setTextColor(0xFFFF9800.toInt())
                }
                RepeaterStatus.UNKNOWN -> {
                    tvStatus.text = "● Unknown"
                    tvStatus.setTextColor(0xFF9E9E9E.toInt())
                }
            }

            if (r.description.isNotEmpty()) {
                tvDescription.visibility = View.VISIBLE
                tvDescription.text = r.description
            } else {
                tvDescription.visibility = View.GONE
            }

            btnProgram.setOnClickListener { onProgramClick(r) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RepeaterItem>() {
            override fun areItemsTheSame(a: RepeaterItem, b: RepeaterItem) =
                a.repeater.id == b.repeater.id

            override fun areContentsTheSame(a: RepeaterItem, b: RepeaterItem) = a == b
        }
    }
}
