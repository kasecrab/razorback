package io.github.kasecrab.razorback.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.bg.Notifs

/** Holds the microphone open for voice mode when the screen is off or the app is behind another. */
class VoiceService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            App.instance.voice.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        Notifs.ensureChannels(this)
        val end = PendingIntent.getService(
            this, 1, Intent(this, VoiceService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, Notifs.CHANNEL_VOICE)
            .setSmallIcon(R.drawable.ic_waveform)
            .setContentTitle(getString(R.string.voice_mode))
            .setContentText(getString(R.string.voice_listening))
            .setOngoing(true)
            .setContentIntent(Notifs.openChat(this, App.instance.engine.conversation?.id))
            .addAction(Notification.Action.Builder(null, getString(R.string.voice_end), end).build())
            .build()
        startForeground(Notifs.ID_VOICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        App.instance.voice.stop()
        stopSelf()
    }

    companion object {
        private const val ACTION_END = "io.github.kasecrab.razorback.voice.END"

        fun start(context: Context) = context.startForegroundService(Intent(context, VoiceService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, VoiceService::class.java))
    }
}
