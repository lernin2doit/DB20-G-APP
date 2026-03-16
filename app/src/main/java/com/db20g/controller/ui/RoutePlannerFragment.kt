package com.db20g.controller.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.db20g.controller.databinding.FragmentRoutePlannerBinding
import com.db20g.controller.protocol.DcsPolarity
import com.db20g.controller.protocol.PowerLevel
import com.db20g.controller.protocol.RadioChannel
import com.db20g.controller.protocol.ToneValue
import com.db20g.controller.repeater.RoutePlanner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutePlannerFragment : Fragment() {

    private var _binding: FragmentRoutePlannerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()

    private lateinit var routePlanner: RoutePlanner
    private val routeRepeaterAdapter = RouteRepeaterAdapter()
    private lateinit var savedRouteAdapter: SavedRouteAdapter

    private var currentPlan: RoutePlanner.RoutePlan? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoutePlannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        routePlanner = RoutePlanner(requireContext())

        binding.rvRouteRepeaters.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = routeRepeaterAdapter
        }

        savedRouteAdapter = SavedRouteAdapter(
            onLoad = { info -> loadSavedRoute(info) },
            onDelete = { info -> deleteSavedRoute(info) }
        )
        binding.rvSavedRoutes.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = savedRouteAdapter
        }

        binding.btnPlanRoute.setOnClickListener { startRoutePlanning() }
        binding.btnProgramRoute.setOnClickListener { programRouteIntoRadio() }
        binding.btnSaveRoute.setOnClickListener { showSaveDialog() }

        refreshSavedRoutes()
    }

    private fun startRoutePlanning() {
        val startAddr = binding.etStartAddress.text?.toString()?.trim() ?: ""
        val endAddr = binding.etEndAddress.text?.toString()?.trim() ?: ""

        if (startAddr.isEmpty() || endAddr.isEmpty()) {
            Toast.makeText(requireContext(), "Enter start and end addresses", Toast.LENGTH_SHORT).show()
            return
        }

        val corridorStr = binding.etCorridorWidth.text?.toString()?.trim() ?: "35"
        val corridorMiles = corridorStr.toIntOrNull() ?: 35

        binding.btnPlanRoute.isEnabled = false
        binding.progressPlanning.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Geocode addresses on background thread
                val startCoords = withContext(Dispatchers.IO) {
                    routePlanner.geocodeAddress(startAddr)
                }
                val endCoords = withContext(Dispatchers.IO) {
                    routePlanner.geocodeAddress(endAddr)
                }

                if (startCoords == null) {
                    showError("Could not find location for: $startAddr")
                    return@launch
                }
                if (endCoords == null) {
                    showError("Could not find location for: $endAddr")
                    return@launch
                }

                // Plan the route
                val plan = routePlanner.planRoute(
                    startLat = startCoords.first,
                    startLon = startCoords.second,
                    endLat = endCoords.first,
                    endLon = endCoords.second,
                    corridorMiles = corridorMiles,
                    startAddress = startAddr,
                    endAddress = endAddr
                )

                currentPlan = plan
                showRoutePlan(plan)
            } catch (e: Exception) {
                showError("Route planning failed: ${e.message}")
            } finally {
                binding.btnPlanRoute.isEnabled = true
                binding.progressPlanning.visibility = View.GONE
            }
        }
    }

    private fun showRoutePlan(plan: RoutePlanner.RoutePlan) {
        binding.cardRouteSummary.visibility = View.VISIBLE
        binding.tvRouteSummary.text = plan.summary

        binding.tvRepeaterListHeader.visibility = View.VISIBLE
        binding.rvRouteRepeaters.visibility = View.VISIBLE
        routeRepeaterAdapter.submitList(plan.repeaters)

        if (plan.coverageGaps.isNotEmpty()) {
            binding.cardCoverageGaps.visibility = View.VISIBLE
            binding.tvCoverageGaps.text = plan.coverageGaps.joinToString("\n") { gap ->
                "Mile ${"%.0f".format(gap.startMiles)} → ${"%.0f".format(gap.endMiles)}: " +
                        "${"%.0f".format(gap.gapMiles)} mile gap with no repeater coverage"
            }
        } else {
            binding.cardCoverageGaps.visibility = View.GONE
        }
    }

    private fun programRouteIntoRadio() {
        val plan = currentPlan ?: return

        if (plan.repeaters.isEmpty()) {
            Toast.makeText(requireContext(), "No repeaters to program", Toast.LENGTH_SHORT).show()
            return
        }

        // Find the next available empty channel slot
        val channels = viewModel.channels.value ?: emptyList()
        var nextSlot = channels.indexOfFirst { it.isEmpty }
        if (nextSlot < 0) nextSlot = channels.size
        val available = 128 - nextSlot
        val needed = plan.repeaters.size

        val message = buildString {
            appendLine("Program ${needed} repeaters from this route into the radio?")
            appendLine()
            if (available < needed) {
                appendLine("⚠️ Only $available empty slots available (need $needed).")
                appendLine("First $available repeaters will be programmed.")
            } else {
                appendLine("Channels ${nextSlot + 1} through ${nextSlot + needed} will be used.")
            }
            appendLine()
            appendLine("Each repeater will be set with correct frequency, offset, tone, and high power.")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Program Route Into Radio")
            .setMessage(message)
            .setPositiveButton("Program") { _, _ ->
                var slot = nextSlot
                val count = minOf(needed, available)
                for (i in 0 until count) {
                    if (slot >= 128) break
                    val rr = plan.repeaters[i]
                    val rpt = rr.repeater

                    val txTone = when {
                        rpt.ctcssTone > 0 -> ToneValue.CTCSS(rpt.ctcssTone)
                        rpt.dcsCode > 0 -> ToneValue.DCS(rpt.dcsCode, DcsPolarity.NORMAL)
                        else -> ToneValue.None
                    }

                    val channel = RadioChannel(
                        number = slot,
                        rxFrequency = rpt.outputFrequency,
                        txFrequency = rpt.inputFrequency,
                        power = PowerLevel.HIGH,
                        wideband = true,
                        scan = true,
                        name = rpt.callsign.take(8).ifEmpty {
                            "RPT${rpt.gmrsChannel}".take(8)
                        },
                        txTone = txTone,
                        isEmpty = false
                    )
                    viewModel.addChannel(channel)
                    slot++
                }
                Toast.makeText(
                    requireContext(),
                    "Programmed $count repeaters (slots ${nextSlot + 1}-${nextSlot + count})",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSaveDialog() {
        val plan = currentPlan ?: return
        val editText = EditText(requireContext()).apply {
            hint = "Route name"
            setText("${plan.startAddress} to ${plan.endAddress}".take(40))
            setPadding(48, 24, 48, 24)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save Route Plan")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (routePlanner.savePlan(plan, name)) {
                        Toast.makeText(requireContext(), "Route saved", Toast.LENGTH_SHORT).show()
                        refreshSavedRoutes()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save route", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadSavedRoute(info: RoutePlanner.SavedPlanInfo) {
        val plan = routePlanner.loadPlan(info.fileName)
        if (plan != null) {
            currentPlan = plan
            binding.etStartAddress.setText(plan.startAddress)
            binding.etEndAddress.setText(plan.endAddress)
            showRoutePlan(plan)
            Toast.makeText(requireContext(), "Loaded: ${info.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Failed to load route", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSavedRoute(info: RoutePlanner.SavedPlanInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Route")
            .setMessage("Delete \"${info.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                routePlanner.deletePlan(info.fileName)
                refreshSavedRoutes()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshSavedRoutes() {
        val saved = routePlanner.listSavedPlans()
        savedRouteAdapter.submitList(saved)
        binding.tvNoSavedRoutes.visibility = if (saved.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
