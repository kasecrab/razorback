package io.github.kasecrab.razorback.ui.drawer

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.Conversation
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Themed
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.SearchBox
import io.github.kasecrab.razorback.ui.widget.Shapes

/** Sidebar contents: search, chats grouped by date, and settings and a new chat along the bottom. */
class DrawerPanel(context: Context) : LinearLayout(context), Themed {

    val newChat = Chip(context)
    val settings = IconButton(context)
    val search = SearchBox(context)
    var onOpen: ((Conversation) -> Unit)? = null
    var onMenu: ((Conversation) -> Unit)? = null
    /** The paired machine's row above the chats was tapped. */
    var onRemoteHub: (() -> Unit)? = null

    private val header = FrameLayout(context)
    private val brand = TextView(context)
    private val list = RecyclerView(context)
    private val adapter = ChatListAdapter({ onOpen?.invoke(it) }, { onMenu?.invoke(it) })
    private val placeholder = TextView(context)
    private val footer = LinearLayout(context)

    init {
        orientation = VERTICAL
        // Taps between the rows must end here, not on the chat underneath.
        isClickable = true

        brand.typeface = Fonts.medium
        brand.setText(R.string.app_name)
        header.addView(brand, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(20) })
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        search.setHint(R.string.search_chats)
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), 0, dp(12), dp(8)) })

        val listHost = FrameLayout(context)
        list.layoutManager = LinearLayoutManager(context)
        adapter.onRemoteHub = { onRemoteHub?.invoke() }
        list.adapter = adapter
        list.itemAnimator = null
        list.clipToPadding = false
        list.setPadding(dp(8), 0, dp(8), dp(8))
        listHost.addView(list, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        placeholder.typeface = Fonts.regular
        placeholder.gravity = Gravity.CENTER
        placeholder.setText(R.string.drawer_no_chats)
        listHost.addView(placeholder, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(listHost, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        footer.orientation = HORIZONTAL
        footer.gravity = Gravity.CENTER_VERTICAL
        footer.setPadding(dp(12), dp(8), dp(12), dp(8))
        settings.iconRes = R.drawable.ic_settings
        settings.tone = IconButton.Tone.SECONDARY
        settings.contentDescription = context.getString(R.string.cd_settings)
        footer.addView(settings, LayoutParams(dp(44), dp(44)))
        footer.addView(View(context), LayoutParams(0, 0, 1f))
        newChat.style = Chip.Style.ACCENT
        newChat.leadingIcon = R.drawable.ic_new_chat
        newChat.setText(R.string.new_chat)
        newChat.minimumHeight = dp(40)
        newChat.setPadding(dp(14), 0, dp(16), 0)
        footer.addView(newChat, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        onThemeChanged(context.appTheme)
    }

    fun setConversations(convs: List<Conversation>, currentId: String?, remote: ChatListAdapter.Remote? = null) {
        adapter.set(context, convs, currentId, remote)
        placeholder.visibility = if (convs.isEmpty() && remote == null) View.VISIBLE else View.GONE
        placeholder.setText(if (search.text.isBlank()) R.string.drawer_no_chats else R.string.no_models)
    }

    fun setInsets(top: Int, bottom: Int) {
        setPadding(0, top, 0, bottom)
    }

    override fun onThemeChanged(theme: Theme) {
        background = Shapes.solid(theme.surfaceElevated)
        brand.setTextColor(theme.textPrimary)
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        placeholder.setTextColor(theme.textTertiary)
        placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
    }
}
