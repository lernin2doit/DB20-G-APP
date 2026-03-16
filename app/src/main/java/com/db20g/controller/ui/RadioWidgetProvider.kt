package com.db20g.controller.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.db20g.controller.R

/**
 * Home screen widget for the DB20-G radio controller.
 *
 * Sizes:
 * - Small (2x1): Channel display, PTT indicator, channel up/down
 * - Large (4x2): Full controls with PTT button, channel info, scan, navigation
 *
 * Also supports lock screen display via widgetCategory="keyguard".
 *
 * Uses SharedPreferences "widget_state" for cross-process state from the main app/service.
 */
class RadioWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PTT = "com.db20g.controller.WIDGET_PTT"
        const val ACTION_CH_UP = "com.db20g.controller.WIDGET_CH_UP"
        const val ACTION_CH_DOWN = "com.db20g.controller.WIDGET_CH_DOWN"
        const val ACTION_SCAN = "com.db20g.controller.WIDGET_SCAN"
        private const val PREFS = "widget_state"

        /**
         * Call from main app/service to push state updates to all widgets.
         */
        fun updateWidgetState(
            context: Context,
            channel: Int,
            channelName: String,
            frequency: String,
            isTransmitting: Boolean,
            isConnected: Boolean
        ) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("channel", channel)
                .putString("channel_name", channelName)
                .putString("frequency", frequency)
                .putBoolean("transmitting", isTransmitting)
                .putBoolean("connected", isConnected)
                .apply()

            // Trigger widget update
            val mgr = AppWidgetManager.getInstance(context)
            val smallIds = mgr.getAppWidgetIds(
                ComponentName(context, RadioWidgetProvider::class.java))
            val largeIds = mgr.getAppWidgetIds(
                ComponentName(context, RadioWidgetLargeProvider::class.java))

            if (smallIds.isNotEmpty()) {
                val intent = Intent(context, RadioWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, smallIds)
                }
                context.sendBroadcast(intent)
            }
            if (largeIds.isNotEmpty()) {
                val intent = Intent(context, RadioWidgetLargeProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, largeIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            updateSmallWidget(context, manager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PTT, ACTION_CH_UP, ACTION_CH_DOWN, ACTION_SCAN -> {
                // Forward action to RadioService via broadcast
                val serviceIntent = Intent(intent.action).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(serviceIntent)
            }
        }
    }

    private fun updateSmallWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val channel = prefs.getInt("channel", 1)
        val frequency = prefs.getString("frequency", "462.5625") ?: "462.5625"
        val transmitting = prefs.getBoolean("transmitting", false)

        val views = RemoteViews(context.packageName, R.layout.widget_radio_small)

        views.setTextViewText(R.id.tvChannel, "CH $channel")
        views.setTextViewText(R.id.tvFrequency, "$frequency MHz")
        views.setTextViewText(R.id.tvPtt, if (transmitting) "TX" else "PTT")
        views.setInt(R.id.pttButton, "setBackgroundColor",
            if (transmitting) 0xFFC62828.toInt() else 0xFF4CAF50.toInt())

        // PTT click
        views.setOnClickPendingIntent(R.id.pttButton,
            getPendingIntent(context, ACTION_PTT))
        // Channel up/down
        views.setOnClickPendingIntent(R.id.btnChUp,
            getPendingIntent(context, ACTION_CH_UP))
        views.setOnClickPendingIntent(R.id.btnChDown,
            getPendingIntent(context, ACTION_CH_DOWN))

        manager.updateAppWidget(id, views)
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, RadioWidgetProvider::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
