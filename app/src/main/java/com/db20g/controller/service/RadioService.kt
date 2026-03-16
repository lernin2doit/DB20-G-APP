package com.db20g.controller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.db20g.controller.R
import com.db20g.controller.ui.MainActivity

/**
 * Foreground service that keeps the radio connection alive when the app is in the background.
 *
 * Features:
 * - Persistent notification with current channel, frequency, and signal activity
 * - Quick actions from notification: channel up/down
 * - Wake lock management for continuous monitoring
 * - Auto-restart on process kill (START_STICKY)
 * - Configurable auto-sleep timer
 */
class RadioService : Service() {

    companion object {
        private const val TAG = "RadioService"
        const val CHANNEL_ID = "radio_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.db20g.controller.action.START_SERVICE"
        const val ACTION_STOP = "com.db20g.controller.action.STOP_SERVICE"
        const val ACTION_CHANNEL_UP = "com.db20g.controller.action.CHANNEL_UP"
        const val ACTION_CHANNEL_DOWN = "com.db20g.controller.action.CHANNEL_DOWN"
        const val ACTION_PTT_DOWN = "com.db20g.controller.action.PTT_DOWN"
        const val ACTION_PTT_UP = "com.db20g.controller.action.PTT_UP"
        const val ACTION_UPDATE_NOTIFICATION = "com.db20g.controller.action.UPDATE_NOTIFICATION"

        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_FREQUENCY = "frequency"
        const val EXTRA_IS_RECEIVING = "is_receiving"
        const val EXTRA_IS_TRANSMITTING = "is_transmitting"

        fun start(context: Context) {
            val intent = Intent(context, RadioService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RadioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateNotification(
            context: Context,
            channelName: String,
            frequency: String,
            isReceiving: Boolean,
            isTransmitting: Boolean
        ) {
            val intent = Intent(context, RadioService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_FREQUENCY, frequency)
                putExtra(EXTRA_IS_RECEIVING, isReceiving)
                putExtra(EXTRA_IS_TRANSMITTING, isTransmitting)
            }
            context.startService(intent)
        }
    }

    // --- State ---

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentChannelName: String = "No channel"
    private var currentFrequency: String = "—"
    private var isReceiving: Boolean = false
    private var isTransmitting: Boolean = false
    private var autoSleepTimer: AutoSleepTimer? = null

    // Service binding for direct communication with Activity
    inner class RadioBinder : Binder() {
        fun getService(): RadioService = this@RadioService
    }

    private val binder = RadioBinder()

    // --- Callbacks ---
    var onChannelUp: (() -> Unit)? = null
    var onChannelDown: (() -> Unit)? = null
    var onPttDown: (() -> Unit)? = null
    var onPttUp: (() -> Unit)? = null

    // --- Lifecycle ---

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "RadioService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> startForegroundMode()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHANNEL_UP -> onChannelUp?.invoke()
            ACTION_CHANNEL_DOWN -> onChannelDown?.invoke()
            ACTION_PTT_DOWN -> onPttDown?.invoke()
            ACTION_PTT_UP -> onPttUp?.invoke()
            ACTION_UPDATE_NOTIFICATION -> {
                currentChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: currentChannelName
                currentFrequency = intent.getStringExtra(EXTRA_FREQUENCY) ?: currentFrequency
                isReceiving = intent.getBooleanExtra(EXTRA_IS_RECEIVING, false)
                isTransmitting = intent.getBooleanExtra(EXTRA_IS_TRANSMITTING, false)
                updateNotificationDisplay()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        autoSleepTimer?.cancel()
        Log.i(TAG, "RadioService destroyed")
    }

    // --- Foreground Service ---

    private fun startForegroundMode() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        acquireWakeLock()
        Log.i(TAG, "Foreground service started")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Radio Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps radio connection active in background"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val chUpIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RadioService::class.java).apply { action = ACTION_CHANNEL_UP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val chDownIntent = PendingIntent.getService(
            this, 2,
            Intent(this, RadioService::class.java).apply { action = ACTION_CHANNEL_DOWN },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 3,
            Intent(this, RadioService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val statusIcon = when {
            isTransmitting -> "TX"
            isReceiving -> "RX"
            else -> "●"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$statusIcon $currentChannelName — $currentFrequency MHz")
            .setContentText(
                when {
                    isTransmitting -> "Transmitting..."
                    isReceiving -> "Receiving signal"
                    else -> "Monitoring"
                }
            )
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "CH ▲", chUpIntent)
            .addAction(0, "CH ▼", chDownIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun updateNotificationDisplay() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    // --- Wake Lock ---

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DB20G::RadioServiceWakeLock"
        ).apply {
            acquire(4 * 60 * 60 * 1000L) // 4-hour max, configurable via auto-sleep
        }
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    // --- Auto-Sleep ---

    /**
     * Set an auto-sleep timer. The service will stop itself after the given minutes.
     * Pass 0 to disable.
     */
    fun setAutoSleep(minutes: Int) {
        autoSleepTimer?.cancel()
        if (minutes <= 0) {
            autoSleepTimer = null
            return
        }
        autoSleepTimer = AutoSleepTimer(minutes * 60 * 1000L) {
            Log.i(TAG, "Auto-sleep timer expired, stopping service")
            stopSelf()
        }.also { it.start() }
    }

    /**
     * Update channel info displayed in the notification.
     */
    fun updateChannel(name: String, frequency: String) {
        currentChannelName = name
        currentFrequency = frequency
        updateNotificationDisplay()
    }

    fun setReceiving(receiving: Boolean) {
        isReceiving = receiving
        updateNotificationDisplay()
    }

    fun setTransmitting(transmitting: Boolean) {
        isTransmitting = transmitting
        updateNotificationDisplay()
    }

    // --- Auto-Sleep Timer ---

    private class AutoSleepTimer(
        private val delayMs: Long,
        private val onExpire: () -> Unit
    ) {
        private var thread: Thread? = null

        @Volatile
        private var cancelled = false

        fun start() {
            cancelled = false
            thread = Thread {
                try {
                    Thread.sleep(delayMs)
                    if (!cancelled) onExpire()
                } catch (_: InterruptedException) {
                    // Cancelled
                }
            }.apply {
                isDaemon = true
                start()
            }
        }

        fun cancel() {
            cancelled = true
            thread?.interrupt()
            thread = null
        }
    }
}
