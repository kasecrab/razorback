package io.github.kasecrab.razorback.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import io.github.kasecrab.razorback.core.Log

/** Puts the phone in call-style audio for the length of a voice session. */
class AudioFocus(context: Context, private val onLost: () -> Unit) {

    private val manager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var previousMode = AudioManager.MODE_NORMAL

    fun acquire(): Boolean {
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLost()
            }
            .build()
        request = r
        val granted = manager.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        previousMode = manager.mode
        // Needs MODIFY_AUDIO_SETTINGS; without it the system ignores the call quietly and the
        // phone's echo canceller, which only runs on the call path, never engages.
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        Log.d { "audio: mode ${manager.mode} after asking for ${AudioManager.MODE_IN_COMMUNICATION}" }
        // Prefer the loudspeaker unless something like a headset is already routed.
        val speaker = manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val current = manager.communicationDevice
        if (speaker != null && (current == null || current.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)) manager.setCommunicationDevice(speaker)
        return granted
    }

    fun release() {
        manager.clearCommunicationDevice()
        manager.mode = previousMode
        request?.let { manager.abandonAudioFocusRequest(it) }
        request = null
    }
}
