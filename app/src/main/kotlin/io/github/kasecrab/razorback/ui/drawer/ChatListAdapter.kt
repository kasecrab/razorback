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

    private sealed class Item {
        class Header(val label: String) : Item()
        class Row(val conv: Conversation) : Item()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    private val items = ArrayList<Item>()
    private var currentId: String? = null

    fun set(context: Context, list: List<Conversation>, current: String?) {
        currentId = current
        items.clear()
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

    override fun getItemViewType(position: Int): Int = if (items[position] is Item.Header) HEADER else ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v: View = if (viewType == HEADER) HeaderView(parent.context) else RowView(parent.context)
        v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
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

        fun bind(c: Conversation, current: Boolean) {
            text = c.title
            selected = current
            onThemeChanged(context.appTheme)
        }

        override fun onThemeChanged(theme: Theme) {
            setTextColor(theme.textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            val shape = if (selected) Shapes.rounded(theme.accentSoft, dp(theme.radiusM)) else null
            background = Shapes.ripple(theme.accentSoft, shape, dp(theme.radiusM))
        }
    }

    private companion object {
        const val HEADER = 0
        const val ROW = 1
    }
}
