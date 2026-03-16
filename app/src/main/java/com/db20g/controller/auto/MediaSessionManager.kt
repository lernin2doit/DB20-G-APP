package com.db20g.controller.auto

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.db20g.controller.ui.MainActivity

/**
 * MediaSession integration for car head unit display and steering wheel controls.
 *
 * Maps radio operations to media session concepts:
 * - Play/Pause → PTT toggle
 * - Skip Next/Prev → Channel Up/Down
 * - Custom actions for Scan and Emergency
 * - Metadata shows current channel, frequency, callsign
 * - PlaybackState shows TX/RX/Idle status
 */
class MediaSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "MediaSessionMgr"
        private const val SESSION_TAG = "DB20G_Radio"

        const val ACTION_SCAN = "com.db20g.controller.ACTION_SCAN"
        const val ACTION_EMERGENCY = "com.db20g.controller.ACTION_EMERGENCY"
    }

    private var mediaSession: MediaSessionCompat? = null
    private var listener: MediaSessionListener? = null

    interface MediaSessionListener {
        fun onPttToggle()
        fun onChannelUp()
        fun onChannelDown()
        fun onScanToggle()
        fun onEmergency()
        fun onVoiceCommand(query: String)
    }

    fun setListener(listener: MediaSessionListener) {
        this.listener = listener
    }

    fun initialize() {
        val session = MediaSessionCompat(context, SESSION_TAG)

        // Set the launch intent so tapping the notification opens the app
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session.setSessionActivity(pendingIntent)

        // Set initial playback state
        session.setPlaybackState(buildPlaybackState(RadioState.IDLE, 0))

        // Set initial metadata
        session.setMetadata(buildMetadata("No Channel", "—", ""))

        // Set callback for media button events
        session.setCallback(object : MediaSessionCompat.Callback() {

            override fun onPlay() {
                // Play → PTT activate
                Log.d(TAG, "Media button: Play → PTT activate")
                listener?.onPttToggle()
            }

            override fun onPause() {
                // Pause → PTT release (same toggle)
                Log.d(TAG, "Media button: Pause → PTT release")
                listener?.onPttToggle()
            }

            override fun onStop() {
                Log.d(TAG, "Media button: Stop")
                listener?.onPttToggle()
            }

            override fun onSkipToNext() {
                // Skip next → Channel up
                Log.d(TAG, "Media button: Skip Next → Channel Up")
                listener?.onChannelUp()
            }

            override fun onSkipToPrevious() {
                // Skip prev → Channel down
                Log.d(TAG, "Media button: Skip Prev → Channel Down")
                listener?.onChannelDown()
            }

            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    ACTION_SCAN -> {
                        Log.d(TAG, "Custom action: Scan")
                        listener?.onScanToggle()
                    }
                    ACTION_EMERGENCY -> {
                        Log.d(TAG, "Custom action: Emergency")
                        listener?.onEmergency()
                    }
                }
            }

            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                // Voice search → parse as voice command
                if (!query.isNullOrEmpty()) {
                    Log.d(TAG, "Voice search: $query")
                    listener?.onVoiceCommand(query)
                }
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                // Let the default handling work for standard media buttons
                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        })

        // Indicate what actions are supported
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        session.isActive = true
        mediaSession = session
        Log.d(TAG, "MediaSession initialized")
    }

    /**
     * Update the head unit display with current radio state.
     */
    fun updateRadioState(
        channelName: String,
        frequency: String,
        callsign: String,
        state: RadioState,
        channelNumber: Int
    ) {
        mediaSession?.let { session ->
            session.setMetadata(buildMetadata(channelName, frequency, callsign))
            session.setPlaybackState(buildPlaybackState(state, channelNumber.toLong()))
        }
    }

    /**
     * Update just the state (TX/RX/Idle) without changing metadata.
     */
    fun updateState(state: RadioState) {
        mediaSession?.let { session ->
            val currentPosition = session.controller?.playbackState?.position ?: 0L
            session.setPlaybackState(buildPlaybackState(state, currentPosition))
        }
    }

    private fun buildMetadata(
        channelName: String,
        frequency: String,
        callsign: String
    ): MediaMetadataCompat {
        return MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, channelName)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "${frequency} MHz")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "DB20-G GMRS Radio")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, callsign)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                "GMRS Radio — $channelName — ${frequency} MHz")
            .build()
    }

    private fun buildPlaybackState(
        state: RadioState,
        position: Long
    ): PlaybackStateCompat {
        val pbState = when (state) {
            RadioState.TRANSMITTING -> PlaybackStateCompat.STATE_PLAYING
            RadioState.RECEIVING -> PlaybackStateCompat.STATE_BUFFERING
            RadioState.SCANNING -> PlaybackStateCompat.STATE_FAST_FORWARDING
            RadioState.IDLE -> PlaybackStateCompat.STATE_PAUSED
        }

        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH

        return PlaybackStateCompat.Builder()
            .setState(pbState, position, 1.0f)
            .setActions(actions)
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_SCAN, "Scan", android.R.drawable.ic_media_ff
                ).build()
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_EMERGENCY, "Emergency", android.R.drawable.ic_dialog_alert
                ).build()
            )
            .build()
    }

    fun getSession(): MediaSessionCompat? = mediaSession

    fun getSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken

    fun release() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        listener = null
        Log.d(TAG, "MediaSession released")
    }

    enum class RadioState {
        IDLE,
        TRANSMITTING,
        RECEIVING,
        SCANNING
    }
}
