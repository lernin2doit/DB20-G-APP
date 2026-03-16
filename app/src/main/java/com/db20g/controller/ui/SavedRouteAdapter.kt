package com.db20g.controller.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.databinding.ItemSavedRouteBinding
import com.db20g.controller.repeater.RoutePlanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedRouteAdapter(
    private val onLoad: (RoutePlanner.SavedPlanInfo) -> Unit,
    private val onDelete: (RoutePlanner.SavedPlanInfo) -> Unit
) : ListAdapter<RoutePlanner.SavedPlanInfo, SavedRouteAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RoutePlanner.SavedPlanInfo>() {
            override fun areItemsTheSame(
                old: RoutePlanner.SavedPlanInfo,
                new: RoutePlanner.SavedPlanInfo
            ) = old.fileName == new.fileName

            override fun areContentsTheSame(
                old: RoutePlanner.SavedPlanInfo,
                new: RoutePlanner.SavedPlanInfo
            ) = old == new
        }
    }

    class ViewHolder(val binding: ItemSavedRouteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavedRouteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(item.timestamp))

        b.tvRouteName.text = item.name
        b.tvRouteDetails.text = "${item.startAddress} → ${item.endAddress}\n${item.repeaterCount} repeaters · $dateStr"

        b.btnLoadRoute.setOnClickListener { onLoad(item) }
        b.btnDeleteRoute.setOnClickListener { onDelete(item) }
    }
}
