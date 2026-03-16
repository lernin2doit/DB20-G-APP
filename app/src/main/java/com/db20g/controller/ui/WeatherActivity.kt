package com.db20g.controller.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.db20g.controller.R
import com.db20g.controller.databinding.ActivityWeatherBinding
import com.db20g.controller.emergency.WeatherAlertMonitor
import com.db20g.controller.emergency.WeatherAlertMonitor.WeatherAlert
import com.google.android.gms.location.LocationServices

class WeatherActivity : AppCompatActivity(), WeatherAlertMonitor.WeatherAlertListener {

    private lateinit var binding: ActivityWeatherBinding
    private lateinit var monitor: WeatherAlertMonitor
    private var isMonitoring = false
    private val alerts = mutableListOf<WeatherAlert>()
    private lateinit var alertAdapter: AlertAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (locationGranted) fetchLocationAndStart()
        else Toast.makeText(this, "Location required for weather alerts", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeId = getSharedPreferences("app_settings", MODE_PRIVATE).getInt("theme_id", 0)
        when (themeId) {
            1 -> setTheme(R.style.Theme_DB20GController_AMOLED)
            2 -> setTheme(R.style.Theme_DB20GController_RedLight)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        monitor = WeatherAlertMonitor(this)
        monitor.listener = this

        setupToolbar()
        setupControls()
        setupAlertList()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupControls() {
        binding.btnStartStop.setOnClickListener {
            if (isMonitoring) stopMonitoring() else startMonitoring()
        }

        binding.btnCheckNow.setOnClickListener {
            if (hasLocationPermission()) {
                fetchLocationAndCheck()
            } else {
                requestLocationPermission()
            }
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            monitor.notificationsEnabled = checked
        }

        binding.switchAutoEmergency.setOnCheckedChangeListener { _, checked ->
            monitor.autoEmergencyMode = checked
        }

        // Restore settings
        binding.switchNotifications.isChecked = monitor.notificationsEnabled
        binding.switchAutoEmergency.isChecked = monitor.autoEmergencyMode
    }

    private fun setupAlertList() {
        alertAdapter = AlertAdapter(alerts)
        binding.rvAlerts.layoutManager = LinearLayoutManager(this)
        binding.rvAlerts.adapter = alertAdapter
    }

    private fun startMonitoring() {
        if (hasLocationPermission()) {
            fetchLocationAndStart()
        } else {
            requestLocationPermission()
        }
    }

    private fun fetchLocationAndStart() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        client.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                monitor.updateLocation(location)
                monitor.startMonitoring()
                isMonitoring = true

                binding.btnStartStop.text = "STOP MONITORING"
                binding.btnStartStop.setBackgroundColor(
                    ContextCompat.getColor(this, android.R.color.holo_red_dark))
                binding.tvMonitorStatus.text =
                    "Monitoring (%.4f, %.4f)".format(location.latitude, location.longitude)
                val indicator = binding.statusIndicator.background as? GradientDrawable
                    ?: GradientDrawable()
                indicator.setColor(0xFF4CAF50.toInt())
                binding.statusIndicator.background = indicator
            } else {
                Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchLocationAndCheck() {
        val client = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        client.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                monitor.updateLocation(location)
                monitor.checkNow()
                Toast.makeText(this, "Checking for alerts...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopMonitoring() {
        monitor.stopMonitoring()
        isMonitoring = false
        binding.btnStartStop.text = "START MONITORING"
        binding.btnStartStop.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_green_dark))
        binding.tvMonitorStatus.text = "Monitoring stopped"
        val indicator = binding.statusIndicator.background as? GradientDrawable
            ?: GradientDrawable()
        indicator.setColor(0xFF757575.toInt())
        binding.statusIndicator.background = indicator
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    override fun onDestroy() {
        super.onDestroy()
        monitor.release()
    }

    // ======================== WeatherAlertListener ========================

    override fun onAlertsUpdated(alerts: List<WeatherAlert>) {
        runOnUiThread {
            this.alerts.clear()
            this.alerts.addAll(alerts)
            alertAdapter.notifyDataSetChanged()

            binding.tvAlertCount.text = "${alerts.size} active alert${if (alerts.size != 1) "s" else ""}"
            binding.tvNoAlerts.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
            binding.rvAlerts.visibility = if (alerts.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onNewAlert(alert: WeatherAlert) {
        runOnUiThread {
            Toast.makeText(this, "⚠️ ${alert.event}: ${alert.headline}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onEmergencyTriggered(alert: WeatherAlert) {
        runOnUiThread {
            Toast.makeText(this, "🚨 Emergency mode activated: ${alert.event}", Toast.LENGTH_LONG).show()
        }
    }

    // ======================== Alert Adapter ========================

    class AlertAdapter(
        private val items: List<WeatherAlert>
    ) : RecyclerView.Adapter<AlertAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val severityBanner: LinearLayout = view.findViewById(R.id.severityBanner)
            val tvSeverity: TextView = view.findViewById(R.id.tvSeverity)
            val tvEvent: TextView = view.findViewById(R.id.tvEvent)
            val tvExpires: TextView = view.findViewById(R.id.tvExpires)
            val tvHeadline: TextView = view.findViewById(R.id.tvHeadline)
            val tvArea: TextView = view.findViewById(R.id.tvArea)
            val tvInstruction: TextView = view.findViewById(R.id.tvInstruction)
            val tvSender: TextView = view.findViewById(R.id.tvSender)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_weather_alert, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val alert = items[position]

            holder.severityBanner.setBackgroundColor(alert.severity.color)
            holder.tvSeverity.text = alert.severity.label.uppercase()
            holder.tvEvent.text = alert.event
            holder.tvExpires.text = "Expires: ${alert.formattedExpiry()}"
            holder.tvHeadline.text = alert.headline
            holder.tvArea.text = alert.areaDesc
            holder.tvInstruction.text = alert.instruction.ifEmpty { "No specific instructions" }
            holder.tvSender.text = alert.senderName
        }

        override fun getItemCount() = items.size
    }
}
