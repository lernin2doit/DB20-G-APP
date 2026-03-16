package com.db20g.controller.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.bluetooth.BluetoothPttManager
import com.db20g.controller.databinding.ItemBtDeviceBinding

class BtDeviceAdapter :
    ListAdapter<BluetoothPttManager.BluetoothDeviceInfo, BtDeviceAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BluetoothPttManager.BluetoothDeviceInfo>() {
            override fun areItemsTheSame(
                old: BluetoothPttManager.BluetoothDeviceInfo,
                new: BluetoothPttManager.BluetoothDeviceInfo
            ) = old.address == new.address

            override fun areContentsTheSame(
                old: BluetoothPttManager.BluetoothDeviceInfo,
                new: BluetoothPttManager.BluetoothDeviceInfo
            ) = old == new
        }
    }

    class ViewHolder(val binding: ItemBtDeviceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemBtDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.tvDeviceName.text = item.name
        b.tvDeviceAddress.text = item.address

        b.tvDeviceType.text = when (item.type) {
            BluetoothPttManager.DeviceType.PTT_BUTTON -> "PTT Button (HID)"
            BluetoothPttManager.DeviceType.AUDIO_HEADSET -> "Audio Headset"
            BluetoothPttManager.DeviceType.SPEAKER -> "Speaker"
            BluetoothPttManager.DeviceType.UNKNOWN -> "Unknown"
        }

        if (item.isConnected) {
            b.tvDeviceStatus.text = "Connected"
            b.tvDeviceStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            b.tvDeviceStatus.text = "Paired"
            b.tvDeviceStatus.setTextColor(0xFF9E9E9E.toInt())
        }
    }
}
