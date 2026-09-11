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
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.ui.widget.Shapes
import android.widget.ImageView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Conversations grouped by when they were last touched. */
class ChatListAdapter(
    private val onOpen: (Conversation) -> Unit,
    private val onMenu: (Conversation) -> Unit,
) : RecyclerView.Adapter<ChatListAdapter.Holder>() {

    /** The paired machine, as one row above the chats that live here. */
    class Remote(val machine: String, val connected: Boolean, val running: Int)

    var onRemoteHub: (() -> Unit)? = null

    private sealed class Item {
        class Header(val label: String) : Item()
        class Row(val conv: Conversation) : Item()
        class Machine(val remote: Remote) : Item()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    private val items = ArrayList<Item>()
    private var currentId: String? = null

    fun set(context: Context, list: List<Conversation>, current: String?, remote: Remote? = null) {
        currentId = current
        items.clear()
        if (remote != null) items.add(Item.Machine(remote))
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
        is Item.Machine -> MACHINE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v: View = when (viewType) {
            HEADER -> HeaderView(parent.context)
            MACHINE -> MachineView(parent.context)
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
            is Item.Machine -> {
                val row = holder.itemView as MachineView
                row.bind(item.remote)
                row.setOnClickListener { onRemoteHub?.invoke() }
            }
        }
    }

    /** The machine on the other end of the link: a laptop glyph, its name, and whether it answers. */
    private class MachineView(context: Context) : android.widget.LinearLayout(context), Themed {
        private val glyph = ImageView(context)
        private val name = TextView(context)
        private val meta = TextView(context)
        private val chevron = ImageView(context)
        private var connected = false

        init {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            glyph.scaleType = ImageView.ScaleType.CENTER
            addView(glyph, LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
            val texts = android.widget.LinearLayout(context)
            texts.orientation = VERTICAL
            name.typeface = Fonts.medium
            name.maxLines = 1
            name.ellipsize = android.text.TextUtils.TruncateAt.END
            meta.typeface = Fonts.regular
            meta.maxLines = 1
            texts.addView(name)
            texts.addView(meta)
            addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            chevron.scaleType = ImageView.ScaleType.CENTER
            addView(chevron, LayoutParams(dp(24), dp(24)))
            onThemeChanged(context.appTheme)
        }

        override fun performClick(): Boolean {
            val handled = super.performClick()
            if (handled) io.github.kasecrab.razorback.ui.core.Haptics.tick()
            return handled
        }

        fun bind(r: Remote) {
            connected = r.connected
            name.text = r.machine
            meta.text = if (r.connected) {
                context.resources.getQuantityString(R.plurals.remote_connected_running, r.running, r.running)
            } else {
                context.getString(R.string.remote_offline)
            }
            onThemeChanged(context.appTheme)
        }

        override fun onThemeChanged(theme: Theme) {
            background = Shapes.ripple(theme.accentSoft, Shapes.rounded(theme.surface, dp(theme.radiusM)), dp(theme.radiusM))
            glyph.setImageDrawable(context.icon(R.drawable.ic_laptop, if (connected) theme.ok else theme.textTertiary))
            glyph.background = Shapes.rounded(theme.bg, dp(theme.radiusS))
            chevron.setImageDrawable(context.icon(R.drawable.ic_chevron_right, theme.textTertiary))
            name.setTextColor(theme.textPrimary)
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            meta.setTextColor(if (connected) theme.ok else theme.textTertiary)
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
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
        const val MACHINE = 2
    }
}
