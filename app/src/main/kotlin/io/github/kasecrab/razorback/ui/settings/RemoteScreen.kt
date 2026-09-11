package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.remote.Codes
import io.github.kasecrab.razorback.remote.Frames
import io.github.kasecrab.razorback.remote.RemoteLink
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Keyboard
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.TextField
import io.github.kasecrab.razorback.ui.widget.TopBar

/**
 * The machine this phone is paired with, and the way to pair one by hand when the
 * camera will not read the QR: the relay's address and the code, typed.
 */
class RemoteScreen(context: Context) : Screen(context), RemoteLink.Watcher {

    private val app = App.instance
    private val link = app.remote
    private val bar = TopBar(context)
    private val status = Caption(context)
    private val url = TextField(context)
    private val code = TextField(context)
    private val pair = Chip(context)
    private val forget = Chip(context)
    private val trouble = Caption(context)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.remote))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(32))

        list.addView(SectionHeader(context).apply { setText(R.string.remote) })
        status.setPadding(dp(16), 0, dp(16), dp(8))
        list.addView(status)
        trouble.tone = Caption.Tone.DANGER
        trouble.setPadding(dp(16), 0, dp(16), dp(8))
        trouble.visibility = View.GONE
        list.addView(trouble)
        forget.setText(R.string.remote_forget)
        forget.setOnClickListener {
            ActionSheet(context)
                .add(R.drawable.ic_trash, context.getString(R.string.remote_forget), danger = true) {
                    link.forget()
                    trouble.visibility = View.GONE
                    sync()
                }
                .show()
        }
        list.addView(forget, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply { setMargins(dp(16), dp(4), dp(16), dp(8)) })

        list.addView(SectionHeader(context).apply { setText(R.string.remote_pair) })
        val hint = Caption(context)
        hint.setText(R.string.remote_hint)
        hint.setPadding(dp(16), 0, dp(16), dp(12))
        list.addView(hint)
        url.setHint(R.string.remote_url_hint)
        url.text = app.prefs[Keys.RELAY_URL]
        list.addView(url, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), 0, dp(16), dp(8)) })
        code.setHint(R.string.remote_code_hint)
        list.addView(code, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), 0, dp(16), dp(8)) })
        val actions = LinearLayout(context)
        actions.gravity = Gravity.END
        pair.style = Chip.Style.ACCENT
        pair.leadingIcon = R.drawable.ic_check
        pair.setText(R.string.remote_pair)
        pair.setOnClickListener { pairTyped() }
        actions.addView(pair, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))
        list.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), 0, dp(16), 0) })

        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        sync()
    }

    override fun onEnter() {
        link.add(this)
        sync()
    }

    override fun onExit() {
        link.remove(this)
    }

    private fun pairTyped() {
        Keyboard.hideAll(context)
        val relay = url.text.trim().trimEnd('/')
        val typed = code.text
        val plainHost = relay.startsWith("https://") || relay.startsWith("http://localhost") || relay.startsWith("http://127.0.0.1")
        if (!plainHost) {
            Haptics.reject()
            fail(context.getString(R.string.remote_bad_url))
            return
        }
        if (Codes.parse(typed) == null || !link.pair(relay, typed)) {
            Haptics.reject()
            fail(context.getString(R.string.remote_bad_code))
            return
        }
        Haptics.confirm()
        code.text = ""
        trouble.visibility = View.GONE
        sync()
    }

    private fun fail(text: String) {
        trouble.text = text
        trouble.visibility = View.VISIBLE
    }

    private fun sync() {
        val paired = link.paired
        status.text = if (paired) {
            context.getString(R.string.remote_paired_with, link.machine?.host ?: app.prefs[Keys.RELAY_URL])
        } else {
            context.getString(R.string.remote_not_paired)
        }
        forget.visibility = if (paired) View.VISIBLE else View.GONE
    }

    override fun onMachine(machine: Frames.Machine) = sync()

    override fun onTrouble(text: String) = fail(text)
}
