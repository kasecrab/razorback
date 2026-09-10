package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Fmt
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.provider.openrouter.AccountInfo
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterAccount
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Shapes
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A pair of big number and label, used by the usage and stats screens. */
class StatTile(context: Context) : LinearLayout(context) {
    private val value = TextView(context)
    private val label = TextView(context)

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        value.typeface = Fonts.medium
        label.typeface = Fonts.regular
        addView(value)
        addView(label)
    }

    fun set(v: String, l: String) {
        value.text = v
        label.text = l
    }

    fun theme(theme: Theme) {
        background = Shapes.rounded(theme.surface, dp(theme.radiusM))
        value.setTextColor(theme.textPrimary)
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
        label.setTextColor(theme.textSecondary)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.CAPTION))
    }
}

/** What OpenRouter reports for the key: spend windows, limit and balance. */
class UsageScreen(context: Context) : Screen(context) {

    private val bar = TopBar(context)
    private val list = LinearLayout(context)
    private val status = Caption(context)
    private val tiles = ArrayList<StatTile>()
    private var job: Job? = null

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.usage), R.drawable.ic_refresh, context.getString(R.string.cd_refresh))
        bar.leading.setOnClickListener { context.nav.pop() }
        bar.trailing.setOnClickListener { load() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(16), dp(8), dp(16), dp(24))
        status.setPadding(0, dp(8), 0, dp(8))
        list.addView(status)
        val scroll = ScrollView(context)
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onEnter() = load()

    override fun onExit() {
        job?.cancel()
    }

    private fun load() {
        val key = App.instance.secrets.get(Secrets.OPENROUTER)
        if (key == null) {
            status.setText(R.string.usage_no_key)
            return
        }
        status.tone = Caption.Tone.NORMAL
        status.setText(R.string.verifying)
        job?.cancel()
        job = context.uiScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { OpenRouterAccount.fetch(key) } }
            r.onSuccess { show(it) }
            r.onFailure {
                status.tone = Caption.Tone.DANGER
                status.text = it.message ?: it.javaClass.simpleName
            }
        }
    }

    private fun show(a: AccountInfo) {
        status.visibility = View.GONE
        for (t in tiles) list.removeView(t)
        tiles.clear()
        fun tile(v: String, l: String) {
            val t = StatTile(context)
            t.set(v, l)
            t.theme(context.appTheme)
            list.addView(t, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
            tiles.add(t)
        }
        tile(Fmt.money(a.usageDaily), "Today")
        tile(Fmt.money(a.usageWeekly), "This week")
        tile(Fmt.money(a.usageMonthly), "This month")
        tile(Fmt.money(a.usage), "All time on this key" + (a.label?.let { " · $it" } ?: ""))
        a.balance?.let { tile(Fmt.money(it), "Balance") }
        a.limitRemaining?.let { tile(Fmt.money(it), "Key limit remaining" + (a.limit?.let { l -> " of ${Fmt.money(l)}" } ?: "")) }
        if (a.isFreeTier) tile("Free tier", "Account")
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        for (t in tiles) t.theme(theme)
    }
}
