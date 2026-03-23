package com.db20g.controller.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.transition.TransitionManager
import com.db20g.controller.R
import com.db20g.controller.databinding.ActivityMainBinding
import com.db20g.controller.service.RadioService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: RadioViewModel by viewModels()
    private var isPillExpanded = false

    companion object {
        private const val ACTION_USB_PERMISSION = "com.db20g.controller.USB_PERMISSION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeManager = ThemeManager(this)
        setTheme(themeManager.getThemeResId())
        themeManager.applyNightMode()

        super.onCreate(savedInstanceState)

        if (!SetupWizardActivity.isSetupComplete(this)) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupPill()
        setupObservers()
        setupButtons()
        registerUsbReceiver()

        viewModel.scanDevices()
    }

    private fun setupTabs() {
        val pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        val themeManager = ThemeManager(this)
        val isRedLight = themeManager.currentTheme == ThemeManager.THEME_RED_LIGHT
        val emergencyNormal = if (isRedLight) 0xFF2E7D32.toInt() else 0xFFD32F2F.toInt()
        val emergencySelected = if (isRedLight) 0xFF4CAF50.toInt() else 0xFFFF1744.toInt()
        val emergencyTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(emergencySelected, emergencyNormal)
        )
        val normalTint = ContextCompat.getColorStateList(this, R.color.tab_icon_color)

        val tabLayout = binding.tabLayout
        val navRail = binding.navRail

        if (tabLayout != null) {
            // Portrait: TabLayout at top
            val tabIcons = intArrayOf(
                R.drawable.ic_tab_live, R.drawable.ic_tab_channels,
                R.drawable.ic_tab_repeaters, R.drawable.ic_tab_routes,
                R.drawable.ic_tab_settings, R.drawable.ic_tab_emergency
            )

            TabLayoutMediator(tabLayout, binding.viewPager) { tab, position ->
                tab.setIcon(tabIcons[position])
                tab.contentDescription = MainPagerAdapter.TAB_TITLES[position]
            }.attach()

            for (i in 0 until tabLayout.tabCount) {
                val tab = tabLayout.getTabAt(i)
                tab?.icon = tab?.icon?.mutate()
                if (i == MainPagerAdapter.EMERGENCY_TAB_POSITION) {
                    tab?.icon?.setTintList(emergencyTint)
                } else {
                    tab?.icon?.setTintList(normalTint)
                }
            }

            binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    for (i in 0 until tabLayout.tabCount) {
                        val tab = tabLayout.getTabAt(i)
                        if (i == MainPagerAdapter.EMERGENCY_TAB_POSITION) {
                            tab?.icon?.setTintList(emergencyTint)
                        } else {
                            tab?.icon?.setTintList(normalTint)
                        }
                    }
                }
            })
        } else if (navRail != null) {
            // Landscape: NavigationRailView on side
            applyNavRailSide(navRail)

            // Disable NavigationRailView's default icon tinting so we control it
            navRail.itemIconTintList = null

            val menu = navRail.menu
            // Apply individual tints to all menu items
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                item.icon = item.icon?.mutate()
                if (item.itemId == R.id.nav_emergency) {
                    item.icon?.setTintList(emergencyTint)
                } else {
                    item.icon?.setTintList(normalTint)
                }
            }

            // Map menu item IDs to ViewPager positions
            val navIdToPosition = mapOf(
                R.id.nav_live to 0, R.id.nav_channels to 1,
                R.id.nav_repeaters to 2, R.id.nav_routes to 3,
                R.id.nav_settings to 4, R.id.nav_emergency to 5
            )
            val positionToNavId = navIdToPosition.entries.associate { (k, v) -> v to k }

            navRail.setOnItemSelectedListener { item ->
                val pos = navIdToPosition[item.itemId] ?: return@setOnItemSelectedListener false
                if (binding.viewPager.currentItem != pos) {
                    binding.viewPager.setCurrentItem(pos, false)
                }
                true
            }

            binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val navId = positionToNavId[position] ?: return
                    if (navRail.selectedItemId != navId) {
                        navRail.selectedItemId = navId
                    }
                    // Re-apply icon tints on page change
                    for (i in 0 until menu.size()) {
                        val item = menu.getItem(i)
                        if (item.itemId == R.id.nav_emergency) {
                            item.icon?.setTintList(emergencyTint)
                        } else {
                            item.icon?.setTintList(normalTint)
                        }
                    }
                }
            })
        }
    }

    private fun applyNavRailSide(navRail: View) {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val side = prefs.getString("nav_rail_side", "left") ?: "left"
        val container = binding.navContainer ?: return
        if (side == "right") {
            container.removeView(navRail)
            container.addView(navRail)
        }
    }

    private fun setupPill() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("show_status_pill", true)) {
            binding.statusPill.visibility = View.GONE
        }
    }

    private fun togglePill() {
        isPillExpanded = !isPillExpanded
        TransitionManager.beginDelayedTransition(binding.statusPill)
        val layout = binding.pillContentLayout
        if (isPillExpanded) {
            binding.pillExpandedContent.visibility = View.VISIBLE
            layout.setPaddingRelative(dpToPx(16), 0, dpToPx(4), 0)
        } else {
            binding.pillExpandedContent.visibility = View.GONE
            layout.setPaddingRelative(dpToPx(18), 0, dpToPx(18), 0)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun setupObservers() {
        viewModel.connectionState.observe(this) { state ->
            when (state) {
                ConnectionState.DISCONNECTED -> {
                    binding.pillStatusIndicator.setBackgroundResource(R.drawable.circle_indicator)
                    binding.pillStatusText.text = "Disconnected"
                    binding.pillActionBtn.text = "Connect"
                    binding.pillActionBtn.visibility = if (isPillExpanded) View.VISIBLE else View.GONE
                    binding.pillDisconnectBtn.visibility = View.GONE
                    binding.progressBar.visibility = View.GONE
                }
                ConnectionState.CONNECTING -> {
                    binding.pillStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_busy)
                    binding.pillStatusText.text = "Connecting..."
                    binding.pillActionBtn.visibility = View.GONE
                    binding.pillDisconnectBtn.visibility = View.GONE
                }
                ConnectionState.CONNECTED -> {
                    binding.pillStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_connected)
                    val transport = if (viewModel.transportType.value == TransportType.BLUETOOTH) "BT" else "USB"
                    binding.pillStatusText.text = "Connected ($transport)"
                    binding.pillActionBtn.text = "Read"
                    binding.pillActionBtn.visibility = if (isPillExpanded) View.VISIBLE else View.GONE
                    binding.pillDisconnectBtn.visibility = if (isPillExpanded) View.VISIBLE else View.GONE
                    binding.progressBar.visibility = View.GONE
                    RadioService.start(this)
                }
                ConnectionState.BUSY -> {
                    binding.pillStatusIndicator.setBackgroundResource(R.drawable.circle_indicator_busy)
                    binding.pillStatusText.text = "Transferring..."
                    binding.pillActionBtn.visibility = View.GONE
                    binding.pillDisconnectBtn.visibility = View.GONE
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
                binding.pillStatusText.text = msg
            }
        }
    }

    private fun setupButtons() {
        binding.pillActionBtn.setOnClickListener {
            when (viewModel.connectionState.value) {
                ConnectionState.DISCONNECTED -> showDevicePicker()
                ConnectionState.CONNECTED -> viewModel.downloadFromRadio()
                else -> {}
            }
        }

        binding.pillDisconnectBtn.setOnClickListener {
            viewModel.disconnect()
        }

        binding.statusPill.setOnClickListener {
            togglePill()
        }
    }

    @Suppress("MissingPermission")
    private fun showDevicePicker() {
        viewModel.scanDevices()
        viewModel.scanBluetoothDevices()
        val usbDevices = viewModel.availableDevices.value ?: emptyList()
        val btDevices = viewModel.btDevices.value ?: emptyList()

        if (usbDevices.isEmpty() && btDevices.isEmpty()) {
            Snackbar.make(binding.root, "No USB or Bluetooth devices found", Snackbar.LENGTH_LONG).show()
            return
        }

        // Build combined list: BT devices first, then USB
        val names = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        for (bt in btDevices) {
            val name = bt.name ?: bt.address
            names.add("\uD83D\uDCF6 BT: $name")
            actions.add { viewModel.connectBluetooth(bt) }
        }
        for (driver in usbDevices) {
            val dev = driver.device
            names.add("\uD83D\uDD0C USB: ${dev.deviceName} (${String.format("%04X:%04X", dev.vendorId, dev.productId)})")
            actions.add {
                if (viewModel.hasUsbPermission(driver)) {
                    viewModel.connect(driver)
                } else {
                    requestUsbPermission(driver.device)
                }
            }
        }

        if (names.size == 1) {
            actions[0]()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Device")
            .setItems(names.toTypedArray()) { _, which -> actions[which]() }
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
        // BluetoothPttFragment is no longer a tab — hosted in separate activity
        return false
    }
}
