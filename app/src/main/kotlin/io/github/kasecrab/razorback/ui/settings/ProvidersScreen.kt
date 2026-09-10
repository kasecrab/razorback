package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.provider.openrouter.AccountInfo
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterAccount
import io.github.kasecrab.razorback.tools.search.BraveSearch
import io.github.kasecrab.razorback.tools.search.ExaSearch
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
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
import java.util.Locale

/** One field per vendor key. Keys are saved as they are typed; each has its own verify. */
class ProvidersScreen(context: Context) : Screen(context) {

    private val secrets = App.instance.secrets
    private val bar = TopBar(context)
    private val list = LinearLayout(context)
    private val jobs = ArrayList<Job>(4)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.providers))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(32))
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        header(R.string.openrouter)
        key(Secrets.OPENROUTER, R.string.key_hint_openrouter) { describe(OpenRouterAccount.fetch(it)) }

        header(R.string.deepgram)
        key(Secrets.DEEPGRAM, R.string.key_hint_generic) { DeepgramAccount.check(it) }

        header(R.string.web_search)
        val toggle = SwitchRow(context)
        val prefs = App.instance.prefs
        toggle.set(context.getString(R.string.web_search_toggle), context.getString(R.string.web_search_toggle_hint), prefs[Keys.WEB_SEARCH]) {
            prefs[Keys.WEB_SEARCH] = it
        }
        list.addView(toggle, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        caption(R.string.brave)
        key(Secrets.BRAVE, R.string.key_hint_generic) { searched(BraveSearch.search(it, "razorback", 1).size) }
        caption(R.string.exa)
        key(Secrets.EXA, R.string.key_hint_generic) { searched(ExaSearch.search(it, "razorback", 1).size) }
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

    /** A masked field, a verify chip and a status line; [check] runs off the main thread and returns what to show. */
    private fun key(name: String, hint: Int, check: (String) -> String) {
        val f = TextField(context)
        f.secret = true
        f.setHint(hint)
        f.text = secrets.get(name) ?: ""
        f.onTextChanged = { secrets.put(name, it) }
        list.addView(f, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(16); marginEnd = dp(16) })
        val actions = LinearLayout(context)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER_VERTICAL
        actions.setPadding(dp(16), dp(10), dp(16), 0)
        val verify = Chip(context)
        verify.style = Chip.Style.ACCENT
        verify.leadingIcon = R.drawable.ic_check
        verify.setText(R.string.verify)
        actions.addView(verify, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))
        list.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val status = Caption(context)
        status.setPadding(dp(16), dp(8), dp(16), 0)
        status.visibility = View.GONE
        list.addView(status, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        verify.setOnClickListener {
            val k = f.text.trim()
            status.visibility = View.VISIBLE
            if (k.isEmpty()) {
                status.tone = Caption.Tone.DANGER
                status.setText(R.string.key_missing)
                return@setOnClickListener
            }
            status.tone = Caption.Tone.NORMAL
            status.setText(R.string.verifying)
            jobs.add(
                context.uiScope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { check(k) } }
                    result.onSuccess { status.text = it }
                    result.onFailure {
                        status.tone = Caption.Tone.DANGER
                        status.text = it.message ?: it.javaClass.simpleName
                    }
                },
            )
        }
    }

    private fun describe(a: AccountInfo): String {
        val sb = StringBuilder()
        sb.append(a.label ?: "Key ok")
        sb.append(" · today ").append(money(a.usageDaily))
        sb.append(" · month ").append(money(a.usageMonthly))
        a.balance?.let { sb.append(" · balance ").append(money(it)) }
        a.limitRemaining?.let { sb.append(" · limit left ").append(money(it)) }
        if (a.isFreeTier) sb.append(" · free tier")
        return sb.toString()
    }

    private fun searched(hits: Int): String = context.getString(R.string.key_ok_search, hits)

    private fun money(v: Double): String = String.format(Locale.US, "$%.2f", v)

    override fun onExit() {
        for (j in jobs) j.cancel()
    }
}
