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
import io.github.kasecrab.razorback.ui.widget.Avatar
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.Shapes
import io.github.kasecrab.razorback.ui.widget.TextField

/** Sidebar contents: search, chats grouped by date, the person and settings below. */
class DrawerPanel(context: Context) : LinearLayout(context), Themed {

    val newChat = IconButton(context)
    val settings = IconButton(context)
    val search = TextField(context)
    var onOpen: ((Conversation) -> Unit)? = null
    var onMenu: ((Conversation) -> Unit)? = null

    private val header = FrameLayout(context)
    private val brand = TextView(context)
    private val list = RecyclerView(context)
    private val adapter = ChatListAdapter({ onOpen?.invoke(it) }, { onMenu?.invoke(it) })
    private val placeholder = TextView(context)
    private val footer = LinearLayout(context)
    private val avatar = Avatar(context)
    private val name = TextView(context)

    init {
        orientation = VERTICAL
        // Taps between the rows must end here, not on the chat underneath.
        isClickable = true

        brand.typeface = Fonts.medium
        brand.setText(R.string.app_name)
        header.addView(brand, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(20) })
        newChat.iconRes = R.drawable.ic_new_chat
        newChat.tone = IconButton.Tone.PRIMARY
        newChat.contentDescription = context.getString(R.string.cd_new_chat)
        header.addView(newChat, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(8) })
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        search.setHint(R.string.search_chats)
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), 0, dp(12), dp(4)) })

        val listHost = FrameLayout(context)
        list.layoutManager = LinearLayoutManager(context)
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
        footer.setPadding(dp(16), dp(8), dp(8), dp(8))
        footer.addView(avatar, LayoutParams(dp(32), dp(32)))
        name.typeface = Fonts.medium
        name.setText(R.string.you)
        footer.addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        settings.iconRes = R.drawable.ic_settings
        settings.tone = IconButton.Tone.SECONDARY
        settings.contentDescription = context.getString(R.string.cd_settings)
        footer.addView(settings, LayoutParams(dp(44), dp(44)))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        onThemeChanged(context.appTheme)
    }

    fun setConversations(convs: List<Conversation>, currentId: String?) {
        adapter.set(context, convs, currentId)
        placeholder.visibility = if (convs.isEmpty()) View.VISIBLE else View.GONE
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
        name.setTextColor(theme.textPrimary)
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
    }
}
