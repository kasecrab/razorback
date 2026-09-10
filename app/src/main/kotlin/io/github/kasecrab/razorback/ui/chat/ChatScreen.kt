package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.chat.ChatEngine
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.models.ModelBrowserScreen
import kotlinx.coroutines.launch
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.ui.drawer.DrawerHost
import io.github.kasecrab.razorback.ui.drawer.DrawerPanel
import io.github.kasecrab.razorback.ui.settings.SettingsScreen
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.TopBar
import android.content.ClipData
import android.content.ClipboardManager
import android.view.Gravity
import io.github.kasecrab.razorback.model.Role

class ChatScreen(context: Context) : Screen(context), ChatEngine.Listener {

    private val app = App.instance
    private val engine = app.engine
    private val onPref: (String) -> Unit = { if (it == Keys.MODEL.name || it == Keys.THINKING.name) refreshChips() }
    private val onCatalog: () -> Unit = { refreshChips() }
    private val drawer = DrawerHost(context)
    private val panel = DrawerPanel(context)
    private val column = LinearLayout(context)
    private val bar = TopBar(context)
    private val content = FrameLayout(context)
    private val empty = EmptyState(context)
    private val list = RecyclerView(context)
    private val adapter = ChatAdapter(engine.messages)
    private val layout = LinearLayoutManager(context).apply { stackFromEnd = true }
    private val pill = IconButton(context)
    val composer = Composer(context)
    private var following = true
        set(value) {
            if (field == value) return
            field = value
            pill.animate().alpha(if (value) 0f else 1f).setDuration(context.appTheme.durShort).start()
            pill.isClickable = !value
        }

    init {
        column.orientation = LinearLayout.VERTICAL
        bar.set(
            R.drawable.ic_menu,
            context.getString(R.string.cd_menu),
            context.getString(R.string.app_name),
            R.drawable.ic_new_chat,
            context.getString(R.string.cd_new_chat),
        )
        bar.leading.setOnClickListener { drawer.open() }
        bar.trailing.setOnClickListener { engine.newConversation() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.layoutManager = layout
        list.adapter = adapter
        list.itemAnimator = null
        list.setHasFixedSize(false)
        list.clipToPadding = false
        list.setPadding(0, dp(8), 0, dp(8))
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy < 0 && rv.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) following = false
                if (!rv.canScrollVertically(1)) following = true
            }
        })
        content.addView(list, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(empty, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pill.iconRes = R.drawable.ic_chevron_down
        pill.filled = true
        pill.tone = IconButton.Tone.PRIMARY
        pill.contentDescription = context.getString(R.string.cd_scroll_bottom)
        pill.alpha = 0f
        pill.isClickable = false
        pill.setOnClickListener {
            list.smoothScrollToPosition(adapter.itemCount)
            following = true
        }
        content.addView(pill, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, dp(16), dp(12)) })
        adapter.onMenu = { showMenu(it) }
        column.addView(content, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        composer.onSend = { engine.send(it) }
        composer.onStop = { engine.stop() }
        composer.modelChip.setOnClickListener { ModelPickerSheet(context).show() }
        composer.modelChip.setOnLongClickListener {
            context.nav.push(ModelBrowserScreen(context))
            true
        }
        composer.thinkingChip.setOnClickListener { ThinkingLevelSheet(context).show() }
        column.addView(
            composer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(10), dp(4), dp(10), dp(8))
            },
        )

        panel.newChat.setOnClickListener {
            drawer.close()
            engine.newConversation()
        }
        panel.settings.setOnClickListener {
            drawer.close()
            context.nav.push(SettingsScreen(context))
        }
        drawer.content = column
        drawer.panel = panel
        drawer.addView(column)
        drawer.addView(panel)
        drawer.onOpenChanged = { context.ui().back.invalidate() }
        addView(drawer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refreshEmpty()
    }

    override fun onEnter() {
        context.ui().back.add(drawer, priority = 10)
        engine.addListener(this)
        app.prefs.onChange(onPref)
        app.catalog.onChange(onCatalog)
        adapter.notifyDataSetChanged()
        composer.streaming = engine.isStreaming
        refreshEmpty()
        refreshChips()
        context.uiScope.launch { app.catalog.load() }
    }

    override fun onExit() {
        engine.removeListener(this)
        app.prefs.removeOnChange(onPref)
        app.catalog.removeOnChange(onCatalog)
        context.ui().back.remove(drawer)
    }

    private fun refreshChips() {
        val id = engine.model
        val info = app.catalog.find(id)
        composer.modelChip.text = info?.shortName ?: id.substringAfter('/')
        composer.thinkingChip.text = engine.thinking.label
        composer.thinkingChip.active = engine.thinking != io.github.kasecrab.razorback.model.ThinkingLevel.OFF
        composer.thinkingChip.visibility = if (info == null || info.supportsReasoning) View.VISIBLE else View.GONE
    }

    override fun onInsetsChanged(top: Int, bottom: Int, left: Int, right: Int) {
        column.setPadding(left, top, right, bottom)
        panel.setInsets(top, bottom)
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        column.setBackgroundColor(theme.bg)
    }

    private fun showMenu(index: Int) {
        val m = engine.messages.getOrNull(index) ?: return
        val sheet = ActionSheet(context)
        sheet.add(R.drawable.ic_copy, context.getString(R.string.action_copy)) {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("message", m.content))
        }
        if (m.role == Role.USER && !engine.isStreaming) {
            sheet.add(R.drawable.ic_edit, context.getString(R.string.action_edit_resend)) {
                composer.input.setText(m.content)
                composer.input.setSelection(m.content.length)
                composer.input.requestFocus()
                engine.truncateFrom(index)
            }
        }
        if (m.role == Role.ASSISTANT && index == engine.messages.size - 1 && !engine.isStreaming) {
            sheet.add(R.drawable.ic_refresh, context.getString(R.string.action_regenerate)) { engine.regenerate() }
        }
        if (!engine.isStreaming) {
            sheet.add(R.drawable.ic_trash, context.getString(R.string.action_delete), danger = true) { engine.delete(index) }
        }
        sheet.show()
    }

    private fun refreshEmpty() {
        empty.visibility = if (engine.messages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun follow() {
        if (following) list.scrollToPosition(adapter.itemCount - 1)
    }

    // ChatEngine.Listener

    override fun onReset() {
        adapter.notifyDataSetChanged()
        following = true
        refreshEmpty()
    }

    override fun onMessageAdded(index: Int) {
        adapter.notifyItemInserted(index)
        following = true
        refreshEmpty()
        follow()
    }

    override fun onMessageChanged(index: Int, streaming: Boolean) {
        if (streaming) adapter.notifyItemChanged(index, ChatAdapter.STREAM) else adapter.notifyItemChanged(index)
        follow()
    }

    override fun onMessageRemoved(index: Int) {
        adapter.notifyItemRemoved(index)
        refreshEmpty()
    }

    override fun onStreamingChanged(streaming: Boolean) {
        composer.streaming = streaming
    }
}
