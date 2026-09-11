package io.github.kasecrab.razorback.bg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.remote.Frames
import io.github.kasecrab.razorback.remote.RemoteLink

/**
 * Holds the link to a paired machine open while the app is in the background.
 *
 * A session on the other end goes on working whether or not this app is on
 * screen; what needs keeping alive is the socket that hears about it, and the
 * one thing worth waking somebody for is a question the machine is waiting on
 * an answer to.
 */
class RemoteService : Service(), RemoteLink.Watcher {

    private val link: RemoteLink get() = App.instance.remote
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifs.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            Notifs.ID_REMOTE,
            Notifs.watchingRemote(this, link.machine?.host ?: getString(R.string.app_name)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        // Only a session being watched is worth a notification and a socket held open.
        if (!link.paired || link.attached.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!watching) {
            watching = true
            link.add(this)
        }
        link.start()
        return START_NOT_STICKY
    }

    /// A question is the one thing here worth interrupting somebody for.
    override fun onAsk(question: Frames.Question, isTool: Boolean) {
        Notifs.remoteAsking(this, question.what)
    }

    override fun onDestroy() {
        if (watching) link.remove(this)
        watching = false
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val ACTION_END = "io.github.kasecrab.razorback.remote.END"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RemoteService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteService::class.java))
        }
    }
}
