package com.db20g.controller.ui

import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.db20g.controller.R

/**
 * Quick Settings tile for PTT toggle and channel display.
 *
 * Tap: Toggle PTT on/off
 * Label: Current channel name / frequency
 * Subtitle: "Transmitting" / "Idle"
 *
 * Uses widget_state SharedPreferences for cross-process state.
 */
class RadioTileService : TileService() {

    private lateinit var prefs: SharedPreferences
    private var transmitting = false

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("widget_state", MODE_PRIVATE)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        transmitting = !transmitting

        // Update shared state so widgets also reflect the change
        prefs.edit().putBoolean("transmitting", transmitting).apply()

        // Forward PTT action to RadioService
        val intent = android.content.Intent(RadioWidgetProvider.ACTION_PTT).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)

        updateTileState()

        // Also trigger widget updates
        RadioWidgetProvider.updateWidgetState(
            this,
            prefs.getInt("channel", 1),
            prefs.getString("channel_name", "GMRS CH1") ?: "GMRS CH1",
            prefs.getString("frequency", "462.5625") ?: "462.5625",
            transmitting,
            prefs.getBoolean("connected", false)
        )
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        transmitting = prefs.getBoolean("transmitting", false)
        val channel = prefs.getInt("channel", 1)
        val channelName = prefs.getString("channel_name", "GMRS CH1") ?: "GMRS CH1"

        tile.label = "CH $channel $channelName"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (transmitting) "Transmitting" else "Idle"
        }
        tile.state = if (transmitting) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        tile.updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }
}
