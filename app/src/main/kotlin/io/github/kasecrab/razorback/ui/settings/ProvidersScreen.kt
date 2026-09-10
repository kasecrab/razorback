package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.provider.openrouter.AccountInfo
import io.github.kasecrab.razorback.provider.openrouter.OpenRouter
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterAccount
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.TextField
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** One field per vendor key. Keys are saved as they are typed and verified on demand. */
class ProvidersScreen(context: Context) : Screen(context) {

    private val secrets = App.instance.secrets
    private val bar = TopBar(context)
    private val list = LinearLayout(context)
    private val status = Caption(context)
    private var verifyJob: Job? = null

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
        val openrouter = field(Secrets.OPENROUTER, R.string.key_hint_openrouter)
        val actions = LinearLayout(context)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER_VERTICAL
        actions.setPadding(dp(16), dp(10), dp(16), 0)
        val verify = Chip(context)
        verify.style = Chip.Style.ACCENT
        verify.leadingIcon = R.drawable.ic_check
        verify.setText(R.string.verify)
        verify.setOnClickListener { verify(openrouter.text) }
        actions.addView(verify, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)))
        val getKey = Chip(context)
        getKey.style = Chip.Style.PLAIN
        getKey.trailingIcon = R.drawable.ic_external
        getKey.setText(R.string.get_key)
        getKey.setOnClickListener { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OpenRouter.KEYS_URL))) }
        actions.addView(getKey, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(36)).apply { marginStart = dp(8) })
        list.addView(actions, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        status.setPadding(dp(16), dp(8), dp(16), 0)
        status.visibility = View.GONE
        list.addView(status, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        header(R.string.deepgram)
        field(Secrets.DEEPGRAM, R.string.key_hint_generic)

        header(R.string.web_search)
        val toggle = SwitchRow(context)
        val prefs = App.instance.prefs
        toggle.set(context.getString(R.string.web_search_toggle), context.getString(R.string.web_search_toggle_hint), prefs[io.github.kasecrab.razorback.core.Keys.WEB_SEARCH]) {
            prefs[io.github.kasecrab.razorback.core.Keys.WEB_SEARCH] = it
        }
        list.addView(toggle, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        caption(R.string.brave)
        field(Secrets.BRAVE, R.string.key_hint_generic)
        caption(R.string.exa)
        field(Secrets.EXA, R.string.key_hint_generic)
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

    private fun field(name: String, hint: Int): TextField {
        val f = TextField(context)
        f.secret = true
        f.setHint(hint)
        f.text = secrets.get(name) ?: ""
        f.onTextChanged = { secrets.put(name, it) }
        list.addView(
            f,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            },
        )
        return f
    }

    private fun verify(key: String) {
        verifyJob?.cancel()
        status.visibility = View.VISIBLE
        status.tone = Caption.Tone.NORMAL
        status.setText(R.string.verifying)
        verifyJob = context.uiScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { OpenRouterAccount.fetch(key.trim()) } }
            result.onSuccess { status.text = describe(it) }
            result.onFailure {
                status.tone = Caption.Tone.DANGER
                status.text = it.message ?: it.javaClass.simpleName
            }
        }
    }

    private fun describe(a: AccountInfo): String {
        val sb = StringBuilder()
        sb.append(a.label ?: "key ok")
        sb.append(" · today ").append(money(a.usageDaily))
        sb.append(" · month ").append(money(a.usageMonthly))
        a.balance?.let { sb.append(" · balance ").append(money(it)) }
        a.limitRemaining?.let { sb.append(" · limit left ").append(money(it)) }
        if (a.isFreeTier) sb.append(" · free tier")
        return sb.toString()
    }

    private fun money(v: Double): String = String.format(Locale.US, "$%.2f", v)

    override fun onExit() {
        verifyJob?.cancel()
    }
}
