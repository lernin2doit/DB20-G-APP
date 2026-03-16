package com.db20g.controller.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.db20g.controller.databinding.FragmentChannelsBinding
import com.db20g.controller.databinding.DialogEditChannelBinding
import com.db20g.controller.protocol.*
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChannelsFragment : Fragment() {

    private var _binding: FragmentChannelsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()
    private lateinit var adapter: ChannelListAdapter
    private lateinit var groupManager: ChannelGroupManager
    private val undoManager = UndoManager()

    private var filterMode = FilterMode.ALL
    private var activeGroupFilter: String? = null
    private var searchQuery = ""
    private var lastDownloadedSnapshot: List<RadioChannel>? = null

    // File picker for backup restore
    private val restoreFilePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val json = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: return@registerForActivityResult
            val result = groupManager.restoreBackup(json)
            if (result.success && result.channels.isNotEmpty()) {
                saveUndoState("Restore backup")
                viewModel.applyImportedChannels(result.channels)
                refreshGroups()
                refreshFavorites()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Restore Complete")
                    .setMessage(result.summary)
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Restore Failed")
                    .setMessage(result.summary)
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Error")
                .setMessage("Failed to read file: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // File picker for saving backup
    private val backupFileSaver = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val channels = viewModel.channels.value ?: emptyList()
            val json = groupManager.createBackup(channels)
            requireContext().contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray())
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Backup Saved")
                .setMessage("Channel configuration backed up successfully.")
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Error")
                .setMessage("Failed to save backup: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChannelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupManager = ChannelGroupManager(requireContext())

        adapter = ChannelListAdapter(
            onEditClick = { channel -> showEditChannelDialog(channel) },
            onFavoriteClick = { channel ->
                groupManager.toggleFavorite(channel.number)
                refreshFavorites()
            },
            onSelectionChanged = { selected -> updateBulkToolbar(selected) },
            onLongClick = { channel ->
                if (!adapter.multiSelectMode) {
                    adapter.multiSelectMode = true
                    adapter.toggleSelection(channel.number)
                    binding.bulkEditToolbar.visibility = View.VISIBLE
                }
            }
        )
        adapter.favorites = groupManager.getFavorites()

        binding.recyclerChannels.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerChannels.adapter = adapter

        // Search bar
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilter()
            }
        })

        // Filter chips
        binding.chipAll.setOnClickListener { filterMode = FilterMode.ALL; activeGroupFilter = null; applyFilter() }
        binding.chipActive.setOnClickListener { filterMode = FilterMode.ACTIVE; activeGroupFilter = null; applyFilter() }
        binding.chipGmrs.setOnClickListener { filterMode = FilterMode.GMRS; activeGroupFilter = null; applyFilter() }
        binding.chipFavorites.setOnClickListener { filterMode = FilterMode.FAVORITES; activeGroupFilter = null; applyFilter() }

        // Bulk edit buttons
        binding.btnBulkPower.setOnClickListener { showBulkPowerDialog() }
        binding.btnBulkClone.setOnClickListener { showBulkCloneDialog() }
        binding.btnBulkGroup.setOnClickListener { showBulkGroupDialog() }
        binding.btnBulkDelete.setOnClickListener { showBulkDeleteDialog() }
        binding.btnBulkCancel.setOnClickListener { exitMultiSelect() }

        // Undo / Redo
        binding.btnUndo.setOnClickListener { performUndo() }
        binding.btnRedo.setOnClickListener { performRedo() }

        // Diff / Groups / Backup
        binding.btnDiffView.setOnClickListener { showDiffView() }
        binding.btnManageGroups.setOnClickListener { showManageGroupsDialog() }
        binding.btnBackupRestore.setOnClickListener { showBackupRestoreDialog() }

        // Observe channels
        viewModel.channels.observe(viewLifecycleOwner) {
            applyFilter()
            updateUndoRedoButtons()
        }

        refreshGroups()
    }

    // --- Search & Filtering ---

    private fun applyFilter() {
        val allChannels = viewModel.channels.value ?: emptyList()
        val favorites = groupManager.getFavorites()

        var filtered = when (filterMode) {
            FilterMode.ALL -> allChannels
            FilterMode.ACTIVE -> allChannels.filter { !it.isEmpty }
            FilterMode.GMRS -> allChannels.filter { ch ->
                !ch.isEmpty && GmrsConstants.GMRS_CHANNELS.any { gmrs ->
                    kotlin.math.abs(ch.rxFrequency - gmrs.rxFreqMHz) < 0.0001
                }
            }
            FilterMode.FAVORITES -> allChannels.filter { it.number in favorites && !it.isEmpty }
        }

        // Group filter
        activeGroupFilter?.let { groupId ->
            val group = groupManager.getGroups().find { it.id == groupId }
            if (group != null) {
                filtered = filtered.filter { it.number in group.channelNumbers }
            }
        }

        // Search query
        if (searchQuery.isNotEmpty()) {
            val q = searchQuery.lowercase()
            filtered = filtered.filter { ch ->
                ch.name.lowercase().contains(q) ||
                String.format("%.4f", ch.rxFrequency).contains(q) ||
                String.format("%.4f", ch.txFrequency).contains(q) ||
                toneValueToString(ch.txTone).lowercase().contains(q) ||
                toneValueToString(ch.rxTone).lowercase().contains(q) ||
                "ch ${ch.number + 1}".contains(q)
            }
        }

        val activeCount = allChannels.count { !it.isEmpty }
        binding.tvChannelCount.text = "$activeCount/${allChannels.size} channels"

        if (filtered.isEmpty() && allChannels.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerChannels.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerChannels.visibility = View.VISIBLE
            adapter.submitList(filtered.toList())
        }
    }

    private fun showEditChannelDialog(channel: RadioChannel) {
        val dialogBinding = DialogEditChannelBinding.inflate(layoutInflater)

        dialogBinding.tvEditorChannelNum.text = "Channel ${channel.number + 1}"

        // Populate fields
        if (!channel.isEmpty) {
            dialogBinding.etChannelName.setText(channel.name)
            dialogBinding.etRxFreq.setText(String.format("%.4f", channel.rxFrequency))
            dialogBinding.etTxFreq.setText(String.format("%.4f", channel.txFrequency))

            when (channel.power) {
                PowerLevel.HIGH -> dialogBinding.chipPowerHigh.isChecked = true
                PowerLevel.MEDIUM -> dialogBinding.chipPowerMid.isChecked = true
                PowerLevel.LOW -> dialogBinding.chipPowerLow.isChecked = true
            }

            if (!channel.wideband) {
                dialogBinding.chipBandwidthNarrow.isChecked = true
            } else {
                dialogBinding.chipBandwidthWide.isChecked = true
            }

            dialogBinding.switchBcl.isChecked = channel.bcl
            dialogBinding.switchScan.isChecked = channel.scan
        } else {
            dialogBinding.chipPowerHigh.isChecked = true
            dialogBinding.chipBandwidthWide.isChecked = true
            dialogBinding.switchScan.isChecked = true
        }

        // Tone dropdowns
        val toneOptions = buildToneDropdownOptions()
        val toneAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, toneOptions)
        dialogBinding.actvRxTone.setAdapter(toneAdapter)
        dialogBinding.actvTxTone.setAdapter(toneAdapter)
        dialogBinding.actvRxTone.setText(toneValueToString(channel.rxTone), false)
        dialogBinding.actvTxTone.setText(toneValueToString(channel.txTone), false)

        // GMRS preset picker
        dialogBinding.btnGmrsPreset.setOnClickListener {
            showGmrsPresetPicker(dialogBinding)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnDeleteChannel.setOnClickListener {
            saveUndoState("Delete channel ${channel.number + 1}")
            viewModel.deleteChannel(channel.number)
            dialog.dismiss()
        }

        dialogBinding.btnSaveChannel.setOnClickListener {
            val rxStr = dialogBinding.etRxFreq.text.toString()
            val txStr = dialogBinding.etTxFreq.text.toString()
            val rxFreq = rxStr.toDoubleOrNull()
            val txFreq = txStr.toDoubleOrNull()

            if (rxFreq == null || txFreq == null) {
                dialogBinding.etRxFreq.error = if (rxFreq == null) "Invalid frequency" else null
                dialogBinding.etTxFreq.error = if (txFreq == null) "Invalid frequency" else null
                return@setOnClickListener
            }

            val power = when {
                dialogBinding.chipPowerLow.isChecked -> PowerLevel.LOW
                dialogBinding.chipPowerMid.isChecked -> PowerLevel.MEDIUM
                else -> PowerLevel.HIGH
            }

            val updatedChannel = RadioChannel(
                number = channel.number,
                rxFrequency = rxFreq,
                txFrequency = txFreq,
                rxTone = parseToneString(dialogBinding.actvRxTone.text.toString()),
                txTone = parseToneString(dialogBinding.actvTxTone.text.toString()),
                power = power,
                wideband = !dialogBinding.chipBandwidthNarrow.isChecked,
                bcl = dialogBinding.switchBcl.isChecked,
                scan = dialogBinding.switchScan.isChecked,
                name = dialogBinding.etChannelName.text.toString().take(10),
                isEmpty = false
            )

            // Run validation on the edited channel
            val issues = ChannelValidator.validate(updatedChannel)
            val errors = issues.filter { it.severity == Severity.ERROR }
            val warnings = issues.filter { it.severity == Severity.WARNING }

            if (errors.isNotEmpty()) {
                val msg = errors.joinToString("\n\n") { "✗ ${it.message}\n${it.explanation}" }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Validation Errors")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            if (warnings.isNotEmpty()) {
                val msg = warnings.joinToString("\n\n") { "⚠ ${it.message}\n${it.explanation}" }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Validation Warnings")
                    .setMessage("$msg\n\nSave anyway?")
                    .setPositiveButton("Save") { _, _ ->
                        saveUndoState("Edit channel ${channel.number + 1}")
                        viewModel.updateChannel(updatedChannel)
                        viewModel.validateChannels()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Fix", null)
                    .show()
                return@setOnClickListener
            }

            saveUndoState("Edit channel ${channel.number + 1}")
            viewModel.updateChannel(updatedChannel)
            viewModel.validateChannels()
            dialog.dismiss()
        }

        dialog.show()
    }

    // --- Group Management ---

    private fun refreshGroups() {
        val container = binding.groupChipsContainer
        container.removeAllViews()
        val groups = groupManager.getGroups()

        if (groups.isEmpty()) {
            binding.groupScroller.visibility = View.GONE
            return
        }

        binding.groupScroller.visibility = View.VISIBLE
        for (group in groups) {
            val chip = Chip(requireContext()).apply {
                text = "${group.name} (${group.channelNumbers.size})"
                isCheckable = true
                isChecked = activeGroupFilter == group.id
                setOnClickListener {
                    if (activeGroupFilter == group.id) {
                        activeGroupFilter = null
                        isChecked = false
                    } else {
                        activeGroupFilter = group.id
                        // Uncheck others
                        for (i in 0 until container.childCount) {
                            (container.getChildAt(i) as? Chip)?.isChecked = false
                        }
                        isChecked = true
                    }
                    applyFilter()
                }
            }
            container.addView(chip)
        }
    }

    private fun refreshFavorites() {
        adapter.favorites = groupManager.getFavorites()
        applyFilter()
    }

    private fun showManageGroupsDialog() {
        val groups = groupManager.getGroups()
        if (groups.isEmpty()) {
            // No groups — offer to create one
            showCreateGroupDialog()
            return
        }

        val items = groups.map { "${it.name} (${it.channelNumbers.size} ch)" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Channel Groups")
            .setItems(items) { _, which ->
                showGroupDetailDialog(groups[which])
            }
            .setPositiveButton("New Group") { _, _ -> showCreateGroupDialog() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCreateGroupDialog(preselectedChannels: List<Int> = emptyList()) {
        val input = EditText(requireContext()).apply {
            hint = "Group name"
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Group")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    groupManager.addGroup(name, preselectedChannels)
                    refreshGroups()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGroupDetailDialog(group: ChannelGroupManager.ChannelGroup) {
        val channels = viewModel.channels.value ?: emptyList()
        val groupChannels = channels.filter { it.number in group.channelNumbers && !it.isEmpty }
        val detail = groupChannels.joinToString("\n") {
            "  CH ${it.number + 1}: ${it.name.ifBlank { String.format("%.4f", it.rxFrequency) }}"
        }.ifEmpty { "  (no active channels)" }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(group.name)
            .setMessage("${group.channelNumbers.size} channels:\n$detail")
            .setPositiveButton("Close", null)
            .setNeutralButton("Delete") { _, _ ->
                groupManager.deleteGroup(group.id)
                if (activeGroupFilter == group.id) activeGroupFilter = null
                refreshGroups()
                applyFilter()
            }
            .show()
    }

    // --- Bulk Edit Operations ---

    private fun updateBulkToolbar(selected: Set<Int>) {
        binding.tvSelectionCount.text = "${selected.size} selected"
        if (selected.isEmpty()) {
            exitMultiSelect()
        }
    }

    private fun exitMultiSelect() {
        adapter.clearSelection()
        binding.bulkEditToolbar.visibility = View.GONE
    }

    private fun showBulkPowerDialog() {
        val selected = adapter.selectedChannels.toSet()
        if (selected.isEmpty()) return

        val options = arrayOf("High", "Medium", "Low")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Set Power for ${selected.size} channels")
            .setItems(options) { _, which ->
                val power = when (which) {
                    0 -> PowerLevel.HIGH
                    1 -> PowerLevel.MEDIUM
                    else -> PowerLevel.LOW
                }
                saveUndoState("Bulk power change (${selected.size} ch)")
                val channels = viewModel.channels.value?.toMutableList() ?: return@setItems
                for (i in channels.indices) {
                    if (channels[i].number in selected && !channels[i].isEmpty) {
                        channels[i] = channels[i].copy(power = power)
                    }
                }
                viewModel.setChannels(channels)
                exitMultiSelect()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBulkCloneDialog() {
        val selected = adapter.selectedChannels.toSet()
        if (selected.isEmpty()) return

        val channels = viewModel.channels.value ?: return
        val sourceChannels = channels.filter { it.number in selected && !it.isEmpty }
        if (sourceChannels.isEmpty()) return

        // Find empty slots
        val emptySlots = channels.filter { it.isEmpty }.map { it.number }
        if (emptySlots.size < sourceChannels.size) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Not Enough Slots")
                .setMessage("Need ${sourceChannels.size} empty slots but only ${emptySlots.size} available.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clone ${sourceChannels.size} Channels")
            .setMessage("Clone selected channels to the next ${sourceChannels.size} empty slot(s)?")
            .setPositiveButton("Clone") { _, _ ->
                saveUndoState("Clone ${sourceChannels.size} channels")
                val list = channels.toMutableList()
                sourceChannels.zip(emptySlots).forEach { (src, targetSlot) ->
                    list[targetSlot] = src.copy(number = targetSlot)
                }
                viewModel.setChannels(list)
                exitMultiSelect()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBulkGroupDialog() {
        val selected = adapter.selectedChannels.toSet()
        if (selected.isEmpty()) return

        val groups = groupManager.getGroups()
        if (groups.isEmpty()) {
            showCreateGroupDialog(selected.toList())
            exitMultiSelect()
            return
        }

        val items = groups.map { it.name }.toTypedArray() + "New Group..."
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add ${selected.size} channels to group")
            .setItems(items) { _, which ->
                if (which < groups.size) {
                    for (ch in selected) {
                        groupManager.addChannelToGroup(groups[which].id, ch)
                    }
                    refreshGroups()
                } else {
                    showCreateGroupDialog(selected.toList())
                }
                exitMultiSelect()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBulkDeleteDialog() {
        val selected = adapter.selectedChannels.toSet()
        if (selected.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete ${selected.size} Channels")
            .setMessage("Clear all selected channels? This can be undone.")
            .setPositiveButton("Delete") { _, _ ->
                saveUndoState("Delete ${selected.size} channels")
                val channels = viewModel.channels.value?.toMutableList() ?: return@setPositiveButton
                for (i in channels.indices) {
                    if (channels[i].number in selected) {
                        channels[i] = RadioChannel(number = channels[i].number, isEmpty = true)
                    }
                }
                viewModel.setChannels(channels)
                exitMultiSelect()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Undo / Redo ---

    private fun saveUndoState(description: String) {
        val channels = viewModel.channels.value ?: return
        undoManager.saveState(channels, description)
        updateUndoRedoButtons()
    }

    private fun performUndo() {
        val current = viewModel.channels.value ?: return
        val snapshot = undoManager.undo(current)
        if (snapshot != null) {
            viewModel.setChannels(snapshot.channels)
            updateUndoRedoButtons()
        }
    }

    private fun performRedo() {
        val current = viewModel.channels.value ?: return
        val snapshot = undoManager.redo(current)
        if (snapshot != null) {
            viewModel.setChannels(snapshot.channels)
            updateUndoRedoButtons()
        }
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = undoManager.canUndo
        binding.btnUndo.alpha = if (undoManager.canUndo) 1.0f else 0.3f
        binding.btnRedo.isEnabled = undoManager.canRedo
        binding.btnRedo.alpha = if (undoManager.canRedo) 1.0f else 0.3f
    }

    // --- Diff View ---

    private fun showDiffView() {
        val current = viewModel.channels.value ?: emptyList()
        val snapshot = lastDownloadedSnapshot

        if (snapshot == null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Comparison Available")
                .setMessage("Download from radio first to capture a baseline snapshot for comparison.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val diffs = groupManager.diffChannels(snapshot, current)
        if (diffs.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Differences")
                .setMessage("Current channels match the last downloaded configuration.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val sb = StringBuilder()
        var added = 0; var removed = 0; var modified = 0
        for (diff in diffs) {
            val chNum = diff.channelNumber + 1
            when (diff.type) {
                ChannelGroupManager.DiffType.ADDED -> {
                    added++
                    val ch = diff.appChannel!!
                    sb.appendLine("+ CH $chNum: ${ch.name.ifBlank { String.format("%.4f", ch.rxFrequency) }}")
                }
                ChannelGroupManager.DiffType.REMOVED -> {
                    removed++
                    val ch = diff.radioChannel!!
                    sb.appendLine("− CH $chNum: ${ch.name.ifBlank { String.format("%.4f", ch.rxFrequency) }}")
                }
                ChannelGroupManager.DiffType.MODIFIED -> {
                    modified++
                    val old = diff.radioChannel!!
                    val new = diff.appChannel!!
                    sb.appendLine("~ CH $chNum: ${old.name} → ${new.name}")
                    if (old.rxFrequency != new.rxFrequency)
                        sb.appendLine("    RX: ${String.format("%.4f", old.rxFrequency)} → ${String.format("%.4f", new.rxFrequency)}")
                    if (old.txFrequency != new.txFrequency)
                        sb.appendLine("    TX: ${String.format("%.4f", old.txFrequency)} → ${String.format("%.4f", new.txFrequency)}")
                    if (old.power != new.power)
                        sb.appendLine("    Power: ${old.power} → ${new.power}")
                }
                ChannelGroupManager.DiffType.UNCHANGED -> {}
            }
        }

        val summary = "Added: $added  |  Removed: $removed  |  Modified: $modified\n\n$sb"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Channel Differences")
            .setMessage(summary)
            .setPositiveButton("Close", null)
            .show()
    }

    fun captureDownloadSnapshot(channels: List<RadioChannel>) {
        lastDownloadedSnapshot = channels.map { it.copy() }
    }

    // --- Backup / Restore ---

    private fun showBackupRestoreDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Backup & Restore")
            .setItems(arrayOf("Create Backup", "Restore from File")) { _, which ->
                when (which) {
                    0 -> backupFileSaver.launch("db20g_backup.json")
                    1 -> restoreFilePicker.launch("application/json")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Existing helpers ---

    private fun showGmrsPresetPicker(dialogBinding: DialogEditChannelBinding) {
        val channels = GmrsConstants.GMRS_CHANNELS
        val items = channels.map { ch ->
            "CH ${ch.number}: ${String.format("%.4f", ch.rxFreqMHz)} MHz" +
                if (ch.txFreqMHz != ch.rxFreqMHz) " (RPT)" else ""
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select GMRS Channel")
            .setItems(items) { _, which ->
                val gmrs = channels[which]
                dialogBinding.etRxFreq.setText(String.format("%.4f", gmrs.rxFreqMHz))
                dialogBinding.etTxFreq.setText(String.format("%.4f", gmrs.txFreqMHz))
                dialogBinding.etChannelName.setText("GMRS ${gmrs.number}")
            }
            .show()
    }

    private fun buildToneDropdownOptions(): List<String> {
        val options = mutableListOf("None")
        GmrsConstants.CTCSS_TONES.forEach { options.add("CTCSS $it") }
        GmrsConstants.DCS_CODES.forEach {
            options.add("DCS ${it}N")
            options.add("DCS ${it}I")
        }
        return options
    }

    private fun toneValueToString(tone: ToneValue): String {
        return when (tone) {
            ToneValue.None -> "None"
            is ToneValue.CTCSS -> "CTCSS ${tone.frequency}"
            is ToneValue.DCS -> "DCS ${tone.code}${if (tone.polarity == DcsPolarity.INVERTED) "I" else "N"}"
        }
    }

    private fun parseToneString(str: String): ToneValue {
        if (str == "None" || str.isBlank()) return ToneValue.None
        if (str.startsWith("CTCSS ")) {
            val freq = str.removePrefix("CTCSS ").toDoubleOrNull() ?: return ToneValue.None
            return ToneValue.CTCSS(freq)
        }
        if (str.startsWith("DCS ")) {
            val rest = str.removePrefix("DCS ")
            val polarity = if (rest.endsWith("I")) DcsPolarity.INVERTED else DcsPolarity.NORMAL
            val code = rest.dropLast(1).toIntOrNull() ?: return ToneValue.None
            return ToneValue.DCS(code, polarity)
        }
        return ToneValue.None
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class FilterMode { ALL, ACTIVE, GMRS, FAVORITES }
}
