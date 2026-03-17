package com.db20g.controller.ui

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.db20g.controller.audio.AudioRecorder
import com.db20g.controller.databinding.FragmentToolsBinding
import com.db20g.controller.protocol.GmrsConstants
import com.db20g.controller.protocol.PowerLevel
import com.db20g.controller.protocol.RadioChannel
import com.db20g.controller.protocol.ToneValue
import com.db20g.controller.protocol.ChirpCsvManager
import com.db20g.controller.protocol.QsoLogger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class ToolsFragment : Fragment() {

    companion object {
        const val ARG_MODE = "mode"
        const val MODE_RECORDING = "recording"
        const val MODE_DATA_TOOLS = "data_tools"
    }

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var recordingAdapter: RecordingAdapter
    private lateinit var qsoLogger: QsoLogger
    private var mediaPlayer: MediaPlayer? = null

    private val adifExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                qsoLogger.exportAdif(output)
                appendLog("Exported QSO log as ADIF")
            }
        }
    }

    private val qsoCsvExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                qsoLogger.exportCsv(output)
                appendLog("Exported QSO log as CSV")
            }
        }
    }

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val context = requireContext()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                val tempFile = File(context.cacheDir, "temp_export.img")
                viewModel.saveToFile(tempFile)
                if (tempFile.exists()) {
                    tempFile.inputStream().use { it.copyTo(output) }
                    tempFile.delete()
                }
            }
        }
    }

    private val loadFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val context = requireContext()
            val tempFile = File(context.cacheDir, "temp_import.img")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { input.copyTo(it) }
            }
            if (tempFile.exists()) {
                viewModel.loadFromFile(tempFile)
                tempFile.delete()
            }
        }
    }

    private val chirpExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                viewModel.exportChirpCsv(output)
            }
        }
    }

    private val chirpImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val context = requireContext()
            // Detect file name from URI for extension-based type detection
            val fileName = uri.lastPathSegment ?: "import.csv"
            val ext = if (fileName.endsWith(".img", ignoreCase = true)) ".img" else ".csv"
            val tempFile = File(context.cacheDir, "chirp_import$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { input.copyTo(it) }
            }
            if (tempFile.exists()) {
                val result = viewModel.importChirpFile(tempFile)
                tempFile.delete()
                showImportPreview(result)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDownload.setOnClickListener {
            if (viewModel.connectionState.value == ConnectionState.CONNECTED) {
                viewModel.downloadFromRadio()
            } else {
                appendLog("Not connected to radio")
            }
        }

        binding.btnUpload.setOnClickListener {
            if (viewModel.connectionState.value != ConnectionState.CONNECTED) {
                appendLog("Not connected to radio")
                return@setOnClickListener
            }
            // Run validation first
            val report = viewModel.getValidationReport()
            val hasErrors = report.contains("error(s)")
            val message = if (hasErrors) {
                "Validation issues found:\n\n$report\n\nUpload will be blocked if there are errors."
            } else {
                "This will overwrite ALL channels and settings in the radio. Continue?"
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Upload to Radio")
                .setMessage(message)
                .setPositiveButton("Upload") { _, _ -> viewModel.uploadToRadio() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSaveFile.setOnClickListener {
            saveFileLauncher.launch("db20g_backup.img")
        }

        binding.btnLoadFile.setOnClickListener {
            loadFileLauncher.launch("application/octet-stream")
        }

        binding.btnLoadGmrsChannels.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Load GMRS Channels")
                .setMessage("This will load the standard 22 GMRS channels into memory slots 1-22. Existing data in those slots will be overwritten.")
                .setPositiveButton("Load") { _, _ -> loadGmrsChannels(false) }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnLoadGmrsRepeaters.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Load GMRS + Repeater Pairs")
                .setMessage("This will load 22 GMRS channels plus repeater input/output pairs into memory. Existing channels will be overwritten.")
                .setPositiveButton("Load") { _, _ -> loadGmrsChannels(true) }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear All Channels")
                .setMessage("This will erase all channels from memory. This cannot be undone (until uploading to the radio).")
                .setPositiveButton("Clear") { _, _ ->
                    val empty = List(128) { RadioChannel(number = it, isEmpty = true) }
                    empty.forEach { viewModel.addChannel(it) }
                    appendLog("All channels cleared")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnValidateAll.setOnClickListener {
            val report = viewModel.getValidationReport()
            binding.tvValidationResult.visibility = android.view.View.VISIBLE
            binding.tvValidationResult.text = report
            appendLog("Validation complete")
        }

        binding.btnExportChirpCsv.setOnClickListener {
            chirpExportLauncher.launch("db20g_channels.csv")
        }

        binding.btnImportChirpFile.setOnClickListener {
            chirpImportLauncher.launch("*/*")
        }

        // --- Audio Recording ---
        audioRecorder = AudioRecorder(requireContext())
        recordingAdapter = RecordingAdapter(
            onPlay = { info -> playRecording(info) },
            onDelete = { info -> deleteRecording(info) }
        )
        binding.rvRecordings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecordings.adapter = recordingAdapter

        // Init toggle states from saved prefs
        binding.switchRecordingEnabled.isChecked = audioRecorder.isEnabled
        binding.switchRecordRx.isChecked = audioRecorder.recordRx
        binding.switchRecordTx.isChecked = audioRecorder.recordTx
        binding.etRetentionDays.setText(audioRecorder.maxRetentionDays.toString())
        binding.etMaxStorageMb.setText(audioRecorder.maxStorageMb.toString())

        binding.switchRecordingEnabled.setOnCheckedChangeListener { _, checked ->
            audioRecorder.isEnabled = checked
            viewModel.setRecordingEnabled(checked)
            appendLog(if (checked) "Recording enabled" else "Recording disabled")
        }
        binding.switchRecordRx.setOnCheckedChangeListener { _, checked ->
            audioRecorder.recordRx = checked
        }
        binding.switchRecordTx.setOnCheckedChangeListener { _, checked ->
            audioRecorder.recordTx = checked
        }
        binding.etRetentionDays.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val days = binding.etRetentionDays.text.toString().toIntOrNull() ?: 30
                audioRecorder.maxRetentionDays = days
                binding.etRetentionDays.setText(audioRecorder.maxRetentionDays.toString())
            }
        }
        binding.etMaxStorageMb.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val mb = binding.etMaxStorageMb.text.toString().toIntOrNull() ?: 500
                audioRecorder.maxStorageMb = mb
                binding.etMaxStorageMb.setText(audioRecorder.maxStorageMb.toString())
            }
        }

        binding.btnDeleteAllRecordings.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete All Recordings")
                .setMessage("This will permanently delete all audio recordings. This cannot be undone.")
                .setPositiveButton("Delete All") { _, _ ->
                    audioRecorder.deleteAllRecordings()
                    refreshRecordingList()
                    appendLog("All recordings deleted")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnClearActivityLog.setOnClickListener {
            audioRecorder.clearActivityLog()
            binding.tvActivityLog.text = "No activity logged."
        }

        binding.btnHardwareGuide.setOnClickListener {
            startActivity(Intent(requireContext(), HardwareGuideActivity::class.java))
        }

        binding.btnTextMessaging.setOnClickListener {
            startActivity(Intent(requireContext(), TextMessagingActivity::class.java))
        }

        binding.btnSstv.setOnClickListener {
            startActivity(Intent(requireContext(), SstvActivity::class.java))
        }

        binding.btnSpectrum.setOnClickListener {
            startActivity(Intent(requireContext(), SpectrumActivity::class.java))
        }

        binding.btnWeather.setOnClickListener {
            startActivity(Intent(requireContext(), WeatherActivity::class.java))
        }

        // --- Social & Community Features ---
        qsoLogger = QsoLogger(requireContext())
        setupCommunityFeatures()

        // Register recorder with ViewModel so audio engine can feed it
        viewModel.setAudioRecorder(audioRecorder)

        refreshRecordingList()
        refreshActivityLog()

        // Observe status messages for log
        viewModel.statusMessage.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotEmpty()) appendLog(msg)
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                appendLog("ERROR: $err")
                viewModel.clearError()
            }
        }

        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            val connected = state == ConnectionState.CONNECTED || state == ConnectionState.BUSY
            binding.btnDownload.isEnabled = connected && state != ConnectionState.BUSY
            binding.btnUpload.isEnabled = connected && state != ConnectionState.BUSY
        }

        // Apply mode-based section visibility
        applyModeFilter()
    }

    private fun applyModeFilter() {
        val mode = arguments?.getString(ARG_MODE)
        when (mode) {
            MODE_RECORDING -> {
                binding.groupDataTools.visibility = View.GONE
                binding.groupExtras.visibility = View.GONE
                binding.groupRecording.visibility = View.VISIBLE
            }
            MODE_DATA_TOOLS -> {
                binding.groupDataTools.visibility = View.VISIBLE
                binding.groupExtras.visibility = View.VISIBLE
                binding.groupRecording.visibility = View.GONE
            }
        }
    }

    private fun loadGmrsChannels(includeRepeaters: Boolean) {
        var slot = 0
        for (gmrs in GmrsConstants.GMRS_CHANNELS) {
            if (slot >= 128) break
            val channel = RadioChannel(
                number = slot,
                rxFrequency = gmrs.rxFreqMHz,
                txFrequency = gmrs.txFreqMHz,
                power = if (gmrs.maxPowerW >= 50.0) PowerLevel.HIGH else PowerLevel.LOW,
                wideband = true,
                scan = true,
                name = "GMRS ${gmrs.number}",
                isEmpty = false
            )
            viewModel.addChannel(channel)
            slot++

            // Add repeater pair if applicable
            if (includeRepeaters && gmrs.txFreqMHz != gmrs.rxFreqMHz) {
                if (slot >= 128) break
                val rptChannel = RadioChannel(
                    number = slot,
                    rxFrequency = gmrs.txFreqMHz,
                    txFrequency = gmrs.rxFreqMHz,
                    power = PowerLevel.HIGH,
                    wideband = true,
                    scan = true,
                    name = "GMRS${gmrs.number}R",
                    txTone = ToneValue.None,
                    isEmpty = false
                )
                viewModel.addChannel(rptChannel)
                slot++
            }
        }

        appendLog("Loaded ${slot} GMRS channels")
    }

    private fun showImportPreview(result: ChirpCsvManager.ImportResult) {
        if (!result.success) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Import Failed")
                .setMessage(result.summary + if (result.warnings.isNotEmpty()) {
                    "\n\nWarnings:\n" + result.warnings.joinToString("\n") { "• $it" }
                } else "")
                .setPositiveButton("OK", null)
                .show()
            appendLog("CHIRP import failed: ${result.summary}")
            return
        }

        val preview = buildString {
            appendLine(result.summary)
            if (result.warnings.isNotEmpty()) {
                appendLine()
                appendLine("Warnings:")
                result.warnings.forEach { appendLine("• $it") }
            }
            appendLine()
            appendLine("Channels to import:")
            result.channels.take(20).forEach { ch ->
                appendLine("  ${ch.number}: ${ch.name} - ${"%.4f".format(ch.rxFrequency)} MHz")
            }
            if (result.channels.size > 20) {
                appendLine("  ... and ${result.channels.size - 20} more")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Preview")
            .setMessage(preview)
            .setPositiveButton("Apply") { _, _ ->
                viewModel.applyImportedChannels(result.channels)
                appendLog("Applied ${result.channels.size} channels from CHIRP import")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playRecording(info: AudioRecorder.RecordingInfo) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(info.file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { stopPlayback() }
            }
            appendLog("Playing: ${info.file.name}")
        } catch (e: Exception) {
            appendLog("Playback error: ${e.message}")
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    private fun deleteRecording(info: AudioRecorder.RecordingInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Recording")
            .setMessage("Delete ${info.file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                audioRecorder.deleteRecording(info.file)
                refreshRecordingList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshRecordingList() {
        val recordings = audioRecorder.listRecordings()
        recordingAdapter.submitList(recordings)
        binding.tvNoRecordings.visibility = if (recordings.isEmpty()) View.VISIBLE else View.GONE
        binding.rvRecordings.visibility = if (recordings.isEmpty()) View.GONE else View.VISIBLE

        val usedBytes = audioRecorder.getStorageUsedBytes()
        val usedMb = usedBytes / (1024.0 * 1024.0)
        val count = audioRecorder.getRecordingCount()
        binding.tvRecordingStats.text = "$count recordings • ${"%.1f".format(usedMb)} MB used"

        // Enforce storage limits
        audioRecorder.enforceStorageLimits()
    }

    private fun refreshActivityLog() {
        val lines = audioRecorder.getActivityLog()
        binding.tvActivityLog.text = if (lines.isEmpty()) {
            "No activity logged."
        } else {
            lines.joinToString("\n")
        }
    }

    private fun appendLog(message: String) {
        val current = binding.tvLog.text.toString()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        binding.tvLog.text = "$current\n[$timestamp] $message"
    }

    private fun setupCommunityFeatures() {
        refreshQsoDisplay()

        binding.btnLogQso.setOnClickListener {
            val theirCallsign = binding.etQsoCallsign.text?.toString()?.trim() ?: ""
            if (theirCallsign.isEmpty()) {
                appendLog("Enter a callsign to log QSO")
                return@setOnClickListener
            }
            val myCallsign = viewModel.callsignManager.callsign
            val chIdx = viewModel.activeChannelIndex.value ?: 0
            val channel = viewModel.channels.value?.getOrNull(chIdx)
            val freq = channel?.rxFrequency ?: 0.0
            val notes = binding.etQsoNotes.text?.toString() ?: ""

            qsoLogger.logQso(QsoLogger.QsoRecord(
                callsign = myCallsign,
                theirCallsign = theirCallsign,
                frequency = freq,
                channel = chIdx,
                notes = notes
            ))

            binding.etQsoCallsign.text?.clear()
            binding.etQsoNotes.text?.clear()
            appendLog("QSO logged with $theirCallsign on CH${chIdx + 1}")
            refreshQsoDisplay()
        }

        binding.btnExportAdif.setOnClickListener {
            adifExportLauncher.launch("qso_log.adi")
        }

        binding.btnExportQsoCsv.setOnClickListener {
            qsoCsvExportLauncher.launch("qso_log.csv")
        }

        binding.btnShareChannels.setOnClickListener {
            val channels = viewModel.channels.value ?: emptyList()
            val json = qsoLogger.exportChannelConfigJson(channels)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
                putExtra(Intent.EXTRA_SUBJECT, "DB20-G Channel Configuration")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Channels"))
        }

        binding.btnSearchContacts.setOnClickListener {
            val query = binding.etContactSearch.text?.toString() ?: ""
            if (query.isEmpty()) {
                val contacts = qsoLogger.getContacts().take(20)
                binding.tvContactResults.text = if (contacts.isEmpty()) "No contacts"
                else contacts.joinToString("\n") { c ->
                    "${c.callsign} — ${c.name.ifEmpty { "?" }} ${c.city} ${c.state} (${c.totalQsos} QSOs)"
                }
            } else {
                val results = qsoLogger.searchContacts(query)
                binding.tvContactResults.text = if (results.isEmpty()) "No matches for \"$query\""
                else results.joinToString("\n") { c ->
                    "${c.callsign} — ${c.name.ifEmpty { "?" }} ${c.city} ${c.state} (${c.totalQsos} QSOs)"
                }
            }
        }
    }

    private fun refreshQsoDisplay() {
        val stats = qsoLogger.getQsoStats()
        val total = stats["totalQsos"] as Int
        val unique = stats["uniqueCallsigns"] as Int
        binding.tvQsoStats.text = "$total QSOs • $unique unique callsigns"

        val recent = qsoLogger.getQsoLog().take(15)
        val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US)
        binding.tvRecentQsos.text = if (recent.isEmpty()) "No QSOs logged."
        else recent.joinToString("\n") { q ->
            "${sdf.format(java.util.Date(q.startTime))} ${q.theirCallsign.padEnd(10)} CH${q.channel + 1} ${"%.4f".format(q.frequency)}"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPlayback()
        _binding = null
    }
}
