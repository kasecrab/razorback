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
import io.github.kasecrab.razorback.ui.core.Haptics
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
import io.github.kasecrab.razorback.ui.drawer.ChatListAdapter
import io.github.kasecrab.razorback.ui.drawer.DrawerPanel
import io.github.kasecrab.razorback.ui.settings.SettingsScreen
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.TopBar
import android.content.ClipData
import android.content.ClipboardManager
import android.view.Gravity
import io.github.kasecrab.razorback.model.Role
import io.github.kasecrab.razorback.model.Conversation
import io.github.kasecrab.razorback.ui.widget.InputSheet
import kotlinx.coroutines.Job
import android.app.Activity
import android.net.Uri
import android.widget.Toast
import io.github.kasecrab.razorback.media.CameraProvider
import io.github.kasecrab.razorback.media.ImagePrep
import io.github.kasecrab.razorback.media.Pick
import io.github.kasecrab.razorback.media.TextExtract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatScreen(context: Context) : Screen(context), ChatEngine.Listener {

    private val app = App.instance
    private val engine = app.engine
    private val onPref: (String) -> Unit = { if (it == Keys.MODEL.name || it == Keys.THINKING.name) refreshChips() }
    private val onCatalog: () -> Unit = {
        engine.fitThinking()
        refreshChips()
    }
    private var listJob: Job? = null
    /** A paired machine's sessions, kept up to date while the drawer is there to show them. */
    private val onRemote = object : io.github.kasecrab.razorback.remote.RemoteLink.Watcher {
        override fun onSessions(sessions: List<io.github.kasecrab.razorback.remote.Frames.Session>) = reloadConversations()
        override fun onMachine(machine: io.github.kasecrab.razorback.remote.Frames.Machine) = reloadConversations()
        override fun onLink() = reloadConversations()
        override fun onSessionStarted(session: String) = openRemote(session)
        override fun onTrouble(text: String) {
            if (drawer.isOpen) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
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
        bar.trailing.setOnClickListener { newChat() }
        // The drawer carries its own new-chat button; the bar's fades out as the drawer slides in.
        drawer.onFraction = { f ->
            bar.trailing.alpha = 1f - f
            bar.trailing.isClickable = f < 0.5f
        }
        bar.makeTitleClickable({ ModelPickerSheet(context).show() }, { context.nav.push(ModelBrowserScreen(context)) })
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

        composer.onSend = { text, pending -> send(text, pending) }
        composer.onAttach = { showAttachSheet() }
        composer.onVoiceMode = {
            if (io.github.kasecrab.razorback.ui.core.KeyNeeded.check(context, io.github.kasecrab.razorback.core.Secrets.DEEPGRAM)) {
                context.nav.push(io.github.kasecrab.razorback.ui.voice.VoiceScreen(context))
            }
        }
        composer.onStop = { engine.stop() }
        composer.thinkingChip.setOnClickListener { ThinkingLevelSheet(context).show() }
        composer.temporaryChip.setOnClickListener {
            val on = !engine.temporary
            Haptics.toggle(on)
            engine.newConversation()
            engine.temporary = on
        }
        column.addView(
            composer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(10), dp(4), dp(10), dp(8))
            },
        )

        panel.newChat.setOnClickListener {
            drawer.close()
            newChat()
        }
        panel.onOpen = {
            drawer.close()
            engine.open(it)
        }
        panel.onRemoteHub = {
            drawer.close()
            context.nav.push(io.github.kasecrab.razorback.ui.remote.RemoteHubScreen(context))
        }
        panel.onMenu = { showConversationMenu(it) }
        panel.search.onTextChanged = { reloadConversations() }
        panel.settings.setOnClickListener {
            drawer.close()
            context.nav.push(SettingsScreen(context))
        }
        drawer.content = column
        drawer.panel = panel
        drawer.addView(column)
        drawer.addView(panel)
        drawer.onOpenChanged = {
            context.ui().back.invalidate()
            if (it) {
                // The microphone belongs to the composer; it does not listen on through the sidebar.
                composer.dictation.stop()
                reloadConversations()
            }
        }
        addView(drawer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        refreshEmpty()
    }

    override fun onEnter() {
        context.ui().back.add(drawer, priority = 10)
        app.remote.add(onRemote)
        if (app.remote.paired) app.remote.start()
        engine.addListener(this)
        app.prefs.onChange(onPref)
        app.catalog.onChange(onCatalog)
        adapter.notifyDataSetChanged()
        composer.streaming = engine.isStreaming
        refreshEmpty()
        refreshChips()
        refreshTitle()
        reloadConversations()
        context.uiScope.launch { app.catalog.load() }
    }

    /** The session the machine just started opens by itself, wherever the person is in the app. */
    private fun openRemote(session: String) {
        if (context.nav.top is io.github.kasecrab.razorback.ui.remote.RemoteSessionScreen) return
        context.nav.push(io.github.kasecrab.razorback.ui.remote.RemoteSessionScreen(context, session, app.remote.startingIn.ifBlank { null }))
    }

    /** The paired machine's row for the sidebar, or nothing when there is no machine or a search is on. */
    private fun remoteGroup(): ChatListAdapter.Remote? {
        val remote = app.remote
        if (!remote.paired || panel.search.text.isNotBlank()) return null
        val host = remote.machine?.host ?: context.getString(R.string.remote)
        return ChatListAdapter.Remote(host, remote.connected, if (remote.connected) remote.sessions.count { it.live } else 0)
    }

    private fun reloadConversations() {
        listJob?.cancel()
        listJob = context.uiScope.launch {
            val convs = app.store.listConversations(panel.search.text)
            panel.setConversations(convs, engine.conversation?.id, remoteGroup())
        }
    }

    /** A new chat starts clean: whatever was typed or attached for the old one goes with it. */
    private fun newChat() {
        composer.clearDraft()
        engine.newConversation()
    }

    private fun refreshTitle() {
        bar.setSubtitle(if (engine.temporary) context.getString(R.string.temporary_chat) else engine.conversation?.title)
        composer.temporaryChip.active = engine.temporary
    }

    private fun showConversationMenu(conv: Conversation) {
        val sheet = ActionSheet(context)
        sheet.add(R.drawable.ic_star, context.getString(if (conv.pinned) R.string.action_unpin else R.string.action_pin)) {
            Haptics.toggle(!conv.pinned)
            engine.setPinned(conv, !conv.pinned)
        }
        sheet.add(R.drawable.ic_edit, context.getString(R.string.action_rename)) {
            InputSheet(context, context.getString(R.string.rename_chat), conv.title) { engine.rename(conv, it) }.show()
        }
        sheet.add(R.drawable.ic_trash, context.getString(R.string.action_delete), danger = true) { engine.deleteConversation(conv) }
        sheet.show()
    }

    /** Another screen on top means nobody is typing here: dictation ends, keeping what it heard. */
    override fun onPause() {
        composer.dictation.stop()
    }

    override fun onExit() {
        app.remote.remove(onRemote)
        composer.dictation.release()
        engine.removeListener(this)
        app.prefs.removeOnChange(onPref)
        app.catalog.removeOnChange(onCatalog)
        context.ui().back.remove(drawer)
    }

    private fun refreshChips() {
        val id = engine.model
        val info = app.catalog.find(id)
        bar.title.text = info?.shortName ?: id.substringAfter('/')
        val effective = io.github.kasecrab.razorback.model.Reasoning.effective(engine.thinking, info)
        composer.thinkingChip.text = effective?.label ?: io.github.kasecrab.razorback.model.ThinkingLevel.OFF.label
        composer.thinkingChip.active = effective != null
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

    private fun send(text: String, pending: List<AttachStrip.Pending>) {
        val images = ArrayList<String>()
        val blocks = StringBuilder()
        for (p in pending) {
            p.attachment?.let { images.add(it.path) }
            p.text?.let { blocks.append(TextExtract.wrap(it.name, it.text)).append("\n\n") }
        }
        val content = if (blocks.isEmpty()) text else blocks.toString() + text
        if (content.isBlank() && images.isEmpty()) return
        // Without a key the message stays in the composer and the way to the key is shown.
        if (!io.github.kasecrab.razorback.ui.core.KeyNeeded.check(context, io.github.kasecrab.razorback.core.Secrets.OPENROUTER)) {
            Haptics.reject()
            return
        }
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            context.ui().permissions.request(android.Manifest.permission.POST_NOTIFICATIONS) {}
        }
        engine.send(content, images)
    }

    private fun showAttachSheet() {
        val sheet = ActionSheet(context)
        sheet.add(R.drawable.ic_camera, context.getString(R.string.attach_camera)) { takePhoto() }
        sheet.add(R.drawable.ic_image, context.getString(R.string.attach_photos)) { pickPhotos() }
        sheet.add(R.drawable.ic_file, context.getString(R.string.attach_files)) { pickFile() }
        sheet.add(R.drawable.ic_edit, context.getString(R.string.attach_prompt)) {
            PromptPickerSheet(context) { text ->
                val e = composer.input
                val at = e.selectionStart.coerceAtLeast(0)
                e.text.insert(at, text)
                e.requestFocus()
            }.show()
        }
        sheet.show()
    }

    private fun takePhoto() {
        val capture = Pick.camera(context)
        context.ui().results.start(capture.intent) { code, _ ->
            if (code != Activity.RESULT_OK) return@start
            val file = CameraProvider.fileFor(context, capture.fileName)
            importAll(listOf { ImagePrep.importFile(context, file, "photo").also { file.delete() } })
        }
    }

    private fun pickPhotos() {
        context.ui().results.start(Pick.photos()) { code, data ->
            if (code != Activity.RESULT_OK || data == null) return@start
            val uris = ArrayList<Uri>()
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
            data.data?.let { if (uris.isEmpty()) uris.add(it) }
            importAll(uris.map { uri -> { ImagePrep.importUri(context, uri, TextExtract.displayName(context, uri)) } })
        }
    }

    private fun pickFile() {
        context.ui().results.start(Pick.files()) { code, data ->
            val uri = data?.data
            if (code != Activity.RESULT_OK || uri == null) return@start
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime.startsWith("image/")) {
                importAll(listOf { ImagePrep.importUri(context, uri, TextExtract.displayName(context, uri)) })
            } else {
                context.uiScope.launch {
                    val r = runCatching { withContext(Dispatchers.IO) { TextExtract.load(context, uri) } }
                    r.onSuccess {
                        composer.strip.add(AttachStrip.Pending(null, it))
                        Haptics.confirm()
                    }
                    r.onFailure {
                        Haptics.reject()
                        Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                    }
                    composer.updatePrimary()
                }
            }
        }
    }

    private fun importAll(jobs: List<() -> io.github.kasecrab.razorback.model.Attachment>) {
        context.uiScope.launch {
            for (job in jobs) {
                val r = runCatching { withContext(Dispatchers.IO) { job() } }
                r.onSuccess {
                    composer.strip.add(AttachStrip.Pending(it, null))
                    Haptics.confirm()
                }
                r.onFailure {
                    Haptics.reject()
                    Toast.makeText(context, context.getString(R.string.attach_failed, it.message ?: "image"), Toast.LENGTH_SHORT).show()
                }
            }
            composer.updatePrimary()
        }
    }

    private fun showMenu(index: Int) {
        val m = engine.messages.getOrNull(index) ?: return
        val sheet = ActionSheet(context)
        sheet.add(R.drawable.ic_copy, context.getString(R.string.action_copy)) {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("message", m.content))
            Haptics.confirm()
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
        if (streaming && isShown && context.ui().nav.top === this) Haptics.stream()
        follow()
    }

    override fun onMessageRemoved(index: Int) {
        adapter.notifyItemRemoved(index)
        refreshEmpty()
    }

    /** A reply that has just finished gives one light tick, so eyes can be elsewhere while it writes. */
    override fun onStreamingChanged(streaming: Boolean) {
        composer.streaming = streaming
        if (!streaming && isShown) {
            val last = engine.messages.lastOrNull()
            if (last != null && last.status == io.github.kasecrab.razorback.model.MessageStatus.ERROR) Haptics.reject() else Haptics.tick()
        }
    }

    override fun onConversationChanged() = refreshTitle()

    override fun onConversationsChanged() = reloadConversations()
}
