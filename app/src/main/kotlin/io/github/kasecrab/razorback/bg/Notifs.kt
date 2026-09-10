package io.github.kasecrab.razorback.bg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.kasecrab.razorback.MainActivity
import io.github.kasecrab.razorback.R

/** Channels and the two notifications the app posts: a reply in progress, a reply ready. */
object Notifs {
    const val CHANNEL_TURNS = "turns"
    const val CHANNEL_REPLIES = "replies"
    const val CHANNEL_VOICE = "voice"
    const val ID_TURN = 1
    const val ID_REPLY = 2
    const val ID_VOICE = 3

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TURNS, context.getString(R.string.channel_turns), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REPLIES, context.getString(R.string.channel_replies), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_VOICE, context.getString(R.string.channel_voice), NotificationManager.IMPORTANCE_LOW))
    }

    fun openChat(context: Context, conversationId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
        if (conversationId != null) intent.data = Uri.parse("razorback://chat/$conversationId")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, conversationId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun turnInProgress(context: Context, title: String, conversationId: String?): Notification =
        Notification.Builder(context, CHANNEL_TURNS)
            .setSmallIcon(R.drawable.ic_waveform)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_replying))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setContentIntent(openChat(context, conversationId))
            .build()

    fun replyReady(context: Context, title: String, preview: String, conversationId: String?) {
        val n = Notification.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_waveform)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(Notification.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setContentIntent(openChat(context, conversationId))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_REPLY, n)
    }

    fun cancelReply(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_REPLY)
    }
}
