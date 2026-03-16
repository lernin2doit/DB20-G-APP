package com.db20g.controller.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.databinding.ItemEmergencyContactBinding
import com.db20g.controller.emergency.EmergencyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmergencyContactAdapter :
    ListAdapter<EmergencyManager.EmergencyContact, EmergencyContactAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EmergencyManager.EmergencyContact>() {
            override fun areItemsTheSame(
                old: EmergencyManager.EmergencyContact,
                new: EmergencyManager.EmergencyContact
            ) = old.callsign == new.callsign

            override fun areContentsTheSame(
                old: EmergencyManager.EmergencyContact,
                new: EmergencyManager.EmergencyContact
            ) = old == new
        }
    }

    class ViewHolder(val binding: ItemEmergencyContactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmergencyContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.tvContactCallsign.text = item.callsign
        b.tvContactName.text = item.name

        val statusColor = when (item.lastStatus) {
            EmergencyManager.CheckInStatus.OK -> 0xFF4CAF50.toInt()
            EmergencyManager.CheckInStatus.NEED_INFO, EmergencyManager.CheckInStatus.NEED_SUPPLIES -> 0xFFFFAB00.toInt()
            EmergencyManager.CheckInStatus.NEED_MEDICAL, EmergencyManager.CheckInStatus.NEED_EVACUATION -> 0xFFFF6F00.toInt()
            EmergencyManager.CheckInStatus.EMERGENCY -> 0xFFFF1744.toInt()
        }
        b.tvContactStatus.setTextColor(statusColor)
        b.tvContactStatus.text = item.lastStatus.name.replace("_", " ")

        if (item.lastCheckIn > 0) {
            val fmt = SimpleDateFormat("HH:mm", Locale.US)
            b.tvContactLastCheckIn.text = "Last: ${fmt.format(Date(item.lastCheckIn))}"
        } else {
            b.tvContactLastCheckIn.text = "No check-in"
        }
    }
}
