package com.db20g.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.db20g.controller.bluetooth.BluetoothPttManager
import com.db20g.controller.databinding.FragmentBluetoothPttBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class BluetoothPttFragment : Fragment() {

    private var _binding: FragmentBluetoothPttBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RadioViewModel by activityViewModels()

    private lateinit var btManager: BluetoothPttManager
    private val deviceAdapter = BtDeviceAdapter()
    private lateinit var mappingAdapter: ButtonMappingAdapter

    private val requestBtPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                refreshDevices()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBluetoothPttBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btManager = BluetoothPttManager(requireContext())
        btManager.initialize()

        // Wire PTT callbacks to the ViewModel
        btManager.onPttDown = { activity?.runOnUiThread { viewModel.pttDown() } }
        btManager.onPttUp = { activity?.runOnUiThread { viewModel.pttUp() } }
        btManager.onChannelUp = {
            activity?.runOnUiThread {
                val channels = viewModel.channels.value
                val maxIdx = if (channels.isNullOrEmpty()) 127 else channels.size - 1
                val idx = ((viewModel.activeChannelIndex.value ?: 0) + 1).coerceAtMost(maxIdx)
                viewModel.setActiveChannel(idx)
            }
        }
        btManager.onChannelDown = {
            activity?.runOnUiThread {
                val idx = (viewModel.activeChannelIndex.value ?: 0) - 1
                viewModel.setActiveChannel(idx.coerceAtLeast(0))
            }
        }

        btManager.onDeviceConnected = { dev ->
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "BT connected: ${dev.name}", Toast.LENGTH_SHORT).show()
                refreshDevices()
            }
        }
        btManager.onDeviceDisconnected = { dev ->
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "BT disconnected: ${dev.name}", Toast.LENGTH_SHORT).show()
                refreshDevices()
            }
        }

        setupToggles()
        setupDeviceList()
        setupMappings()
        setupKeyTest()

        requestBluetoothPermissions()
    }

    private fun setupToggles() {
        binding.switchBtPtt.isChecked = btManager.isEnabled
        binding.switchBtAudio.isChecked = btManager.audioRoutingEnabled

        binding.switchBtPtt.setOnCheckedChangeListener { _, checked ->
            btManager.isEnabled = checked
        }
        binding.switchBtAudio.setOnCheckedChangeListener { _, checked ->
            btManager.audioRoutingEnabled = checked
        }
    }

    private fun setupDeviceList() {
        binding.rvBtDevices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = deviceAdapter
        }

        binding.btnRefreshDevices.setOnClickListener { refreshDevices() }
        refreshDevices()
    }

    private fun setupMappings() {
        mappingAdapter = ButtonMappingAdapter { keyCode, currentAction ->
            showActionPicker(keyCode, currentAction)
        }

        binding.rvButtonMappings.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = mappingAdapter
        }

        binding.btnResetMappings.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset Button Mappings?")
                .setMessage("This will restore default BT button mappings.")
                .setPositiveButton("Reset") { _, _ ->
                    btManager.resetToDefaults()
                    refreshMappings()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        refreshMappings()
    }

    private fun setupKeyTest() {
        // The activity needs to forward key events to us.
        // We show the last received key event here for testing.
        binding.tvKeyTest.text = "Press any BT button..."
        binding.tvKeyAction.text = "Action: —"
    }

    /**
     * Called from the Activity when a key event is received.
     * Updates the test display and processes the event through BluetoothPttManager.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (_binding == null) return false

        val keyName = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        val action = btManager.getButtonMapping(event.keyCode)

        if (event.action == KeyEvent.ACTION_DOWN) {
            binding.tvKeyTest.text = "Key: $keyName (code ${event.keyCode})"
            binding.tvKeyAction.text = "Action: ${action.name.replace("_", " ")}"
        }

        return btManager.handleKeyEvent(event)
    }

    private fun refreshDevices() {
        if (!hasBluetoothPermission()) {
            binding.tvBtStatus.text = "Bluetooth permission required"
            binding.tvNoDevices.visibility = View.VISIBLE
            binding.rvBtDevices.visibility = View.GONE
            return
        }

        val bonded = btManager.getBondedDevices()
        val connected = btManager.getConnectedDevices()

        // Merge: show connected first, then other bonded
        val connectedAddresses = connected.map { it.address }.toSet()
        val allDevices = connected + bonded.filter { it.address !in connectedAddresses }

        deviceAdapter.submitList(allDevices)

        if (allDevices.isEmpty()) {
            binding.tvNoDevices.visibility = View.VISIBLE
            binding.rvBtDevices.visibility = View.GONE
            binding.tvBtStatus.text = "No Bluetooth devices found"
        } else {
            binding.tvNoDevices.visibility = View.GONE
            binding.rvBtDevices.visibility = View.VISIBLE
            val connCount = connected.size
            binding.tvBtStatus.text = "${allDevices.size} device(s) — $connCount connected"
        }
    }

    private fun refreshMappings() {
        val items = btManager.getAllMappings().map { (keyCode, action) ->
            ButtonMappingAdapter.MappingItem(
                keyCode = keyCode,
                keyName = KeyEvent.keyCodeToString(keyCode),
                action = action
            )
        }.sortedBy { it.action.ordinal }

        mappingAdapter.submitList(items)
    }

    private fun showActionPicker(keyCode: Int, currentAction: BluetoothPttManager.ButtonAction) {
        val actions = BluetoothPttManager.ButtonAction.entries
        val names = actions.map { it.name.replace("_", " ") }.toTypedArray()
        val selected = actions.indexOf(currentAction)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Action for ${KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")}")
            .setSingleChoiceItems(names, selected) { dialog, which ->
                btManager.setButtonMapping(keyCode, actions[which])
                refreshMappings()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-API 31 doesn't need runtime BT permission
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val needed = perms.filter {
                ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                requestBtPermission.launch(needed.toTypedArray())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        btManager.shutdown()
        _binding = null
    }
}
