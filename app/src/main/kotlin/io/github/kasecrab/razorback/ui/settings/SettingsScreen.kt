package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.widget.TopBar

class SettingsScreen(context: Context) : Screen(context) {

    private val bar = TopBar(context)
    private val list = LinearLayout(context)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.settings))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        row(R.drawable.ic_key, R.string.providers, R.string.providers_subtitle) { context.nav.push(ProvidersScreen(context)) }
        row(R.drawable.ic_edit, R.string.prompts, R.string.prompts_subtitle) { context.nav.push(PromptsScreen(context)) }
        row(R.drawable.ic_waveform, R.string.voice, R.string.voice_subtitle) { context.nav.push(VoiceSettingsScreen(context)) }
        row(R.drawable.ic_image, R.string.appearance, R.string.appearance_subtitle) { context.nav.push(AppearanceScreen(context)) }
        row(R.drawable.ic_globe, R.string.usage, R.string.usage_subtitle) { context.nav.push(UsageScreen(context)) }
        row(R.drawable.ic_brain, R.string.stats, R.string.stats_subtitle) { context.nav.push(StatsScreen(context)) }
        row(R.drawable.ic_download, R.string.backup, R.string.backup_subtitle) { context.nav.push(BackupScreen(context)) }
    }

    private fun row(icon: Int, title: Int, subtitle: Int, onClick: () -> Unit) {
        val r = NavRow(context)
        r.set(icon, context.getString(title), context.getString(subtitle))
        r.setOnClickListener { onClick() }
        list.addView(r, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}
