package io.github.kasecrab.razorback.ui.remote

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.remote.Frames
import io.github.kasecrab.razorback.remote.RemoteLink
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.settings.RemoteScreen
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.InputSheet
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.Shapes
import io.github.kasecrab.razorback.ui.widget.TopBar

/**
 * The paired machine as a page: whether it is reachable and what it is, how many
 * sessions it has and how many run, where they run, the sessions themselves, and a
 * button to start another. Everything a person needs to pick a session or begin one,
 * and nothing about the protocol underneath.
 */
class RemoteHubScreen(context: Context) : Screen(context), RemoteLink.Watcher {

    private val app = App.instance
    private val link = app.remote
    private val bar = TopBar(context)
    private val status = StatusCard(context)
    private val sessionsTile = Tile(context)
    private val runningTile = Tile(context)
    private val sessions = LinearLayout(context)
    private val empty = Caption(context)
    private val start = Chip(context)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.remote), R.drawable.ic_settings, context.getString(R.string.remote))
        bar.leading.setOnClickListener { context.nav.pop() }
        bar.trailing.setOnClickListener { context.nav.push(RemoteScreen(context)) }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(96))
        list.addView(status, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), dp(4), dp(16), 0) })

        val tiles = LinearLayout(context)
        tiles.orientation = LinearLayout.HORIZONTAL
        tiles.addView(sessionsTile, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        tiles.addView(runningTile, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        list.addView(tiles, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), dp(12), dp(16), 0) })

        list.addView(SectionHeader(context).apply { setText(R.string.remote_sessions) })
        empty.setPadding(dp(16), 0, dp(16), dp(8))
        list.addView(empty)
        sessions.orientation = LinearLayout.VERTICAL
        list.addView(sessions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val host = FrameLayout(context)
        host.addView(scroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        start.style = Chip.Style.ACCENT
        start.leadingIcon = R.drawable.ic_plus
        start.setText(R.string.remote_new_session)
        start.minimumHeight = dp(48)
        start.setPadding(dp(18), 0, dp(22), 0)
        start.setOnClickListener { RemoteStart.ask(context) }
        host.addView(start, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48), Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, dp(20), dp(20)) })
        column.addView(host, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        render()
    }

    /** While this page is open the machine is asked for its list now and then, so "connected" stays true to life. */
    private val probe = object : Runnable {
        override fun run() {
            link.probe()
            postDelayed(this, PROBE_MS)
        }
    }

    override fun onEnter() {
        link.add(this)
        if (link.paired) link.start()
        removeCallbacks(probe)
        post(probe)
        render()
    }

    override fun onResume() {
        link.probe()
        render()
    }

    override fun onExit() {
        removeCallbacks(probe)
        link.remove(this)
    }

    override fun onSessions(sessions: List<Frames.Session>) = render()
    override fun onMachine(machine: Frames.Machine) = render()
    override fun onLink() = render()
    override fun onTrouble(text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun render() {
        val machine = link.machine
        val connected = link.connected
        // A machine that is away runs nothing, whatever its last list said.
        val all = if (connected) link.sessions else link.sessions.map { it.copy(live = false) }
        val live = all.filter { it.live }
        bar.title.text = machine?.host ?: context.getString(R.string.remote)
        status.set(connected, machine)
        sessionsTile.set(all.size.toString(), context.getString(R.string.remote_tile_sessions))
        runningTile.set(live.size.toString(), context.getString(R.string.remote_tile_running))
        start.visibility = if (link.canStart && connected) View.VISIBLE else View.GONE

        sessions.removeAllViews()
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        empty.setText(if (connected) R.string.remote_nothing_open else R.string.remote_offline)
        for (s in all.sortedWith(compareByDescending<Frames.Session> { it.live }.thenByDescending { it.startedMs })) {
            val card = SessionCard(context)
            card.set(s)
            card.setOnClickListener {
                Haptics.tick()
                context.nav.push(RemoteSessionScreen(context, s.id))
            }
            card.setOnLongClickListener {
                menu(s)
                true
            }
            sessions.addView(card, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), 0, dp(16), dp(8)) })
        }
    }

    private fun menu(s: Frames.Session) {
        ActionSheet(context)
            .add(R.drawable.ic_edit, context.getString(R.string.action_rename)) {
                InputSheet(context, context.getString(R.string.action_rename), s.name ?: s.title) { name -> link.rename(s.id, name) }.show()
            }
            .add(R.drawable.ic_copy, context.getString(R.string.copy_session_id)) {
                context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("session", s.id))
                Haptics.confirm()
            }
            .show()
    }

    private companion object {
        const val PROBE_MS = 60_000L
    }

    /** Reachable or not, and what is on the other end. */
    private class StatusCard(context: Context) : LinearLayout(context), Themed {
        private val glyph = ImageView(context)
        private val title = TextView(context)
        private val meta = TextView(context)
        private var connected = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            glyph.scaleType = ImageView.ScaleType.CENTER
            addView(glyph, LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
            val texts = LinearLayout(context)
            texts.orientation = VERTICAL
            title.typeface = Fonts.medium
            meta.typeface = Fonts.regular
            meta.maxLines = 2
            texts.addView(title)
            texts.addView(meta, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
            addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            onThemeChanged(context.appTheme)
        }

        fun set(ready: Boolean, machine: Frames.Machine?) {
            connected = ready
            title.text = context.getString(if (ready) R.string.remote_connected else R.string.remote_offline)
            val parts = ArrayList<String>(3)
            if (machine != null) {
                parts.add(machine.os)
                if (machine.version.isNotBlank()) parts.add("ah " + machine.version)
                parts.add(context.getString(if (machine.holder == "daemon") R.string.remote_holder_daemon else R.string.remote_holder_window))
            } else {
                parts.add(context.getString(R.string.remote_waiting_hello))
            }
            meta.text = parts.joinToString("  ·  ")
            onThemeChanged(context.appTheme)
        }

        override fun onThemeChanged(theme: Theme) {
            background = Shapes.rounded(theme.surface, dp(theme.radiusM))
            glyph.setImageDrawable(context.icon(R.drawable.ic_laptop, if (connected) theme.ok else theme.textTertiary))
            glyph.background = Shapes.rounded(theme.bg, dp(theme.radiusS))
            title.setTextColor(if (connected) theme.ok else theme.textSecondary)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            meta.setTextColor(theme.textSecondary)
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        }
    }

    private class Tile(context: Context) : LinearLayout(context), Themed {
        private val value = TextView(context)
        private val label = TextView(context)

        init {
            orientation = VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            value.typeface = Fonts.medium
            label.typeface = Fonts.regular
            addView(value)
            addView(label)
            onThemeChanged(context.appTheme)
        }

        fun set(v: String, l: String) {
            value.text = v
            label.text = l
        }

        override fun onThemeChanged(theme: Theme) {
            background = Shapes.rounded(theme.surface, dp(theme.radiusM))
            value.setTextColor(theme.textPrimary)
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
            label.setTextColor(theme.textSecondary)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        }
    }

    /** One session: name, whether it runs and where, and how old it is. */
    private class SessionCard(context: Context) : LinearLayout(context), Themed {
        private val glyph = ImageView(context)
        private val title = TextView(context)
        private val meta = TextView(context)
        private val age = TextView(context)
        private var live = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            glyph.scaleType = ImageView.ScaleType.CENTER
            addView(glyph, LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
            val texts = LinearLayout(context)
            texts.orientation = VERTICAL
            title.typeface = Fonts.regular
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            meta.typeface = Fonts.regular
            meta.maxLines = 1
            meta.ellipsize = android.text.TextUtils.TruncateAt.START
            texts.addView(title)
            texts.addView(meta, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
            addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            age.typeface = Fonts.regular
            addView(age, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
            onThemeChanged(context.appTheme)
        }

        override fun performClick(): Boolean {
            val handled = super.performClick()
            if (handled) Haptics.tick()
            return handled
        }

        fun set(s: Frames.Session) {
            live = s.live
            title.text = (s.name ?: s.title).ifBlank { s.cwd.substringAfterLast('/') }
            val state = context.getString(if (s.live) R.string.remote_running else R.string.remote_stopped)
            meta.text = "$state  ·  ${shortPath(s.cwd)}"
            age.text = ago(s.startedMs)
            onThemeChanged(context.appTheme)
        }

        /** The last two steps of a path: enough to tell sessions apart without a line of slashes. */
        private fun shortPath(path: String): String {
            val parts = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
            return if (parts.size <= 2) path else "…/" + parts.takeLast(2).joinToString("/")
        }

        private fun ago(startedMs: Long): String {
            if (startedMs <= 0L) return ""
            val m = (System.currentTimeMillis() - startedMs) / 60_000
            return when {
                m < 2 -> context.getString(R.string.age_now)
                m < 60 -> "${m}m"
                m < 48 * 60 -> "${m / 60}h"
                else -> "${m / (24 * 60)}d"
            }
        }

        override fun onThemeChanged(theme: Theme) {
            background = Shapes.ripple(theme.accentSoft, Shapes.rounded(theme.surface, dp(theme.radiusM)), dp(theme.radiusM))
            glyph.setImageDrawable(context.icon(if (live) R.drawable.ic_waveform else R.drawable.ic_file, if (live) theme.ok else theme.textTertiary))
            glyph.background = Shapes.rounded(theme.bg, dp(theme.radiusS))
            title.setTextColor(theme.textPrimary)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            meta.setTextColor(if (live) theme.ok else theme.textTertiary)
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
            age.setTextColor(theme.textTertiary)
            age.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        }
    }
}
