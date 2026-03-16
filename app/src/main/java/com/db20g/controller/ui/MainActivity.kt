package com.db20g.controller.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.db20g.controller.R
import com.db20g.controller.databinding.ActivityMainBinding
import com.db20g.controller.service.RadioService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: RadioViewModel by viewModels()

    companion object {
        private const val ACTION_USB_PERMISSION = "com.db20g.controller.USB_PERMISSION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before calling super/setContentView
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)

        // First-launch: redirect to setup wizard
        if (!SetupWizardActivity.isSetupComplete(this)) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupTabs()
        setupObservers()
        setupButtons()
        registerUsbReceiver()

        // Auto-scan on startup
        viewModel.scanDevices()
    }

    private fun setupTabs() {
        val pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = MainPagerAdapter.TAB_TITLES[position]
        }.attach()
    }

    private fun setupObservers() {
        viewModel.connectionState.observe(this) { state ->
            when (state) {
                ConnectionState.DISCONNECTED -> {
                    binding.statusIndicator.setBackgroundResource(R.drawable.circle_indicator)
                    binding.tvConnectionStatus.text = "Disconnected"
                    binding.btnConnect.text = "Connect"
                    binding.progressBar.visibility = View.GONE
                    binding.fabAction.setImageResource(android.R.drawable.stat_sys_download)
                }
                ConnectionState.CONNECTING -> {
                    binding.statusIndicator.setBackgroundResource(R.drawable.circle_indicator_busy)
                    binding.tvConnectionStatus.text = "Connecting..."
                    binding.btnConnect.text = "Cancel"
                }
                ConnectionState.CONNECTED -> {
                    binding.statusIndicator.setBackgroundResource(R.drawable.circle_indicator_connected)
                    binding.tvConnectionStatus.text = "Connected"
                    binding.btnConnect.text = "Disconnect"
                    binding.progressBar.visibility = View.GONE
                    binding.fabAction.setImageResource(android.R.drawable.stat_sys_download)
                    RadioService.start(this)
                }
                ConnectionState.BUSY -> {
                    binding.statusIndicator.setBackgroundResource(R.drawable.circle_indicator_busy)
                    binding.tvConnectionStatus.text = "Transferring..."
                    binding.progressBar.visibility = View.VISIBLE
                }
                null -> {}
            }
        }

        viewModel.progress.observe(this) { pct ->
            binding.progressBar.progress = pct
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                    .setAction("OK") { viewModel.clearError() }
                    .show()
            }
        }

        viewModel.statusMessage.observe(this) { msg ->
            if (msg.isNotEmpty()) {
                binding.tvConnectionStatus.text = msg
            }
        }
    }

    private fun setupButtons() {
        binding.btnConnect.setOnClickListener {
            when (viewModel.connectionState.value) {
                ConnectionState.DISCONNECTED -> showDevicePicker()
                ConnectionState.CONNECTED -> viewModel.disconnect()
                else -> {}
            }
        }

        binding.fabAction.setOnClickListener {
            when (viewModel.connectionState.value) {
                ConnectionState.CONNECTED -> viewModel.downloadFromRadio()
                ConnectionState.DISCONNECTED -> showDevicePicker()
                else -> {}
            }
        }
    }

    private fun showDevicePicker() {
        viewModel.scanDevices()
        val devices = viewModel.availableDevices.value ?: emptyList()

        if (devices.isEmpty()) {
            Snackbar.make(binding.root, "No USB serial devices found", Snackbar.LENGTH_LONG).show()
            return
        }

        if (devices.size == 1) {
            val driver = devices[0]
            if (viewModel.hasUsbPermission(driver)) {
                viewModel.connect(driver)
            } else {
                requestUsbPermission(driver.device)
            }
            return
        }

        val names = devices.map { driver ->
            val dev = driver.device
            "${driver.javaClass.simpleName} — ${dev.deviceName} (${String.format("%04X:%04X", dev.vendorId, dev.productId)})"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Select USB Device")
            .setItems(names) { _, which ->
                val driver = devices[which]
                if (viewModel.hasUsbPermission(driver)) {
                    viewModel.connect(driver)
                } else {
                    requestUsbPermission(driver.device)
                }
            }
            .show()
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun autoConnect() {
        val devices = viewModel.availableDevices.value ?: return
        if (devices.size == 1 && viewModel.hasUsbPermission(devices[0])) {
            viewModel.connect(devices[0])
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        viewModel.scanDevices()
                        autoConnect()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    viewModel.scanDevices()
                    Snackbar.make(binding.root, "USB device connected", Snackbar.LENGTH_SHORT).show()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    viewModel.disconnect()
                    viewModel.scanDevices()
                    Snackbar.make(binding.root, "USB device disconnected", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        RadioService.stop(this)
        viewModel.disconnect()
    }

    // Handle USB device attached via intent filter
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            viewModel.scanDevices()
            autoConnect()
        }
    }

    // Forward key events to BluetoothPttFragment for BT button mapping
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && forwardKeyToBtFragment(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && forwardKeyToBtFragment(event)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun forwardKeyToBtFragment(event: KeyEvent): Boolean {
        val pagerAdapter = binding.viewPager.adapter as? MainPagerAdapter ?: return false
        // BT tab is at position 5
        val fragment = supportFragmentManager.findFragmentByTag("f5")
        return (fragment as? BluetoothPttFragment)?.onKeyEvent(event) ?: false
    }
}
