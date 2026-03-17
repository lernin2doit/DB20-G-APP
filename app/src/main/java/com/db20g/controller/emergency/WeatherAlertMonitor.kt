package com.db20g.controller.emergency

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.db20g.controller.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NOAA/NWS Weather Alert Monitor.
 *
 * Uses the public NWS API (api.weather.gov) to fetch active alerts
 * based on device location. No API key required.
 *
 * Features:
 * - Location-based alert monitoring with configurable radius
 * - Severity color coding (watch/warning/emergency)
 * - Push notifications for severe weather
 * - Auto-switch to emergency mode during active warnings
 * - Weather channel frequency reference
 * - Periodic polling (configurable interval)
 */
class WeatherAlertMonitor(private val context: Context) {

    companion object {
        private const val TAG = "WeatherAlert"
        private const val CHANNEL_ID = "weather_alerts"
        private const val PREFS_NAME = "weather_alert_prefs"
        private const val NWS_API_BASE = "https://api.weather.gov"

        // NOAA Weather Radio (NWR) frequencies
        val WEATHER_FREQUENCIES = listOf(
            162.400, 162.425, 162.450, 162.475, 162.500, 162.525, 162.550
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scopeJob = Job()
    private val scope = CoroutineScope(Dispatchers.IO + scopeJob)
    private var monitorJob: Job? = null

    @Volatile
    private var monitoring = false
    private var lastLocation: Location? = null
    private val activeAlerts = java.util.Collections.synchronizedList(mutableListOf<WeatherAlert>())
    private val seenAlertIds = mutableSetOf<String>()

    var listener: WeatherAlertListener? = null
    var emergencyCallback: (() -> Unit)? = null

    // Configuration
    var pollIntervalMinutes: Int
        get() = prefs.getInt("poll_interval", 5)
        set(value) = prefs.edit().putInt("poll_interval", value.coerceIn(1, 60)).apply()

    var radiusKm: Int
        get() = prefs.getInt("radius_km", 50)
        set(value) = prefs.edit().putInt("radius_km", value.coerceIn(10, 500)).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(value) = prefs.edit().putBoolean("notifications_enabled", value).apply()

    var autoEmergencyMode: Boolean
        get() = prefs.getBoolean("auto_emergency", true)
        set(value) = prefs.edit().putBoolean("auto_emergency", value).apply()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Weather Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "NOAA severe weather alerts"
                enableLights(true)
                enableVibration(true)
            }
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }

    /**
     * Update current device location for alert queries.
     */
    fun updateLocation(location: Location) {
        lastLocation = location
    }

    /**
     * Start periodic weather alert monitoring.
     */
    fun startMonitoring() {
        if (monitoring) return
        monitoring = true

        monitorJob = scope.launch {
            while (isActive && monitoring) {
                fetchAlerts()
                delay(pollIntervalMinutes * 60_000L)
            }
        }
        Log.d(TAG, "Weather alert monitoring started (interval=${pollIntervalMinutes}min)")
    }

