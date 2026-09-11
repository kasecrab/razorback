package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.KeyChecks
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.provider.openrouter.AccountInfo
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterAccount
import io.github.kasecrab.razorback.tools.search.BraveSearch
import io.github.kasecrab.razorback.tools.search.ExaSearch
import io.github.kasecrab.razorback.ui.core.Keyboard
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.IconButton
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.TextField
import io.github.kasecrab.razorback.ui.widget.TopBar
import io.github.kasecrab.razorback.voice.DeepgramAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * One field per vendor key. Typing only stages a key: Save writes the keys that changed and
 * asks their vendors about those keys alone, so an untouched key is never checked twice, and
 * a key that was accepted keeps its date until someone edits it or asks for a fresh check.
 */
class ProvidersScreen(context: Context) : Screen(context) {

    private val prefs = App.instance.prefs
    private val secrets = App.instance.secrets
    private val checks = KeyChecks(prefs)
    private val bar = TopBar(context)
    private val list = LinearLayout(context)
    private val saveBar = LinearLayout(context)
    private val saveEdge = View(context)
    private val rows = ArrayList<KeyRow>(4)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.providers))
        bar.leading.setOnClickListener { if (!onBack()) context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        buildSaveBar()

        header(R.string.openrouter)
        rows.add(KeyRow(Secrets.OPENROUTER, R.string.key_hint_openrouter, null) { describe(OpenRouterAccount.fetch(it)) })

        header(R.string.deepgram)
        rows.add(KeyRow(Secrets.DEEPGRAM, R.string.key_hint_generic, null) { DeepgramAccount.check(it) })

        header(R.string.web_search)
        val toggle = SwitchRow(context)
        toggle.set(context.getString(R.string.web_search_toggle), context.getString(R.string.web_search_toggle_hint), prefs[Keys.WEB_SEARCH]) {
            prefs[Keys.WEB_SEARCH] = it
        }
        list.addView(toggle, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        caption(R.string.search_default_hint)
        caption(R.string.brave)
        rows.add(KeyRow(Secrets.BRAVE, R.string.key_hint_generic, Secrets.BRAVE) { searched(BraveSearch.search(it, "razorback", 1).size) })
        caption(R.string.exa)
        rows.add(KeyRow(Secrets.EXA, R.string.key_hint_generic, Secrets.EXA) { searched(ExaSearch.search(it, "razorback", 1).size) })
        syncDefault()
        syncSaveBar()
    }

    /** Leaving with a key typed but not written asks what to do with it. */
    override fun onBack(): Boolean {
        if (rows.none { it.dirty }) return false
        ActionSheet(context)
            .add(R.drawable.ic_check, context.getString(R.string.save)) { saveAll() }
            .add(R.drawable.ic_close, context.getString(R.string.discard_changes), danger = true) {
                discardAll()
                context.nav.pop()
            }
            .show()
        return true
    }

    override fun onExit() {
        for (r in rows) r.cancel()
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        saveBar.setBackgroundColor(theme.surface)
        saveEdge.setBackgroundColor(theme.outline)
    }

    private fun buildSaveBar() {
        saveBar.orientation = LinearLayout.VERTICAL
        saveBar.isClickable = true
        saveBar.visibility = View.GONE
        saveBar.addView(saveEdge, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1)))
        val actions = LinearLayout(context)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER_VERTICAL
        actions.setPadding(dp(16), dp(10), dp(16), dp(10))
        val note = Caption(context)
        note.setText(R.string.keys_unsaved)
        actions.addView(note, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val discard = Chip(context)
        discard.style = Chip.Style.PLAIN
        discard.setText(R.string.discard)
        discard.setOnClickListener { discardAll() }
        actions.addView(discard, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(8) })
        val save = Chip(context)
        save.style = Chip.Style.ACCENT
        save.leadingIcon = R.drawable.ic_check
        save.setText(R.string.save)
        save.setOnClickListener { saveAll() }
        actions.addView(save, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))
        saveBar.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(saveBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    /** Writes every key that changed, then checks those keys and no others. */
    private fun saveAll() {
        Keyboard.hideAll(context)
        for (r in rows) if (r.save()) r.check()
        syncSaveBar()
    }

    private fun discardAll() {
        Keyboard.hideAll(context)
        for (r in rows) r.revert()
        syncSaveBar()
    }

    private fun syncSaveBar() {
        val dirty = rows.any { it.dirty }
        saveBar.visibility = if (dirty) View.VISIBLE else View.GONE
        // The bar floats over the list, so leave it room at the end of the scroll.
        list.setPadding(0, 0, 0, if (dirty) dp(96) else dp(32))
    }

    private fun syncDefault() {
        val chosen = prefs[Keys.SEARCH_PROVIDER]
        for (r in rows) r.syncDefault(chosen)
    }

    private fun header(res: Int) {
        val h = SectionHeader(context)
        h.setText(res)
        list.addView(h, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun caption(res: Int) {
        val c = Caption(context)
        c.setText(res)
        c.setPadding(dp(16), dp(8), dp(16), dp(6))
        list.addView(c, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun describe(a: AccountInfo): String {
        val parts = ArrayList<String>(5)
        a.label?.let { parts.add(it) }
        parts.add("today " + money(a.usageDaily))
        parts.add("month " + money(a.usageMonthly))
        a.balance?.let { parts.add("balance " + money(it)) }
        a.limitRemaining?.let { parts.add("limit left " + money(it)) }
        if (a.isFreeTier) parts.add("free tier")
        return parts.joinToString(" · ")
    }

    private fun searched(hits: Int): String = context.getString(R.string.key_ok_search, hits)

    private fun money(v: Double): String = String.format(Locale.US, "$%.2f", v)

    private fun stamp(at: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))

    /**
     * One vendor: the field, what the field is next to do, and what its vendor last said.
     * [probe] runs off the main thread and returns the line to show when the key is accepted.
     */
    private inner class KeyRow(
        private val name: String,
        hint: Int,
        private val vendor: String?,
        private val probe: (String) -> String,
    ) {
        private val input = TextField(context)
        private val status = Caption(context)
        private val prefer = Chip(context)
        private val verify = Chip(context)
        private val refresh = IconButton(context)
        private var job: Job? = null
        private var busy = false
        private var detail: String? = null
        private var failure: String? = null

        private val typed: String get() = input.text.trim()
        private val stored: String get() = secrets.get(name) ?: ""

        val dirty: Boolean get() = typed != stored

        init {
            input.secret = true
            input.setHint(hint)
            input.text = stored
            input.onTextChanged = {
                failure = null
                render()
                syncSaveBar()
            }
            list.addView(
                input,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(16); marginEnd = dp(16) },
            )

            val actions = LinearLayout(context)
            actions.orientation = LinearLayout.HORIZONTAL
            actions.gravity = Gravity.CENTER_VERTICAL
            actions.setPadding(dp(16), dp(6), dp(10), 0)
            actions.addView(status, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            if (vendor != null) {
                prefer.style = Chip.Style.PLAIN
                prefer.setText(R.string.search_default)
                prefer.setOnClickListener {
                    prefs[Keys.SEARCH_PROVIDER] = vendor
                    syncDefault()
                }
                actions.addView(prefer, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(32)))
            }
            verify.setText(R.string.verify)
            verify.setOnClickListener { check() }
            actions.addView(verify, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(32)))
            refresh.iconRes = R.drawable.ic_refresh
            refresh.contentDescription = context.getString(R.string.cd_recheck)
            refresh.setOnClickListener { check() }
            actions.addView(refresh, LinearLayout.LayoutParams(dp(40), dp(40)))
            list.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            render()
        }

        /** Writes a changed key and says whether it is worth checking now. */
        fun save(): Boolean {
            if (!dirty) return false
            val key = typed
            secrets.put(name, key)
            checks.forget(name)
            detail = null
            failure = null
            input.text = key
            render()
            return key.isNotEmpty()
        }

        fun revert() {
            input.text = stored
            failure = null
            render()
        }

        fun check() {
            val key = typed
            if (key.isEmpty()) return
            job?.cancel()
            busy = true
            failure = null
            render()
            job = context.uiScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { probe(key) } }
                busy = false
                result.onSuccess {
                    detail = it
                    checks.accepted(name, key)
                }
                result.onFailure { failure = it.message ?: it.javaClass.simpleName }
                render()
            }
        }

        fun syncDefault(chosen: String) {
            if (vendor != null) prefer.active = vendor == chosen
        }

        fun cancel() {
            job?.cancel()
        }

        private fun render() {
            val key = typed
            val at = if (dirty) null else checks.verifiedAt(name, key)
            val failed = failure
            status.tone = if (failed != null) Caption.Tone.DANGER else Caption.Tone.NORMAL
            status.text = when {
                busy -> context.getString(R.string.verifying)
                failed != null -> failed
                dirty -> context.getString(R.string.key_unsaved)
                key.isEmpty() -> context.getString(R.string.key_none)
                at == null -> context.getString(R.string.key_unchecked)
                detail != null -> context.getString(R.string.key_verified_detail, stamp(at), detail)
                else -> context.getString(R.string.key_verified, stamp(at))
            }
            val settled = !busy && !dirty && key.isNotEmpty()
            verify.visibility = if (settled && at == null) View.VISIBLE else View.GONE
            refresh.visibility = if (settled && at != null) View.VISIBLE else View.GONE
        }
    }
}
