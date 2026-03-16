package com.db20g.controller.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.databinding.ItemRouteRepeaterBinding
import com.db20g.controller.repeater.RoutePlanner

class RouteRepeaterAdapter :
    ListAdapter<RoutePlanner.RouteRepeater, RouteRepeaterAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RoutePlanner.RouteRepeater>() {
            override fun areItemsTheSame(
                old: RoutePlanner.RouteRepeater,
                new: RoutePlanner.RouteRepeater
            ) = old.repeater.callsign == new.repeater.callsign &&
                    old.repeater.outputFrequency == new.repeater.outputFrequency

            override fun areContentsTheSame(
                old: RoutePlanner.RouteRepeater,
                new: RoutePlanner.RouteRepeater
            ) = old == new
        }
    }

    class ViewHolder(val binding: ItemRouteRepeaterBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRouteRepeaterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val rpt = item.repeater
        val b = holder.binding

        b.tvMileMarker.text = "%.0f".format(item.routeDistanceMiles)
        b.tvCallsign.text = rpt.callsign.ifEmpty { "Unknown" }
        b.tvLocation.text = buildString {
            if (rpt.city.isNotEmpty()) append(rpt.city)
            if (rpt.state.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append(rpt.state)
            }
        }
        b.tvFrequency.text = "%.4f MHz".format(rpt.outputFrequency)
        b.tvTone.text = when {
            rpt.ctcssTone > 0 -> "CTCSS %.1f".format(rpt.ctcssTone)
            rpt.dcsCode > 0 -> "DCS %03d".format(rpt.dcsCode)
            else -> ""
        }
        b.tvChannel.text = if (rpt.gmrsChannel.isNotEmpty()) "Ch ${rpt.gmrsChannel}" else ""
        b.tvCorridorDist.text = "${"%.0f".format(item.corridorDistanceMiles)} mi\noff route"
    }
}
