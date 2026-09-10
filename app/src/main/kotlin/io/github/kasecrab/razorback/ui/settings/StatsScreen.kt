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
import io.github.kasecrab.razorback.data.Stats
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.appTheme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.Sparkline
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Local numbers: what this phone asked for, what it cost, how fast replies came. */
class StatsScreen(context: Context) : Screen(context) {

    private val bar = TopBar(context)
    private val list = LinearLayout(context)
    private val empty = Caption(context)
    private val tiles = ArrayList<StatTile>()
    private val rows = ArrayList<TextView>()
    private val spark = Sparkline(context)
    private var job: Job? = null

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.stats))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(24))
        empty.setPadding(dp(16), dp(16), dp(16), dp(16))
        empty.setText(R.string.stats_empty)
        empty.visibility = View.GONE
        list.addView(empty)
        val scroll = ScrollView(context)
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onEnter() {
        job = context.uiScope.launch { show(App.instance.stats.report()) }
    }

    override fun onExit() {
        job?.cancel()
    }

    private fun show(r: Stats.Report) {
        for (t in tiles) list.removeView(t)
        tiles.clear()
        if (r.allTime.requests == 0) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE
        section(R.string.stats_week)
        window(r.week)
        section(R.string.stats_month)
        window(r.month)
        section(R.string.stats_daily)
        spark.setData(r.dailyCost)
        list.addView(spark, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(72)).apply { setMargins(dp(16), dp(4), dp(16), dp(8)) })
        section(R.string.stats_models)
        for (m in r.topModels) {
            row("${m.model.substringAfter('/')}  ·  ${m.requests} req  ·  ${Fmt.tokens(m.tokens)} tok  ·  ${Fmt.money(m.cost)}")
        }
        section(R.string.stats_all_time)
        window(r.allTime)
    }

    private fun window(t: Stats.Totals) {
        val grid = LinearLayout(context)
        grid.orientation = LinearLayout.HORIZONTAL
        grid.setPadding(dp(16), 0, dp(16), 0)
        fun tile(v: String, l: String) {
            val tile = StatTile(context)
            tile.set(v, l)
            tile.theme(context.appTheme)
            grid.addView(tile, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            tiles.add(tile)
        }
        tile(Fmt.money(t.cost), "spent")
        tile(t.requests.toString(), if (t.failures > 0) "requests · ${t.failures} failed" else "requests")
        tile(Fmt.tokens(t.promptTokens + t.completionTokens), "tokens")
        list.addView(grid, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        row("${Fmt.tokens(t.promptTokens)} in · ${Fmt.tokens(t.completionTokens)} out · ${Fmt.tokens(t.reasoningTokens)} thinking · first token ${Fmt.duration(t.avgTtftMs)} · reply ${Fmt.duration(t.avgLatencyMs)}")
    }

    private fun section(res: Int) {
        val h = SectionHeader(context)
        h.setText(res)
        list.addView(h)
    }

    private fun row(text: String) {
        val tv = TextView(context)
        tv.typeface = Fonts.regular
        tv.text = text
        tv.setPadding(dp(16), dp(6), dp(16), dp(6))
        styleRow(tv, context.appTheme)
        list.addView(tv)
        rows.add(tv)
    }

    private fun styleRow(tv: TextView, theme: Theme) {
        tv.setTextColor(theme.textSecondary)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.SECONDARY))
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        for (t in tiles) t.theme(theme)
        for (r in rows) styleRow(r, theme)
    }
}
