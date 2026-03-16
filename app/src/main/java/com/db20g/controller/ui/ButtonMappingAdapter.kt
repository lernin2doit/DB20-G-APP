package com.db20g.controller.ui

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.bluetooth.BluetoothPttManager
import com.db20g.controller.databinding.ItemButtonMappingBinding

class ButtonMappingAdapter(
    private val onMappingClick: (keyCode: Int, currentAction: BluetoothPttManager.ButtonAction) -> Unit
) : ListAdapter<ButtonMappingAdapter.MappingItem, ButtonMappingAdapter.ViewHolder>(DIFF) {

    data class MappingItem(
        val keyCode: Int,
        val keyName: String,
        val action: BluetoothPttManager.ButtonAction
    )

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MappingItem>() {
            override fun areItemsTheSame(old: MappingItem, new: MappingItem) = old.keyCode == new.keyCode
            override fun areContentsTheSame(old: MappingItem, new: MappingItem) = old == new
        }
    }

    class ViewHolder(val binding: ItemButtonMappingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemButtonMappingBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.tvKeyName.text = item.keyName.removePrefix("KEYCODE_")
        b.tvMappedAction.text = item.action.name.replace("_", " ")

        val color = when (item.action) {
            BluetoothPttManager.ButtonAction.PTT -> 0xFFFF1744.toInt()
            BluetoothPttManager.ButtonAction.CHANNEL_UP, BluetoothPttManager.ButtonAction.CHANNEL_DOWN -> 0xFFFF6F00.toInt()
            BluetoothPttManager.ButtonAction.EMERGENCY -> 0xFFB71C1C.toInt()
            BluetoothPttManager.ButtonAction.NONE -> 0xFF9E9E9E.toInt()
        }
        b.tvMappedAction.setTextColor(color)

        holder.itemView.setOnClickListener {
            onMappingClick(item.keyCode, item.action)
        }
    }
}
