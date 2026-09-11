package io.github.kasecrab.razorback.ui.drawer

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.Conversation
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.ui.widget.Shapes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Conversations grouped by when they were last touched. */
class ChatListAdapter(
    private val onOpen: (Conversation) -> Unit,
    private val onMenu: (Conversation) -> Unit,
) : RecyclerView.Adapter<ChatListAdapter.Holder>() {

    /** A paired machine and what it has open, shown above the chats that live here. */
    class Remote(val machine: String, val connected: Boolean, val sessions: List<io.github.kasecrab.razorback.remote.Frames.Session>, val canStart: Boolean)

    var onRemoteOpen: ((io.github.kasecrab.razorback.remote.Frames.Session) -> Unit)? = null
    var onRemoteNew: (() -> Unit)? = null

    private sealed class Item {
        class Header(val label: String) : Item()
        class Row(val conv: Conversation) : Item()
        class Session(val session: io.github.kasecrab.razorback.remote.Frames.Session) : Item()
        class Note(val text: String) : Item()
        data object NewSession : Item()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    private val items = ArrayList<Item>()
    private var currentId: String? = null

    fun set(context: Context, list: List<Conversation>, current: String?, remote: Remote? = null) {
        currentId = current
        items.clear()
        if (remote != null) {
            items.add(Item.Header(remote.machine))
            if (remote.sessions.isEmpty()) {
                items.add(Item.Note(context.getString(if (remote.connected) R.string.remote_nothing_open else R.string.remote_offline)))
            }
            for (s in remote.sessions) items.add(Item.Session(s))
            // A window publishes the one session it has; only the daemon starts others.
            if (remote.canStart) items.add(Item.NewSession)
        }
        var group: String? = null
        val now = Calendar.getInstance()
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        for (c in list) {
            val g = if (c.pinned) context.getString(R.string.group_pinned) else groupOf(context, c.updatedAt, now, monthFmt)
            if (g != group) {
                items.add(Item.Header(g))
                group = g
            }
            items.add(Item.Row(c))
        }
        notifyDataSetChanged()
    }

    private fun groupOf(context: Context, at: Long, now: Calendar, monthFmt: SimpleDateFormat): String {
        val day = 24L * 60 * 60 * 1000
        val startOfToday = now.clone().let {
            it as Calendar
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
            it.timeInMillis
        }
        return when {
            at >= startOfToday -> context.getString(R.string.group_today)
            at >= startOfToday - day -> context.getString(R.string.group_yesterday)
            at >= startOfToday - 7 * day -> context.getString(R.string.group_week)
            at >= startOfToday - 30 * day -> context.getString(R.string.group_month)
            else -> monthFmt.format(Date(at))
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Header -> HEADER
        is Item.Row -> ROW
        is Item.Session -> SESSION
        is Item.Note -> NOTE
        Item.NewSession -> ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v: View = when (viewType) {
            HEADER -> HeaderView(parent.context)
            SESSION -> SessionView(parent.context)
            NOTE -> NoteView(parent.context)
            else -> RowView(parent.context)
        }
        v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.itemView.context.ui().host.refresh(holder.itemView)
        when (val item = items[position]) {
            is Item.Header -> (holder.itemView as HeaderView).text = item.label
            is Item.Row -> {
                val row = holder.itemView as RowView
                row.bind(item.conv, item.conv.id == currentId)
                row.setOnClickListener { onOpen(item.conv) }
                row.setOnLongClickListener {
                    onMenu(item.conv)
                    true
                }
            }
            is Item.Session -> {
                val row = holder.itemView as SessionView
                row.bind(item.session)
                row.setOnClickListener { onRemoteOpen?.invoke(item.session) }
                row.setOnLongClickListener(null)
            }
            is Item.Note -> (holder.itemView as NoteView).text = item.text
            Item.NewSession -> {
                val row = holder.itemView as RowView
                row.bindAction(holder.itemView.context.getString(R.string.remote_new_session))
                row.setOnClickListener { onRemoteNew?.invoke() }
                row.setOnLongClickListener(null)
            }
        }
    }

    /** A line under a header that is not a row: what the machine has to say about itself. */
    private class NoteView(context: Context) : TextView(context), Themed {
        init {
            typeface = Fonts.regular
            setPadding(dp(16), dp(6), dp(16), dp(6))
            onThemeChanged(context.appTheme)
        }

        override fun onThemeChanged(theme: Theme) {
            setTextColor(theme.textTertiary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
        }
    }

    /** A session on the machine: its name, and under it where it runs and whether it is running. */
    private class SessionView(context: Context) : android.widget.LinearLayout(context), Themed {
        private val title = TextView(context)
        private val meta = TextView(context)
        private var live = true

        init {
            orientation = VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isClickable = true
            isFocusable = true
            title.typeface = Fonts.regular
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            meta.typeface = Fonts.regular
            meta.maxLines = 1
            meta.ellipsize = android.text.TextUtils.TruncateAt.START
            addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(meta, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(1) })
            onThemeChanged(context.appTheme)
        }

        override fun performClick(): Boolean {
            val handled = super.performClick()
            if (handled) io.github.kasecrab.razorback.ui.core.Haptics.tick()
            return handled
        }

        fun bind(s: io.github.kasecrab.razorback.remote.Frames.Session) {
            live = s.live
            title.text = (s.name ?: s.title).ifBlank { s.cwd.substringAfterLast('/') }
            meta.text = if (s.live) s.cwd else context.getString(R.string.remote_not_running, s.cwd)
            onThemeChanged(context.appTheme)
        }

        override fun onThemeChanged(theme: Theme) {
            title.setTextColor(if (live) theme.textPrimary else theme.textSecondary)
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            meta.setTextColor(theme.textTertiary)
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
            background = Shapes.ripple(theme.accentSoft, null, dp(theme.radiusM))
        }
    }

    private class HeaderView(context: Context) : TextView(context), Themed {
        init {
            typeface = Fonts.medium
            setPadding(dp(20), dp(16), dp(20), dp(6))
            onThemeChanged(context.appTheme)
        }

        override fun onThemeChanged(theme: Theme) {
            setTextColor(theme.textTertiary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
        }
    }

    private class RowView(context: Context) : TextView(context), Themed {
        private var selected = false

        init {
            typeface = Fonts.regular
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isClickable = true
            isFocusable = true
            onThemeChanged(context.appTheme)
        }

        override fun performClick(): Boolean {
            val handled = super.performClick()
            if (handled) io.github.kasecrab.razorback.ui.core.Haptics.tick()
            return handled
        }

        private var action = false

        fun bind(c: Conversation, current: Boolean) {
            text = c.title
            selected = current
            action = false
            onThemeChanged(context.appTheme)
        }

        /** Something to do rather than something to open: drawn in the accent. */
        fun bindAction(label: String) {
            text = label
            selected = false
            action = true
            onThemeChanged(context.appTheme)
        }

        override fun onThemeChanged(theme: Theme) {
            setTextColor(if (action) theme.accent else theme.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            val shape = if (selected) Shapes.rounded(theme.accentSoft, dp(theme.radiusM)) else null
            background = Shapes.ripple(theme.accentSoft, shape, dp(theme.radiusM))
        }
    }

    private companion object {
        const val HEADER = 0
        const val ROW = 1
        const val SESSION = 2
        const val NOTE = 3
    }
}
