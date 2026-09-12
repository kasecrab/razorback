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
    const val CHANNEL_REMOTE = "remote"
    const val ID_TURN = 1
    const val ID_REPLY = 2
    const val ID_VOICE = 3
    const val ID_REMOTE = 4
    const val ID_REMOTE_ASK = 5

    /** What a notification shows to say it is one of this app's own; see App.openToken. */
    const val EXTRA_TOKEN = "open_token"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TURNS, context.getString(R.string.channel_turns), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REPLIES, context.getString(R.string.channel_replies), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_VOICE, context.getString(R.string.channel_voice), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMOTE, context.getString(R.string.channel_remote), NotificationManager.IMPORTANCE_LOW))
    }

    fun openChat(context: Context, conversationId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
        if (conversationId != null) {
            // The address is still what tells two notifications apart. What says the
            // notification is ours is the word beside it, which another app cannot read
            // out of a pending intent the system is holding.
            intent.data = Uri.parse("razorback://chat/$conversationId")
            intent.putExtra(EXTRA_TOKEN, io.github.kasecrab.razorback.App.instance.openToken)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, conversationId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Quiet and ongoing: the link being open is not news. */
    fun watchingRemote(context: Context, machine: String): Notification =
        Notification.Builder(context, CHANNEL_REMOTE)
            .setSmallIcon(R.drawable.ic_waveform)
            .setContentTitle(machine)
            .setContentText(context.getString(R.string.notif_watching))
            .setOngoing(true)
            .setContentIntent(openChat(context, null))
            .build()

    /** A machine waiting on an answer is worth a person's attention. */
    fun remoteAsking(context: Context, what: String) {
        val public = Notification.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_waveform)
            .setContentTitle(context.getString(R.string.notif_asking))
            .setContentIntent(openChat(context, null))
            .build()
        val n = Notification.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_waveform)
            .setContentTitle(context.getString(R.string.notif_asking))
            .setContentText(what)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(openChat(context, null))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_REMOTE_ASK, n)
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

    /** The words of a reply are for the person holding the unlocked phone; the lock screen sees only that one is ready. */
    fun replyReady(context: Context, title: String, preview: String, conversationId: String?) {
        val public = Notification.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_waveform)
            .setContentTitle(context.getString(R.string.notif_reply_ready))
            .setContentIntent(openChat(context, conversationId))
            .build()
        val n = Notification.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_waveform)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(Notification.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(openChat(context, conversationId))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_REPLY, n)
    }

    fun cancelReply(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_REPLY)
    }
}