    fun stopMonitoring() {
        monitoring = false
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Fetch one-time alert check.
     */
    fun checkNow() {
        scope.launch { fetchAlerts() }
    }

    fun getActiveAlerts(): List<WeatherAlert> = synchronized(activeAlerts) { activeAlerts.toList() }

    private suspend fun fetchAlerts() {
        val location = lastLocation
        if (location == null) {
            Log.w(TAG, "No location available for weather alerts")
            return
        }

        try {
            val lat = location.latitude
            val lon = location.longitude

            // NWS alerts by point
            val url = URL("$NWS_API_BASE/alerts/active?point=$lat,$lon")
            val connection = withContext(Dispatchers.IO) {
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "DB20-G-Controller/1.0 (GMRS Radio App)")
                    setRequestProperty("Accept", "application/geo+json")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "NWS API returned $responseCode")
                connection.disconnect()
                return
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            parseAlerts(body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather alerts: ${e.message}")
        }
    }

    private fun parseAlerts(json: String) {
        val root = JSONObject(json)
        val features = root.optJSONArray("features") ?: return

        val newAlerts = mutableListOf<WeatherAlert>()

        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val props = feature.getJSONObject("properties")

            val alert = WeatherAlert(
                id = props.optString("id", ""),
                event = props.optString("event", "Unknown"),
                headline = props.optString("headline", ""),
                description = props.optString("description", ""),
                severity = parseSeverity(props.optString("severity", "Unknown")),
                urgency = props.optString("urgency", "Unknown"),
                certainty = props.optString("certainty", "Unknown"),
                areaDesc = props.optString("areaDesc", ""),
                onset = props.optString("onset", ""),
                expires = props.optString("expires", ""),
                senderName = props.optString("senderName", ""),
                instruction = props.optString("instruction", "")
            )
            newAlerts.add(alert)
        }

        // Detect newly arrived alerts
        val newIds = newAlerts.map { it.id }.toSet()
        val freshAlerts = newAlerts.filter { it.id !in seenAlertIds }

        activeAlerts.clear()
        activeAlerts.addAll(newAlerts)
        seenAlertIds.addAll(newIds)
        // Prune old seen IDs to prevent unbounded growth
        if (seenAlertIds.size > 500) {
            val currentIds = activeAlerts.map { it.id }.toSet()
            seenAlertIds.retainAll(currentIds)
        }

        // Notify listener
        listener?.onAlertsUpdated(activeAlerts)

        // Send notifications for new alerts
        for (alert in freshAlerts) {
            if (notificationsEnabled) {
                sendNotification(alert)
            }
            listener?.onNewAlert(alert)

            // Auto emergency mode for extreme/severe alerts
            if (autoEmergencyMode && alert.severity in listOf(Severity.EXTREME, Severity.SEVERE)) {
                emergencyCallback?.invoke()
                listener?.onEmergencyTriggered(alert)
            }
        }

        Log.d(TAG, "Fetched ${newAlerts.size} alerts (${freshAlerts.size} new)")
    }

    private fun parseSeverity(value: String): Severity {
        return when (value.lowercase()) {
            "extreme" -> Severity.EXTREME
            "severe" -> Severity.SEVERE
            "moderate" -> Severity.MODERATE
            "minor" -> Severity.MINOR
            else -> Severity.UNKNOWN
        }
    }

    private fun sendNotification(alert: WeatherAlert) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ ${alert.event}")
            .setContentText(alert.headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${alert.headline}\n\n${alert.areaDesc}\n\n${alert.instruction}"))
            .setPriority(when (alert.severity) {
                Severity.EXTREME -> NotificationCompat.PRIORITY_MAX
                Severity.SEVERE -> NotificationCompat.PRIORITY_HIGH
                else -> NotificationCompat.PRIORITY_DEFAULT
            })
            .setColor(alert.severity.color)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
    }

    fun release() {
        stopMonitoring()
        scopeJob.cancel()
    }

    // ======================== Data Classes ========================

    enum class Severity(val label: String, val color: Int) {
        EXTREME("Extreme", 0xFFD50000.toInt()),    // Red
        SEVERE("Severe", 0xFFFF6D00.toInt()),       // Orange
        MODERATE("Moderate", 0xFFFFD600.toInt()),    // Yellow
        MINOR("Minor", 0xFF2962FF.toInt()),          // Blue
        UNKNOWN("Unknown", 0xFF757575.toInt())       // Gray
    }

    data class WeatherAlert(
        val id: String,
        val event: String,
        val headline: String,
        val description: String,
        val severity: Severity,
        val urgency: String,
        val certainty: String,
        val areaDesc: String,
        val onset: String,
        val expires: String,
        val senderName: String,
        val instruction: String
    ) {
        fun formattedExpiry(): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val date = sdf.parse(expires)
                SimpleDateFormat("MMM dd h:mm a", Locale.US).format(date ?: Date())
            } catch (e: Exception) {
                expires
            }
        }
    }

    interface WeatherAlertListener {
        fun onAlertsUpdated(alerts: List<WeatherAlert>)
        fun onNewAlert(alert: WeatherAlert)
        fun onEmergencyTriggered(alert: WeatherAlert)
    }
}
