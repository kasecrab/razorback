package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.BuildConfig
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.TopBar

class AboutScreen(context: Context) : Screen(context) {

    private val bar = TopBar(context)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.about))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        val version = NavRow(context)
        version.set(R.drawable.ic_waveform, context.getString(R.string.app_name), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        list.addView(version)
        val source = NavRow(context)
        source.set(R.drawable.ic_external, context.getString(R.string.source_code), SOURCE_URL)
        source.setOnClickListener { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }
        list.addView(source)
        val note = Caption(context)
        note.setText(R.string.about_note)
        note.setPadding(dp(16), dp(16), dp(16), dp(16))
        list.addView(note)
        val scroll = ScrollView(context)
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/kasecrab/razorback"
    }
}
