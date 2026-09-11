package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.KeyChecks
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterAccount
import io.github.kasecrab.razorback.tools.search.BraveSearch
import io.github.kasecrab.razorback.tools.search.ExaSearch
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Keyboard
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.icon
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.TextField
import io.github.kasecrab.razorback.ui.widget.TopBar
import io.github.kasecrab.razorback.voice.DeepgramAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One field per vendor key, the two the app runs on first and the search keys after. Typing
 * only stages a key: Save asks each changed key's vendor about it and writes it only once
 * accepted, so a key that does not work is never kept. A key that was accepted shows a
 * green tick and nothing else, until someone edits it.
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
    private val firstLabel = TextView(context)
    private val firstChips = ArrayList<Pair<String, Chip>>(2)

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

        header(R.string.providers_core)
        rows.add(KeyRow(Secrets.OPENROUTER, R.string.openrouter, R.string.key_hint_openrouter) { OpenRouterAccount.fetch(it) })
        rows.add(KeyRow(Secrets.DEEPGRAM, R.string.deepgram, R.string.key_hint_generic) { DeepgramAccount.check(it) })

        header(R.string.web_search)
        val toggle = SwitchRow(context)
        toggle.set(context.getString(R.string.web_search_toggle), context.getString(R.string.web_search_toggle_hint), prefs[Keys.WEB_SEARCH]) {
            prefs[Keys.WEB_SEARCH] = it
        }
        list.addView(toggle, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rows.add(KeyRow(Secrets.BRAVE, R.string.brave, R.string.key_hint_generic) { BraveSearch.search(it, "razorback", 1) })
        rows.add(KeyRow(Secrets.EXA, R.string.exa, R.string.key_hint_generic) { ExaSearch.search(it, "razorback", 1) })
        buildFirstChoice()
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
        firstLabel.setTextColor(theme.textPrimary)
        firstLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
        for (r in rows) r.render()
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

    /** Which search engine is asked first: one row, one chip per engine, the chosen one lit. */
    private fun buildFirstChoice() {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(16), dp(14), dp(16), dp(4))
        firstLabel.typeface = Fonts.regular
        firstLabel.setText(R.string.search_first)
        row.addView(firstLabel, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        for ((vendor, label) in listOf(Secrets.BRAVE to R.string.brave_short, Secrets.EXA to R.string.exa)) {
            val chip = Chip(context)
            chip.setText(label)
            chip.setOnClickListener {
                prefs[Keys.SEARCH_PROVIDER] = vendor
                syncFirst()
            }
            row.addView(chip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(32)).apply { marginStart = dp(8) })
            firstChips.add(vendor to chip)
        }
        list.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val hint = Caption(context)
        hint.setText(R.string.search_first_hint)
        hint.setPadding(dp(16), 0, dp(16), dp(12))
        list.addView(hint, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        syncFirst()
    }

    private fun syncFirst() {
        val chosen = prefs[Keys.SEARCH_PROVIDER]
        for ((vendor, chip) in firstChips) chip.active = vendor == chosen
    }

    /** Every key that changed is checked with its vendor and written only if accepted. */
    private fun saveAll() {
        Keyboard.hideAll(context)
        for (r in rows) r.commit()
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

    private fun header(res: Int) {
        val h = SectionHeader(context)
        h.setText(res)
        list.addView(h, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * One vendor: its name with the key's standing beside it, the field under them, and the
     * reason under that when the vendor said no. [probe] runs off the main thread and
     * throws when the key is refused.
     */
    private inner class KeyRow(
        private val name: String,
        title: Int,
        hint: Int,
        private val probe: (String) -> Unit,
    ) {
        private val label = TextView(context)
        private val status = Caption(context)
        private val check = Chip(context)
        private val input = TextField(context)
        private val reason = Caption(context)
        private var job: Job? = null
        private var busy = false
        private var failure: String? = null

        private val typed: String get() = input.text.trim()
        private val stored: String get() = secrets.get(name) ?: ""

        val dirty: Boolean get() = typed != stored

        init {
            val head = LinearLayout(context)
            head.orientation = LinearLayout.HORIZONTAL
            head.gravity = Gravity.CENTER_VERTICAL
            head.setPadding(dp(16), dp(12), dp(16), dp(6))
            label.typeface = Fonts.regular
            label.setText(title)
            head.addView(label, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            status.compoundDrawablePadding = dp(4)
            head.addView(status, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            check.setText(R.string.check_key)
            check.setOnClickListener { check() }
            head.addView(check, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(32)))
            list.addView(head, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

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
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(16)
                    marginEnd = dp(16)
                    bottomMargin = dp(4)
                },
            )
            reason.tone = Caption.Tone.DANGER
            reason.setPadding(dp(16), dp(4), dp(16), dp(4))
            list.addView(reason, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            render()
        }

        /**
         * A cleared key is dropped at once; a new key is put to its vendor first and kept
         * only when accepted, otherwise it stays in the field, unsaved, with the reason.
         */
        fun commit() {
            if (!dirty) return
            val key = typed
            if (key.isEmpty()) {
                secrets.put(name, null)
                checks.forget(name)
                failure = null
                render()
                return
            }
            ask(key) {
                secrets.put(name, key)
                checks.accepted(name, key)
                input.text = key
            }
        }

        /** A saved key nobody has checked yet is put to its vendor without being rewritten. */
        fun check() {
            val key = typed
            if (key.isEmpty() || dirty) return
            ask(key) { checks.accepted(name, key) }
        }

        private fun ask(key: String, accepted: () -> Unit) {
            job?.cancel()
            busy = true
            failure = null
            render()
            job = context.uiScope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { probe(key) } }
                busy = false
                result.onSuccess {
                    accepted()
                    Haptics.confirm(input)
                }
                result.onFailure {
                    failure = reason(it)
                    Haptics.reject(input)
                }
                render()
                syncSaveBar()
            }
        }

        /** What the vendor's refusal comes down to, in a few words. */
        private fun reason(t: Throwable): String {
            val vendorName = when (name) {
                Secrets.OPENROUTER -> "OpenRouter"
                Secrets.DEEPGRAM -> "Deepgram"
                Secrets.BRAVE -> "Brave"
                Secrets.EXA -> "Exa"
                else -> name
            }
            val http = t as? io.github.kasecrab.razorback.core.HttpException
            return if (http != null && (http.status == 401 || http.status == 403)) context.getString(R.string.key_refused, vendorName) else t.message ?: t.javaClass.simpleName
        }

        fun revert() {
            input.text = stored
            failure = null
            render()
        }

        fun cancel() {
            job?.cancel()
        }

        fun render() {
            val theme = context.appTheme
            val key = typed
            val verified = !dirty && checks.verifiedAt(name, key) != null
            val failed = failure
            label.setTextColor(theme.textPrimary)
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.BODY))
            val unchecked = !busy && !dirty && key.isNotEmpty() && !verified
            check.visibility = if (unchecked) View.VISIBLE else View.GONE
            status.visibility = if (unchecked) View.GONE else View.VISIBLE
            status.tone = when {
                busy -> Caption.Tone.NORMAL
                failed != null -> Caption.Tone.DANGER
                dirty -> Caption.Tone.ACCENT
                verified -> Caption.Tone.OK
                else -> Caption.Tone.NORMAL
            }
            status.text = when {
                busy -> context.getString(R.string.verifying)
                failed != null -> context.getString(R.string.key_rejected)
                dirty -> context.getString(R.string.key_unsaved)
                key.isEmpty() -> context.getString(R.string.key_none)
                else -> context.getString(R.string.key_verified)
            }
            val tick = if (!busy && !dirty && verified) context.icon(R.drawable.ic_check, theme.ok).also { it.setBounds(0, 0, dp(16), dp(16)) } else null
            status.setCompoundDrawablesRelative(tick, null, null, null)
            reason.text = failed
            reason.visibility = if (failed != null) View.VISIBLE else View.GONE
        }
    }
}
