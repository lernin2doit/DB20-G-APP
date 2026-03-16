package com.db20g.controller.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.db20g.controller.R
import com.db20g.controller.databinding.FragmentEmergencyBinding
import com.db20g.controller.emergency.EmergencyManager
import com.db20g.controller.protocol.PowerLevel
import com.db20g.controller.protocol.RadioChannel
import com.db20g.controller.protocol.ToneValue
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EmergencyFragment : Fragment() {

    private var _binding: FragmentEmergencyBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()

    private lateinit var emergencyManager: EmergencyManager
    private val contactAdapter = EmergencyContactAdapter()

    private var lastLocation: Location? = null
    private var locationManager: LocationManager? = null

    private val requestLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startLocationUpdates()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emergencyManager = EmergencyManager(requireContext())
        emergencyManager.initialize()

        binding.rvContacts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = contactAdapter
        }

        setupGps()
        setupSosBeacon()
        setupQuickTune()
        setupNetCheckIn()
        setupDeadManSwitch()
        setupContacts()
    }

    private fun setupGps() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        } else {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        binding.btnSpeakGps.setOnClickListener {
            val loc = lastLocation
            if (loc != null) {
                emergencyManager.speakGpsCoordinates(loc)
            } else {
                Toast.makeText(requireContext(), "GPS position not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        locationManager = requireContext().getSystemService(LocationManager::class.java)
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000L,
            10f,
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lastLocation = location
                    if (_binding != null) {
                        binding.tvGpsPosition.text = emergencyManager.formatGpsForDisplay(location)
                    }
                }
            }
        )
        // Try to get last known location immediately
        val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (lastKnown != null) {
            lastLocation = lastKnown
            binding.tvGpsPosition.text = emergencyManager.formatGpsForDisplay(lastKnown)
        }
    }

    private fun setupSosBeacon() {
        binding.btnSosToggle.setOnClickListener {
            if (emergencyManager.isSosActive) {
                emergencyManager.stopSosBeacon()
                binding.btnSosToggle.text = "START SOS BEACON"
                binding.tvSosStatus.text = "Beacon stopped"
            } else {
                val cs = viewModel.callsignManager.callsign
                val callsign = cs.ifEmpty { "UNKNOWN" }

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Activate SOS Beacon?")
                    .setMessage(
                        "This will continuously transmit a distress call with your GPS position.\n\n" +
                        "Callsign: $callsign\n" +
                        "Channel: Ensure radio is tuned to emergency channel before activating.\n\n" +
                        "Only use in genuine emergencies."
                    )
                    .setPositiveButton("ACTIVATE SOS") { _, _ ->
                        emergencyManager.startSosBeacon(
                            location = lastLocation,
                            callsign = callsign
                        ) { message ->
                            activity?.runOnUiThread {
                                if (_binding != null) {
                                    binding.tvSosStatus.text = "🔴 TRANSMITTING: ${message.take(60)}..."
                                }
                            }
                        }
                        binding.btnSosToggle.text = "STOP SOS BEACON"
                        binding.tvSosStatus.text = "🔴 SOS ACTIVE"
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun setupQuickTune() {
        // Quick tune buttons write an emergency channel to slot 0 for immediate use
        binding.btnTuneCh20.setOnClickListener {
            quickTuneToChannel("EMRG 20", 462.675, 462.675, ToneValue.None)
        }
        binding.btnTuneCh16.setOnClickListener {
            quickTuneToChannel("CALL 16", 462.5625, 462.5625, ToneValue.None)
        }
        binding.btnTuneCh19.setOnClickListener {
            quickTuneToChannel("ROAD 19", 462.650, 462.650, ToneValue.None)
        }
        binding.btnTuneCh20R.setOnClickListener {
            quickTuneToChannel("EM20R", 462.675, 467.675, ToneValue.CTCSS(141.3))
        }
    }

    private fun quickTuneToChannel(name: String, rxFreq: Double, txFreq: Double, tone: ToneValue) {
        // Write to channel slot 0 for immediate use
        val channel = RadioChannel(
            number = 0,
            rxFrequency = rxFreq,
            txFrequency = txFreq,
            power = PowerLevel.HIGH,
            wideband = true,
            scan = false,
            name = name,
            txTone = tone,
            rxTone = tone,
            isEmpty = false
        )
        viewModel.addChannel(channel)
        Toast.makeText(
            requireContext(),
            "Quick-tuned to $name (${"%.4f".format(rxFreq)} MHz) → Channel 1",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setupNetCheckIn() {
        binding.chipOk.isChecked = true

        binding.btnSpeakCheckIn.setOnClickListener {
            val cs = viewModel.callsignManager.callsign
            val callsign = cs.ifEmpty { "UNKNOWN" }
            val persons = binding.etPersonCount.text?.toString()?.toIntOrNull() ?: 1
            val status = getSelectedStatus()

            emergencyManager.speakNetCheckIn(callsign, status, lastLocation, persons)
        }
    }

    private fun getSelectedStatus(): EmergencyManager.CheckInStatus {
        return when (binding.chipGroupStatus.checkedChipId) {
            R.id.chipOk -> EmergencyManager.CheckInStatus.OK
            R.id.chipNeedInfo -> EmergencyManager.CheckInStatus.NEED_INFO
            R.id.chipNeedSupplies -> EmergencyManager.CheckInStatus.NEED_SUPPLIES
            R.id.chipNeedMedical -> EmergencyManager.CheckInStatus.NEED_MEDICAL
            R.id.chipNeedEvac -> EmergencyManager.CheckInStatus.NEED_EVACUATION
            R.id.chipEmergency -> EmergencyManager.CheckInStatus.EMERGENCY
            else -> EmergencyManager.CheckInStatus.OK
        }
    }

    private fun setupDeadManSwitch() {
        binding.btnDmsToggle.setOnClickListener {
            if (emergencyManager.isDmsActive) {
                emergencyManager.stopDeadManSwitch()
                binding.btnDmsToggle.text = "Enable Dead Man's Switch"
                binding.tvDmsStatus.text = "Inactive"
            } else {
                val timeout = binding.etDmsTimeout.text?.toString()?.toIntOrNull() ?: 30
                val cs = viewModel.callsignManager.callsign
                val callsign = cs.ifEmpty { "UNKNOWN" }

                emergencyManager.startDeadManSwitch(timeout) {
                    // Auto-transmit distress when timer expires
                    activity?.runOnUiThread {
                        if (_binding != null) {
                            binding.tvDmsStatus.text = "⚠️ TIMEOUT — Auto-distress triggered!"
                        }
                    }
                    emergencyManager.speakMessage(
                        "Automated distress. $callsign has not responded for $timeout minutes. " +
                        "Last known position may be outdated. $callsign, please respond."
                    )
                }
                binding.btnDmsToggle.text = "Disable Dead Man's Switch"
                binding.tvDmsStatus.text = "Active — tap screen to reset timer"
            }
        }

        // Any touch on the fragment resets the DMS timer
        binding.root.setOnClickListener {
            if (emergencyManager.isDmsActive) {
                emergencyManager.resetDeadManSwitch()
                binding.tvDmsStatus.text = "Active — timer reset"
            }
        }
    }

    private fun setupContacts() {
        refreshContacts()

        binding.btnAddContact.setOnClickListener {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 24, 48, 0)
            }
            val etCallsign = EditText(requireContext()).apply {
                hint = "Callsign (e.g., WRXX123)"
            }
            val etName = EditText(requireContext()).apply {
                hint = "Name"
            }
            layout.addView(etCallsign)
            layout.addView(etName)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Emergency Contact")
                .setView(layout)
                .setPositiveButton("Add") { _, _ ->
                    val cs = etCallsign.text.toString().trim().uppercase()
                    val name = etName.text.toString().trim()
                    if (cs.isNotEmpty()) {
                        emergencyManager.addContact(cs, name)
                        refreshContacts()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun refreshContacts() {
        val contacts = emergencyManager.getContacts()
        contactAdapter.submitList(contacts)
        binding.tvNoContacts.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        emergencyManager.shutdown()
        _binding = null
    }
}
