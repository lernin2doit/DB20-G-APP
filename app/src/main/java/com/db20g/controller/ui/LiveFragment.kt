package com.db20g.controller.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.db20g.controller.R
import com.db20g.controller.audio.DtmfGenerator
import com.db20g.controller.audio.AudioAnalyzer
import com.db20g.controller.audio.RadioAudioEngine
import com.db20g.controller.databinding.FragmentLiveBinding
import com.db20g.controller.protocol.GmrsConstants
import com.db20g.controller.protocol.RadioChannel
import com.db20g.controller.protocol.ScanManager
import com.db20g.controller.serial.MultiRadioManager
import com.db20g.controller.serial.UsbSerialManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

class LiveFragment : Fragment() {

    private var _binding: FragmentLiveBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()

    private lateinit var audioEngine: RadioAudioEngine
    private val dtmfGenerator = DtmfGenerator()

    private var currentChannelIndex = 0
    private var isPttDown = false
    private val scanManager = ScanManager()
    private val audioAnalyzer = AudioAnalyzer()

    // Location for repeater lookup
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private val idTimerHandler = Handler(Looper.getMainLooper())

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startAudioEngine()
            } else {
                Snackbar.make(binding.root, "Microphone permission required for live operation", Snackbar.LENGTH_LONG).show()
            }
        }

    private val requestLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startLocationUpdates()
            } else {
                Snackbar.make(binding.root, "Location permission needed for repeater lookup", Snackbar.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        audioEngine = RadioAudioEngine(requireContext())

        setupPttButton()
        setupChannelControls()
        setupDtmfKeypad()
        setupVoxControls()
        setupModeSelector()
        setupRepeaterControls()
        setupCallsignControls()
        setupScanControls()
        setupSignalMonitor()
        setupMultiRadio()
        setupSecondaryPanel()
        setupDtmfModal()
        setupObservers()

        // Load repeater database
        viewModel.loadRepeaterDatabase()

        // Request audio permission
        checkAudioPermission()

        // Request location for repeater lookup
        checkLocationPermission()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPttButton() {
        binding.btnPtt.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    activatePtt()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    deactivatePtt()
                    true
                }
                else -> false
            }
        }
    }

    private fun activatePtt() {
        if (viewModel.connectionState.value != ConnectionState.CONNECTED) {
            Snackbar.make(binding.root, "Not connected to radio", Snackbar.LENGTH_SHORT).show()
            return
        }
        isPttDown = true
        viewModel.pttDown()
        audioEngine.startCapture()

        // Visual feedback
        binding.btnPtt.text = "TX"
        binding.btnPtt.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
        binding.tvTxIndicator.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
        binding.tvTxIndicator.setBackgroundColor(0xFF441111.toInt())
        binding.tvPttHint.text = "Transmitting..."
    }

    private fun deactivatePtt() {
        isPttDown = false
        viewModel.pttUp()
        if (!audioEngine.voxEnabled) {
            audioEngine.stopCapture()
        }

        // Visual feedback
        binding.btnPtt.text = "PTT"
        binding.btnPtt.setBackgroundColor(0xFF2E7D32.toInt())
        binding.tvTxIndicator.setTextColor(0xFF555555.toInt())
        binding.tvTxIndicator.setBackgroundColor(0xFF222222.toInt())
        binding.tvPttHint.text = "Hold to talk"
    }

    private fun setupChannelControls() {
        binding.btnChannelUp.setOnClickListener {
            navigateChannel(1)
        }
        binding.btnChannelDown.setOnClickListener {
            navigateChannel(-1)
        }
    }

    private fun navigateChannel(direction: Int) {
        val channels = viewModel.channels.value ?: return
        val activeChannels = channels.filter { !it.isEmpty }
        if (activeChannels.isEmpty()) return

        currentChannelIndex = (currentChannelIndex + direction).let { idx ->
            when {
                idx < 0 -> activeChannels.size - 1
                idx >= activeChannels.size -> 0
                else -> idx
            }
        }

        val channel = activeChannels[currentChannelIndex]
        updateChannelDisplay(channel.number, channel.name, channel.rxFrequency)

        // Notify ViewModel for live channel switch
        viewModel.setActiveChannel(channel.number)
    }

    private fun updateChannelDisplay(number: Int, name: String, freqMhz: Double) {
        binding.tvChannelNumber.text = "CH %02d".format(number + 1)
        binding.tvChannelName.text = name.ifEmpty { "Channel ${number + 1}" }
        binding.tvFrequency.text = "%.4f MHz".format(freqMhz)
    }

    private fun setupDtmfKeypad() {
        val dtmfButtons = mapOf(
            binding.btnDtmf0 to '0', binding.btnDtmf1 to '1', binding.btnDtmf2 to '2',
            binding.btnDtmf3 to '3', binding.btnDtmf4 to '4', binding.btnDtmf5 to '5',
            binding.btnDtmf6 to '6', binding.btnDtmf7 to '7', binding.btnDtmf8 to '8',
            binding.btnDtmf9 to '9', binding.btnDtmfA to 'A', binding.btnDtmfB to 'B',
            binding.btnDtmfC to 'C', binding.btnDtmfD to 'D', binding.btnDtmfStar to '*',
            binding.btnDtmfHash to '#'
        )

        for ((button, char) in dtmfButtons) {
            button.setOnClickListener {
                binding.etDtmfDisplay.append(char.toString())
                dtmfGenerator.playTone(char)
            }
        }

        binding.btnDtmfClear.setOnClickListener {
            binding.etDtmfDisplay.text?.clear()
        }

        binding.btnDtmfSend.setOnClickListener {
            val sequence = binding.etDtmfDisplay.text?.toString() ?: return@setOnClickListener
            if (sequence.isEmpty()) return@setOnClickListener
            dtmfGenerator.playSequence(sequence)
        }
    }

    private fun setupVoxControls() {
        binding.switchVox.setOnCheckedChangeListener { _, checked ->
            audioEngine.voxEnabled = checked
            binding.sliderVoxSensitivity.isEnabled = checked


            if (checked) {
                // Start continuous audio monitoring for VOX
                checkAudioPermission()
                audioEngine.startCapture()
                audioEngine.onVoxTriggered = { active ->
                    activity?.runOnUiThread {
                        if (active) activatePtt() else deactivatePtt()
                    }
                }
                binding.tvPttHint.text = "VOX active — speak to transmit"
            } else {
                audioEngine.onVoxTriggered = null
                if (!isPttDown) {
                    audioEngine.stopCapture()
                }
                binding.tvPttHint.text = "Hold to talk"
            }
        }

        binding.sliderVoxSensitivity.addOnChangeListener { _, value, _ ->
            audioEngine.voxThreshold = value.toInt()
        }

        // Audio level metering
        audioEngine.onAudioLevel = { rms, _ ->
            activity?.runOnUiThread {
                val level = (rms / 150f * 100f).coerceIn(0f, 100f).toInt()
                binding.audioLevelBar.progress = level
            }
        }
    }

    private fun setupModeSelector() {
        binding.chipGroupMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val isPhoneMode = checkedIds.contains(R.id.chipModePhone)
            if (isPhoneMode) {
                val found = audioEngine.useUsbAudioDevice()
                binding.tvUsbAudioStatus.text = if (found)
                    "USB Audio: connected" else "USB Audio: not detected (using phone)"
            } else {
                audioEngine.useDefaultAudioDevice()
                binding.tvUsbAudioStatus.text = "Handset mode — audio via hand mic"
            }
        }

        // Check USB audio on load
        val usbDevices = audioEngine.getUsbAudioDeviceNames()
        binding.tvUsbAudioStatus.text = if (usbDevices.isNotEmpty())
            "USB Audio: ${usbDevices.first()}" else "USB Audio: not detected"
    }

    private fun setupRepeaterControls() {
        binding.btnRefreshRepeater.setOnClickListener {
            lastLocation?.let { loc ->
                viewModel.findNearestRepeater(loc.latitude, loc.longitude)
            } ?: Snackbar.make(binding.root, "Waiting for GPS fix...", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnConnectRepeater.setOnClickListener {
            val repeater = viewModel.nearestRepeater.value ?: return@setOnClickListener
            viewModel.connectToRepeater(repeater)
        }

        viewModel.nearestRepeater.observe(viewLifecycleOwner) { repeater ->
            if (repeater != null) {
                val dist = viewModel.nearestRepeaterDistance.value
                binding.tvRepeaterCallsign.text = repeater.callsign
                binding.tvRepeaterInfo.text = buildString {
                    append("${repeater.city}, ${repeater.state}")
                    if (dist != null) append(" — %.1f mi".format(dist))
                }
                binding.tvRepeaterFreq.text = "${repeater.frequencyDisplay}  ${repeater.toneDisplay}"
                binding.btnConnectRepeater.isEnabled = true
            } else {
                binding.tvRepeaterCallsign.text = "--"
                binding.tvRepeaterInfo.text = "No repeaters found nearby"
                binding.tvRepeaterFreq.text = ""
                binding.btnConnectRepeater.isEnabled = false
            }
        }
    }

    private fun setupCallsignControls() {
        val csm = viewModel.callsignManager

        // Display callsign from settings (read-only on dashboard)
        binding.tvCallsignDisplay.text = if (csm.isCallsignSet) csm.callsign else "No callsign set"

        binding.btnManualId.setOnClickListener {
            if (!csm.isCallsignSet) {
                Snackbar.make(binding.root, "Set your callsign in Settings", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            csm.transmitId()
        }

        // ID timer display update
        idTimerHandler.post(object : Runnable {
            override fun run() {
                // Refresh callsign display in case it changed in settings
                binding.tvCallsignDisplay.text = if (csm.isCallsignSet) csm.callsign else "No callsign set"

                val remaining = csm.timeUntilNextIdMs
                binding.tvIdTimer.text = if (remaining >= 0) {
                    val min = remaining / 60_000
                    val sec = (remaining % 60_000) / 1000
                    "Next ID: %d:%02d".format(min, sec)
                } else {
                    "ID: --:--"
                }
                idTimerHandler.postDelayed(this, 1000)
            }
        })

        csm.onIdTransmitting = { callsign ->
            activity?.runOnUiThread {
                Snackbar.make(binding.root, "Transmitting ID: $callsign", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSecondaryPanel() {
        binding.btnShowSecondary.setOnClickListener {
            binding.secondaryPanel.visibility = View.VISIBLE
        }
        binding.btnCloseSecondary.setOnClickListener {
            binding.secondaryPanel.visibility = View.GONE
        }
    }

    private fun setupDtmfModal() {
        binding.btnDtmfOpen.setOnClickListener {
            binding.dtmfModal.visibility = View.VISIBLE
        }
        binding.btnDtmfClose.setOnClickListener {
            binding.dtmfModal.visibility = View.GONE
        }
    }

    private fun setupScanControls() {
        scanManager.setRxActiveProvider { viewModel.rxActive.value == true }
        scanManager.setPttActiveProvider { viewModel.pttActive.value == true }

        scanManager.setListener(object : ScanManager.ScanListener {
            override fun onChannelChange(channel: RadioChannel) {
                updateChannelDisplay(channel.number, channel.name, channel.rxFrequency)
                viewModel.setActiveChannel(channel.number)
            }

            override fun onScanStateChanged(state: ScanManager.ScanState) {
                activity?.runOnUiThread { updateScanUi(state) }
            }

            override fun onActivityDetected(channel: RadioChannel) {
                activity?.runOnUiThread {
                    binding.tvScanChannel.text = "▶ CH%02d %s %s".format(
                        channel.number + 1,
                        channel.name.ifEmpty { "" },
                        channel.formattedRxFreq
                    )
                }
            }

            override fun onScanLogUpdated(log: List<ScanManager.ScanLogEntry>) {
                activity?.runOnUiThread {
                    if (log.isEmpty()) {
                        binding.tvScanLog.text = "No scan activity."
                    } else {
                        binding.tvScanLog.text = log.take(20).joinToString("\n") {
                            ScanManager.formatLogEntry(it)
                        }
                    }
                }
            }
        })

        binding.btnScanToggle.setOnClickListener {
            if (scanManager.state.value == ScanManager.ScanState.IDLE) {
                val channels = viewModel.channels.value ?: return@setOnClickListener
                val settings = viewModel.settings.value
                val mode = settings?.scanMode ?: com.db20g.controller.protocol.ScanMode.TIME_OPERATED
                scanManager.updateChannels(channels, mode)

                // Apply priority channel
                val priText = binding.etPriorityChannel.text?.toString()
                scanManager.priorityChannelNumber = priText?.toIntOrNull()?.let { it - 1 } ?: -1

                // Apply dual-watch
                val dwText = binding.etDualWatchChannel.text?.toString()
                scanManager.dualWatchChannel = dwText?.toIntOrNull()?.let { it - 1 } ?: -1

                scanManager.startScan()
            } else {
                scanManager.stopScan()
            }
        }

        binding.btnNuisanceDelete.setOnClickListener {
            val ch = scanManager.currentScanChannel.value ?: return@setOnClickListener
            scanManager.nuisanceDelete(ch.number)
            Snackbar.make(binding.root, "Skipping CH${ch.number + 1}", Snackbar.LENGTH_SHORT).show()
        }

        binding.chipGroupScanSpeed.setOnCheckedStateChangeListener { _, checkedIds ->
            scanManager.scanSpeed = when {
                checkedIds.contains(R.id.chipScanSlow) -> ScanManager.ScanSpeed.SLOW
                checkedIds.contains(R.id.chipScanFast) -> ScanManager.ScanSpeed.FAST
                else -> ScanManager.ScanSpeed.MEDIUM
            }
        }
    }

    private fun updateScanUi(state: ScanManager.ScanState) {
        when (state) {
            ScanManager.ScanState.IDLE -> {
                binding.tvScanState.text = "IDLE"
                binding.tvScanState.setTextColor(0xFF555555.toInt())
                binding.btnScanToggle.text = "Start Scan"
                binding.btnNuisanceDelete.isEnabled = false
                binding.tvScanChannel.text = "—"
            }
            ScanManager.ScanState.SCANNING -> {
                binding.tvScanState.text = "SCANNING"
                binding.tvScanState.setTextColor(0xFF4CAF50.toInt())
                binding.btnScanToggle.text = "Stop Scan"
                binding.btnNuisanceDelete.isEnabled = true
            }
            ScanManager.ScanState.PAUSED_ON_ACTIVITY -> {
                binding.tvScanState.text = "ACTIVITY"
                binding.tvScanState.setTextColor(0xFFFF6F00.toInt())
                binding.btnScanToggle.text = "Stop Scan"
                binding.btnNuisanceDelete.isEnabled = true
            }
            ScanManager.ScanState.TALK_BACK_WAIT -> {
                binding.tvScanState.text = "TALK-BACK"
                binding.tvScanState.setTextColor(0xFFFF5252.toInt())
                binding.btnScanToggle.text = "Stop Scan"
                binding.btnNuisanceDelete.isEnabled = false
            }
        }
    }

    private fun setupSignalMonitor() {
        audioAnalyzer.setListener(object : AudioAnalyzer.AnalysisListener {
            override fun onAnalysisResult(result: AudioAnalyzer.AnalysisResult) {
                activity?.runOnUiThread {
                    val rmsPercent = (result.rms / 150f).coerceIn(0f, 100f).toInt()
                    val peakPercent = (result.peak / 327f).coerceIn(0f, 100f).toInt()

                    binding.signalLevelBar.progress = rmsPercent
                    binding.peakLevelBar.progress = peakPercent
                    binding.tvSignalRms.text = "%.0f".format(result.rms)
                    binding.tvSignalPeak.text = "${result.peak}"
                    binding.tvSnr.text = "SNR: %.1f dB".format(result.snr)
                    binding.tvNoiseFloor.text = "Noise: %.0f".format(result.noiseFloor)

                    // Quality badge color
                    val qColor = when {
                        result.qualityScore >= 70 -> 0xFF4CAF50.toInt()
                        result.qualityScore >= 40 -> 0xFFFF6F00.toInt()
                        else -> 0xFFFF5252.toInt()
                    }
                    binding.tvSignalQuality.text = "Q: ${result.qualityScore}"
                    binding.tvSignalQuality.setTextColor(qColor)

                    // Update spectrum
                    binding.spectrumView.updateSpectrum(result.fftMagnitudes, result.fftFrequencies)
                }
            }

            override fun onSignalEvent(event: AudioAnalyzer.SignalEvent) {
                activity?.runOnUiThread {
                    val history = audioAnalyzer.getSignalHistory()
                    if (history.isEmpty()) {
                        binding.tvSignalHistory.text = "No signal events."
                    } else {
                        binding.tvSignalHistory.text = history.take(10).joinToString("\n") { e ->
                            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                                .format(java.util.Date(e.timestamp))
                            "$time CH${e.channelNumber + 1} Q:${e.qualityScore} ${e.durationMs / 1000}s"
                        }
                    }
                }
            }
        })

        // Hook into audio engine's onAudioData callback
        audioEngine.onAudioData = { samples, count ->
            audioAnalyzer.setCurrentChannel(viewModel.activeChannelIndex.value ?: 0)
            audioAnalyzer.analyze(samples, count)
        }

        // EQ controls
        binding.switchEq.setOnCheckedChangeListener { _, checked ->
            audioAnalyzer.eqEnabled = checked
            binding.sliderBass.isEnabled = checked
            binding.sliderTreble.isEnabled = checked
        }

        binding.sliderBass.addOnChangeListener { _, value, _ ->
            audioAnalyzer.bassGain = value
        }

        binding.sliderTreble.addOnChangeListener { _, value, _ ->
            audioAnalyzer.trebleGain = value
        }
    }

    private fun setupMultiRadio() {
        viewModel.initMultiRadio()

        binding.btnScanRadios.setOnClickListener {
            val drivers = viewModel.multiRadioManager.discoverDevices()
            if (drivers.isEmpty()) {
                Snackbar.make(binding.root, "No USB serial devices found", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Connect all discovered devices
            for (driver in drivers) {
                viewModel.connectMultiRadio(driver)
            }
        }

        binding.btnRadioInventory.setOnClickListener {
            val inventory = viewModel.getRadioInventory()
            binding.tvRadioInventory.text = inventory
            binding.scrollRadioInventory.visibility =
                if (binding.scrollRadioInventory.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Observe radio list changes and update chip group
        viewModel.radioProfiles.observe(viewLifecycleOwner) { radios ->
            binding.chipGroupRadios.removeAllViews()
            binding.tvRadioCount.text = "${radios.count { it.isConnected }} radios"

            for (radio in radios) {
                val chip = Chip(requireContext()).apply {
                    text = radio.label
                    isCheckable = true
                    isChecked = radio.id == viewModel.activeRadioProfile.value?.id
                    setOnClickListener {
                        viewModel.switchActiveRadio(radio.id)
                    }
                    setOnLongClickListener {
                        viewModel.disconnectMultiRadio(radio.id)
                        true
                    }
                }
                binding.chipGroupRadios.addView(chip)
            }

            if (radios.isEmpty()) {
                binding.tvActiveRadio.text = "No radios connected"
            }
        }

        viewModel.activeRadioProfile.observe(viewLifecycleOwner) { profile ->
            if (profile != null) {
                val device = profile.usbDevice
                binding.tvActiveRadio.text = "▶ ${profile.label} (${device.productName ?: "USB"})"
            } else {
                binding.tvActiveRadio.text = "No radios connected"
            }
        }
    }

    private fun setupObservers() {
        viewModel.channels.observe(viewLifecycleOwner) { channels ->
            val active = channels?.filter { !it.isEmpty } ?: return@observe
            if (active.isNotEmpty()) {
                currentChannelIndex = currentChannelIndex.coerceIn(0, active.size - 1)
                val ch = active[currentChannelIndex]
                updateChannelDisplay(ch.number, ch.name, ch.rxFrequency)
            }
        }

        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            val connected = state == ConnectionState.CONNECTED
            binding.btnPtt.isEnabled = connected
            binding.btnPtt.alpha = if (connected) 1.0f else 0.4f
        }

        viewModel.rxActive.observe(viewLifecycleOwner) { active ->
            binding.tvRxIndicator.setTextColor(
                if (active) 0xFF4CAF50.toInt() else 0xFF555555.toInt()
            )
            binding.tvRxIndicator.setBackgroundColor(
                if (active) 0xFF113311.toInt() else 0xFF222222.toInt()
            )
        }
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startAudioEngine()
        }
    }

    private fun startAudioEngine() {
        val prefs = requireContext().getSharedPreferences("ptt_config", android.content.Context.MODE_PRIVATE)
        audioEngine.setSpeakerphone(prefs.getBoolean("speakerphone", true))
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            startLocationUpdates()
        }
    }

    private var locationListener: android.location.LocationListener? = null

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        locationManager = requireContext().getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val listener = android.location.LocationListener { location ->
            lastLocation = location
            viewModel.findNearestRepeater(location.latitude, location.longitude)
        }
        locationListener = listener
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 30_000L, 100f, listener
        )
        // Also try last known location for a quick initial result
        val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (lastKnown != null) {
            lastLocation = lastKnown
            viewModel.findNearestRepeater(lastKnown.latitude, lastKnown.longitude)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanManager.destroy()
        idTimerHandler.removeCallbacksAndMessages(null)
        if (isPttDown) {
            viewModel.pttUp()
        }
        audioEngine.release()
        dtmfGenerator.release()
        locationListener?.let { locationManager?.removeUpdates(it) }
        locationListener = null
        _binding = null
    }
}
