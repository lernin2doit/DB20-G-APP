package com.db20g.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.db20g.controller.databinding.FragmentRepeatersBinding
import com.db20g.controller.repeater.GmrsRepeater
import com.db20g.controller.repeater.RepeaterBookApi
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class RepeaterSearchFragment : Fragment() {

    private var _binding: FragmentRepeatersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()

    private lateinit var api: RepeaterBookApi
    private lateinit var adapter: RepeaterResultAdapter

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepeatersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        api = RepeaterBookApi(requireContext())

        adapter = RepeaterResultAdapter { repeater ->
            showProgramDialog(repeater)
        }
        binding.rvRepeaters.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRepeaters.adapter = adapter

        setupSearchTabs()
        setupSearchButtons()
        showEmptyState(true)

        // Auto-search nearby on launch if we have location permission
        if (hasLocationPermission()) {
            fetchLocationAndSearchNearby()
        }
    }

    private fun setupSearchTabs() {
        binding.tabSearchMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.layoutSearchNearby.visibility =
                    if (tab.position == 0) View.VISIBLE else View.GONE
                binding.layoutSearchState.visibility =
                    if (tab.position == 1) View.VISIBLE else View.GONE
                binding.layoutSearchFreq.visibility =
                    if (tab.position == 2) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupSearchButtons() {
        binding.btnSearchNearby.setOnClickListener {
            val radius = binding.etRadius.text.toString().toIntOrNull() ?: 50
            if (hasLocationPermission()) {
                fetchLocationAndSearchNearby(radius)
            } else {
                binding.tvResultCount.text = "Location permission required for nearby search"
            }
        }

        binding.btnSearchState.setOnClickListener {
            val state = binding.etState.text.toString().trim().uppercase()
            if (state.length == 2) {
                searchByState(state)
            } else {
                binding.tvResultCount.text = "Enter a 2-letter state code"
            }
        }

        binding.btnSearchFreq.setOnClickListener {
            val freq = binding.etFrequency.text.toString().toDoubleOrNull()
            if (freq != null) {
                searchByFrequency(freq)
            } else {
                binding.tvResultCount.text = "Enter a valid frequency"
            }
        }
    }

    private fun fetchLocationAndSearchNearby(radius: Int = 50) {
        if (!hasLocationPermission()) return

        setLoading(true)
        val fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    lastLatitude = location.latitude
                    lastLongitude = location.longitude
                    searchNearby(location.latitude, location.longitude, radius)
                } else {
                    setLoading(false)
                    binding.tvResultCount.text = "Could not get location. Try again or use State search."
                }
            }.addOnFailureListener {
                setLoading(false)
                binding.tvResultCount.text = "Location error: ${it.message}"
            }
        } catch (e: SecurityException) {
            setLoading(false)
            binding.tvResultCount.text = "Location permission not granted"
        }
    }

    private fun searchNearby(lat: Double, lon: Double, radius: Int) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = api.searchNearby(lat, lon, radius)) {
                is RepeaterBookApi.ApiResult.Success -> {
                    val items = result.data
                        .sortedBy { it.distanceMilesFrom(lat, lon) }
                        .map { RepeaterResultAdapter.RepeaterItem(it, it.distanceMilesFrom(lat, lon)) }
                    displayResults(items, result.fromCache, result.stale)
                }
                is RepeaterBookApi.ApiResult.Error -> {
                    setLoading(false)
                    showError(result.message)
                }
            }
        }
    }

    private fun searchByState(state: String) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = api.searchByState(state)) {
                is RepeaterBookApi.ApiResult.Success -> {
                    val items = result.data.map { r ->
                        val dist = if (lastLatitude != null && lastLongitude != null) {
                            r.distanceMilesFrom(lastLatitude!!, lastLongitude!!)
                        } else null
                        RepeaterResultAdapter.RepeaterItem(r, dist)
                    }.let { list ->
                        if (lastLatitude != null) list.sortedBy { it.distanceMiles ?: Double.MAX_VALUE }
                        else list.sortedBy { it.repeater.callsign }
                    }
                    displayResults(items, result.fromCache, result.stale)
                }
                is RepeaterBookApi.ApiResult.Error -> {
                    setLoading(false)
                    showError(result.message)
                }
            }
        }
    }

    private fun searchByFrequency(freq: Double) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = api.searchByFrequency(freq)) {
                is RepeaterBookApi.ApiResult.Success -> {
                    val items = result.data.map { r ->
                        val dist = if (lastLatitude != null && lastLongitude != null) {
                            r.distanceMilesFrom(lastLatitude!!, lastLongitude!!)
                        } else null
                        RepeaterResultAdapter.RepeaterItem(r, dist)
                    }.let { list ->
                        if (lastLatitude != null) list.sortedBy { it.distanceMiles ?: Double.MAX_VALUE }
                        else list.sortedBy { it.repeater.callsign }
                    }
                    displayResults(items, result.fromCache, result.stale)
                }
                is RepeaterBookApi.ApiResult.Error -> {
                    setLoading(false)
                    showError(result.message)
                }
            }
        }
    }

    private fun displayResults(
        items: List<RepeaterResultAdapter.RepeaterItem>,
        fromCache: Boolean,
        stale: Boolean
    ) {
        setLoading(false)
        if (items.isEmpty()) {
            showEmptyState(true, "No repeaters found for this search")
            binding.tvResultCount.text = "0 results"
        } else {
            showEmptyState(false)
            adapter.submitList(items)
            binding.tvResultCount.text = "${items.size} repeater${if (items.size != 1) "s" else ""}"
        }

        binding.tvCacheStatus.text = when {
            stale -> "⚠ Offline (cached)"
            fromCache -> "Cached"
            else -> "Live"
        }
    }

    private fun showProgramDialog(repeater: GmrsRepeater) {
        // Find next available empty channel slot
        val channels = viewModel.channels.value ?: return
        val nextEmpty = channels.indexOfFirst { it.isEmpty }
        val targetSlot = if (nextEmpty >= 0) nextEmpty else 0

        val message = buildString {
            appendLine("Program ${repeater.callsign} into channel ${targetSlot + 1}?")
            appendLine()
            appendLine("Output: %.4f MHz".format(repeater.outputFrequency))
            appendLine("Input: %.4f MHz".format(repeater.inputFrequency))
            appendLine("Tone: ${repeater.toneDisplay}")
            if (targetSlot == 0 && nextEmpty < 0) {
                appendLine()
                appendLine("⚠ No empty slots — will overwrite channel 1")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Program Repeater")
            .setMessage(message)
            .setPositiveButton("Program") { _, _ ->
                viewModel.connectToRepeater(repeater, targetSlot)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showError(message: String) {
        binding.tvResultCount.text = message
        showEmptyState(true, message)
    }

    private fun showEmptyState(show: Boolean, message: String? = null) {
        binding.layoutEmpty.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvRepeaters.visibility = if (show) View.GONE else View.VISIBLE
        if (message != null) {
            binding.tvEmptyMessage.text = message
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressSearch.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSearchNearby.isEnabled = !loading
        binding.btnSearchState.isEnabled = !loading
        binding.btnSearchFreq.isEnabled = !loading
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
