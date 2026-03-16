package com.db20g.controller.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.app.PendingIntent
import com.db20g.controller.R

/**
 * Large (4x2) home screen widget for DB20-G radio controller.
 * Shows full channel info, PTT button, channel navigation, and scan control.
 */
class RadioWidgetLargeProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateLargeWidget(context, manager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            RadioWidgetProvider.ACTION_PTT,
            RadioWidgetProvider.ACTION_CH_UP,
            RadioWidgetProvider.ACTION_CH_DOWN,
            RadioWidgetProvider.ACTION_SCAN -> {
                val serviceIntent = Intent(intent.action).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(serviceIntent)
            }
        }
    }

    private fun updateLargeWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val prefs = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
        val channel = prefs.getInt("channel", 1)
        val channelName = prefs.getString("channel_name", "GMRS CH1") ?: "GMRS CH1"
        val frequency = prefs.getString("frequency", "462.5625") ?: "462.5625"
        val transmitting = prefs.getBoolean("transmitting", false)
        val connected = prefs.getBoolean("connected", false)

        val views = RemoteViews(context.packageName, R.layout.widget_radio_large)

        views.setTextViewText(R.id.tvChannel, "Channel $channel")
        views.setTextViewText(R.id.tvChannelName, channelName)
        views.setTextViewText(R.id.tvFrequency, "$frequency MHz")
        views.setTextViewText(R.id.tvStatus,
            if (connected) "Connected" else "Disconnected")

        // PTT button appearance
        val pttLabel = if (transmitting) "TX" else "PTT"
        views.setTextViewText(R.id.tvPtt, pttLabel)
        views.setInt(R.id.pttButton, "setBackgroundColor",
            if (transmitting) 0xFFC62828.toInt() else 0xFF4CAF50.toInt())

        // Click intents
        views.setOnClickPendingIntent(R.id.pttButton,
            getPendingIntent(context, RadioWidgetProvider.ACTION_PTT))
        views.setOnClickPendingIntent(R.id.btnChUp,
            getPendingIntent(context, RadioWidgetProvider.ACTION_CH_UP))
        views.setOnClickPendingIntent(R.id.btnChDown,
            getPendingIntent(context, RadioWidgetProvider.ACTION_CH_DOWN))
        views.setOnClickPendingIntent(R.id.btnScan,
            getPendingIntent(context, RadioWidgetProvider.ACTION_SCAN))

        manager.updateAppWidget(id, views)
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, RadioWidgetLargeProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
