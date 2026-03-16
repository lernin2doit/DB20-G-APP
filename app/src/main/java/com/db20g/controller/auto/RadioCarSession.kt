package com.db20g.controller.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Android Auto session for the radio controller.
 * Creates the main RadioScreen when the car app starts.
 */
class RadioCarSession : Session() {

    private var voiceCommandHandler: VoiceCommandHandler? = null
    private var audioFocusManager: AudioFocusManager? = null
    private var mediaSessionManager: MediaSessionManager? = null

    override fun onCreateScreen(intent: Intent): Screen {
        // Initialize support services
        val context = carContext
        voiceCommandHandler = VoiceCommandHandler(context).apply { initialize() }
        audioFocusManager = AudioFocusManager(context)
        mediaSessionManager = MediaSessionManager(context).apply { initialize() }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                voiceCommandHandler?.release()
                audioFocusManager?.release()
                mediaSessionManager?.release()
            }
        })

        return RadioScreen(
            carContext,
            voiceCommandHandler!!,
            audioFocusManager!!,
            mediaSessionManager!!
        )
    }
}
