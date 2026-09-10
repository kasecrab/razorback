package io.github.kasecrab.razorback

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.WindowInsetsController
import android.widget.FrameLayout
import io.github.kasecrab.razorback.ui.chat.ChatScreen
import io.github.kasecrab.razorback.ui.core.BackDispatcher
import io.github.kasecrab.razorback.ui.core.ScreenStack
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.ThemeHost
import io.github.kasecrab.razorback.ui.core.ThemeMode
import io.github.kasecrab.razorback.ui.core.UiContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MainActivity : Activity() {

    private lateinit var host: ThemeHost
    private lateinit var root: FrameLayout
    private lateinit var stack: ScreenStack
    private lateinit var back: BackDispatcher
    private var imeAnimating = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var uiContext: UiContext

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.isNavigationBarContrastEnforced = false

        host = ThemeHost(Theme.build(systemMode()))
        back = BackDispatcher(this)
        val ctx = UiContext(this, host, back, scope)
        root = FrameLayout(ctx)
        stack = ScreenStack(root, back)
        ctx.nav = stack
        ctx.root = root
        uiContext = ctx
        back.add(stack, priority = 0)
        setContentView(root)

        host.onChange { applyWindow(it) }
        applyWindow(host.theme)
        wireInsets()

        stack.replaceRoot(ChatScreen(ctx))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val mode = systemMode()
        if (mode != host.theme.mode) host.set(root, Theme.build(mode, host.theme.accent, host.theme.fontScale, host.theme.reduceMotion))
    }

    private fun systemMode(): ThemeMode {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) ThemeMode.DARK else ThemeMode.LIGHT
    }

    private fun applyWindow(theme: Theme) {
        window.setBackgroundDrawable(ColorDrawable(theme.bg))
        val light = if (theme.isLight) {
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        } else {
            0
        }
        window.insetsController?.setSystemBarsAppearance(
            light,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }

    private fun wireInsets() {
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (!imeAnimating) applyInsets(insets)
            WindowInsets.CONSUMED
        }
        root.setWindowInsetsAnimationCallback(object : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP) {
            override fun onPrepare(animation: WindowInsetsAnimation) {
                if (animation.typeMask and WindowInsets.Type.ime() != 0) imeAnimating = true
            }

            override fun onProgress(insets: WindowInsets, running: MutableList<WindowInsetsAnimation>): WindowInsets {
                applyInsets(insets)
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimation) {
                if (animation.typeMask and WindowInsets.Type.ime() != 0) {
                    imeAnimating = false
                    root.rootWindowInsets?.let { applyInsets(it) }
                }
            }
        })
    }

    private fun applyInsets(insets: WindowInsets) {
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val ime = insets.getInsets(WindowInsets.Type.ime())
        uiContext.insetTop = bars.top
        uiContext.insetBottom = bars.bottom
        stack.onInsetsChanged(bars.top, maxOf(bars.bottom, ime.bottom), bars.left, bars.right)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        root.setOnApplyWindowInsetsListener(null)
        root.setWindowInsetsAnimationCallback(null)
    }
}
