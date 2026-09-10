package io.github.kasecrab.razorback.bg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.chat.ChatEngine
import io.github.kasecrab.razorback.model.MessageStatus
import io.github.kasecrab.razorback.model.Role

/**
 * Keeps a streaming reply alive while the app is in the background, then posts the reply
 * and stops itself. Started only when the activity stops mid-turn; never idles.
 */
class TurnService : Service(), ChatEngine.Listener {

    private val engine: ChatEngine get() = App.instance.engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifs.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = engine.conversation?.title ?: getString(R.string.app_name)
        startForeground(Notifs.ID_TURN, Notifs.turnInProgress(this, title, engine.conversation?.id), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!engine.isStreaming) {
            finish(notify = true)
            return START_NOT_STICKY
        }
        engine.addListener(this)
        return START_NOT_STICKY
    }

    override fun onStreamingChanged(streaming: Boolean) {
        if (!streaming) finish(notify = true)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        engine.stop()
        finish(notify = false)
    }

    private fun finish(notify: Boolean) {
        engine.removeListener(this)
        if (notify) {
            val reply = engine.messages.lastOrNull { it.role == Role.ASSISTANT }
            val preview = when {
                reply == null -> null
                reply.status == MessageStatus.ERROR -> reply.error ?: getString(R.string.went_wrong)
                else -> reply.content.take(300).ifBlank { getString(R.string.notif_done) }
            }
            if (preview != null) Notifs.replyReady(this, engine.conversation?.title ?: getString(R.string.app_name), preview, engine.conversation?.id)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onReset() {}
    override fun onMessageAdded(index: Int) {}
    override fun onMessageChanged(index: Int, streaming: Boolean) {}
    override fun onMessageRemoved(index: Int) {}

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, TurnService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TurnService::class.java))
        }
    }
}
