package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.ui.drawer.DrawerHost
import io.github.kasecrab.razorback.ui.drawer.DrawerPanel
import io.github.kasecrab.razorback.ui.settings.SettingsScreen
import io.github.kasecrab.razorback.ui.widget.TopBar

class ChatScreen(context: Context) : Screen(context) {

    private val drawer = DrawerHost(context)
    private val panel = DrawerPanel(context)
    private val column = LinearLayout(context)
    private val bar = TopBar(context)
    private val content = FrameLayout(context)
    private val empty = EmptyState(context)
    val composer = Composer(context)

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
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        content.addView(empty, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        column.addView(content, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        column.addView(
            composer,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(10), dp(4), dp(10), dp(8))
            },
        )

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
    }

    override fun onEnter() {
        context.ui().back.add(drawer, priority = 10)
    }

    override fun onExit() {
        context.ui().back.remove(drawer)
    }

    override fun onInsetsChanged(top: Int, bottom: Int, left: Int, right: Int) {
        column.setPadding(left, top, right, bottom)
        panel.setInsets(top, bottom)
    }

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        column.setBackgroundColor(theme.bg)
    }
}
