package io.github.kasecrab.razorback.ui.remote

import android.content.Context
import android.widget.Toast
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.InputSheet

/**
 * Starting a session on the paired machine, from anywhere: the machine's offered roots
 * and the directories its sessions already run in are one tap each, anything else is
 * typed. The session that starts opens by itself once the machine names it.
 */
object RemoteStart {

    fun ask(context: Context, prefer: String? = null) {
        val remote = App.instance.remote
        if (!remote.ready()) {
            Haptics.reject()
            Toast.makeText(context, R.string.remote_offline, Toast.LENGTH_SHORT).show()
            return
        }
        val host = remote.machine?.host ?: context.getString(R.string.remote)
        val places = remote.startPlaces()
        val typed = { prefill: String ->
            InputSheet(context, context.getString(R.string.remote_new_dir, host), prefill) { cwd -> start(context, cwd) }.show()
        }
        if (prefer != null) {
            start(context, prefer)
            return
        }
        if (places.size <= 1) {
            typed(places.firstOrNull() ?: "")
            return
        }
        val sheet = ActionSheet(context).header(context.getString(R.string.remote_new_session), context.getString(R.string.remote_new_dir, host))
        for (place in places.take(8)) sheet.add(R.drawable.ic_file, place) { start(context, place) }
        sheet.add(R.drawable.ic_edit, context.getString(R.string.remote_other_dir)) { typed(places.first()) }
        sheet.show()
    }

    private fun start(context: Context, cwd: String) {
        Haptics.confirm()
        Toast.makeText(context, context.getString(R.string.remote_starting_in, cwd), Toast.LENGTH_SHORT).show()
        App.instance.remote.newSession(cwd)
    }
}
