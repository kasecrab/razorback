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
import android.content.Intent
import io.github.kasecrab.razorback.ui.core.ActivityResults
import io.github.kasecrab.razorback.ui.core.BackDispatcher
import io.github.kasecrab.razorback.ui.core.PermissionRequests
import io.github.kasecrab.razorback.bg.Notifs
import io.github.kasecrab.razorback.bg.TurnService
import kotlinx.coroutines.launch
import io.github.kasecrab.razorback.ui.core.ScreenStack
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.ThemeHost
import io.github.kasecrab.razorback.ui.core.ThemeResolver
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
    private var pairSheet: io.github.kasecrab.razorback.ui.widget.ActionSheet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.isNavigationBarContrastEnforced = false

        host = ThemeHost(ThemeResolver.resolve(this, App.instance.prefs))
        back = BackDispatcher(this)
        val ctx = UiContext(this, host, back, scope)
        root = FrameLayout(ctx)
        stack = ScreenStack(root, back)
        ctx.nav = stack
        ctx.root = root
        ctx.results = ActivityResults { intent, code -> startActivityForResult(intent, code) }
        ctx.permissions = PermissionRequests(this)
        uiContext = ctx
        back.add(stack, priority = 0)
        setContentView(root)

        host.onChange { applyWindow(it) }
        applyWindow(host.theme)
        wireInsets()

        stack.replaceRoot(ChatScreen(ctx))
        App.instance.prefs.onChange(onPref)
        Notifs.ensureChannels(this)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * What arrives from outside: a pairing link, or one of this app's own notifications
     * asking for a conversation to be brought to the front.
     */
    private fun handleIntent(intent: Intent?) {
        if (BuildConfig.DEBUG && intent?.hasExtra("base_url") == true) {
            // adb shell am start ... --es base_url http://10.0.2.2:8787 points a debug build at a mock
            // server. The extra is not adb's to send alone — any app on the phone can send the same
            // one, and every request afterwards carries the OpenRouter key to wherever it points — so
            // it is taken only for a host a mock server could be on. Clearing it back to the real one
            // is always allowed.
            val url = intent.getStringExtra("base_url")?.trim()?.takeIf { it.isNotEmpty() }
            if (url == null || io.github.kasecrab.razorback.provider.openrouter.OpenRouter.mockServer(url)) {
                App.instance.prefs.putRawString(App.DEV_BASE_URL, url)
                io.github.kasecrab.razorback.provider.openrouter.OpenRouter.BASE = url ?: io.github.kasecrab.razorback.provider.openrouter.OpenRouter.DEFAULT_BASE
            }
        }
        val data = intent?.data ?: return
        if (data.scheme != "razorback") return
        if (data.host == "pair") {
            // What the QR printed by `ah remote pair` holds. The camera app
            // reads it, so nothing here needs a camera or a scanner.
            val url = data.getQueryParameter("u").orEmpty()
            val code = data.getQueryParameter("c").orEmpty()
            intent.data = null
            offerPairing(url, code)
            return
        }
        if (data.host != "chat") return
        // A chat comes from a notification this app posted and from nowhere else. The
        // filter for it is gone from the manifest, and since this activity is the launcher
        // it is exported anyway — any app could still name it and hand it an address — so
        // the notification says a word only this app knows. An id is 32 bits of randomness
        // behind a guessable timestamp, which is not much to hold a conversation shut with.
        if (intent.getStringExtra(Notifs.EXTRA_TOKEN) != App.instance.openToken) return
        val id = data.lastPathSegment ?: return
        intent.data = null
        scope.launch {
            val conv = App.instance.store.getConversation(id) ?: return@launch
            App.instance.engine.open(conv)
        }
    }

    /**
     * Any app on the phone can fire a pairing link at this activity, so nothing is written
     * until the person has read what it is and said yes: the relay has to be https, the
     * code has to be a code, and a pairing already held is named before it is replaced.
     *
     * A hostname on its own is a poor thing to decide on, since one relay address reads
     * much like another, so the sheet also names the hub the code leads to — the same
     * first eight characters `ah remote pair` printed on the machine. Two short strings to
     * compare is a better question than a name somebody else chose.
     *
     * Replacing a pairing already held is not one tap either: a second sheet says which
     * machine is being let go of, in the words on the button.
     */
    private fun offerPairing(url: String, code: String) {
        // A link can be fired again and again, and a sheet landing on top of a sheet is
        // how a person taps something they were only part-way through reading.
        if (pairSheet != null) return
        val remote = App.instance.remote
        val relay = io.github.kasecrab.razorback.remote.RelayUrl
        val raw = io.github.kasecrab.razorback.remote.Codes.parse(code)
        if (raw == null || !relay.acceptable(url)) {
            io.github.kasecrab.razorback.ui.core.Haptics.reject()
            showPairSheet(
                io.github.kasecrab.razorback.ui.widget.ActionSheet(uiContext)
                    .header(getString(R.string.pair_bad_title), getString(R.string.pair_bad_text)),
            )
            return
        }
        val hub = io.github.kasecrab.razorback.remote.Crypto.Keys(raw).hub.take(8)
        val held = if (remote.paired) remote.machine?.host ?: relay.host(App.instance.prefs[io.github.kasecrab.razorback.core.Keys.RELAY_URL]) else null
        val take = { if (remote.pair(url, code)) io.github.kasecrab.razorback.ui.core.Haptics.confirm() }
        val sheet = io.github.kasecrab.razorback.ui.widget.ActionSheet(uiContext)
            .header(
                getString(R.string.pair_title, relay.host(url)),
                if (held != null) getString(R.string.pair_replaces, hub, held) else getString(R.string.pair_text, hub),
            )
        if (held == null) {
            sheet.add(R.drawable.ic_check, getString(R.string.remote_pair)) { take() }
        } else {
            sheet.add(R.drawable.ic_check, getString(R.string.pair_replace, held), danger = true) {
                showPairSheet(
                    io.github.kasecrab.razorback.ui.widget.ActionSheet(uiContext)
                        .header(getString(R.string.pair_replace_title, held), getString(R.string.pair_replace_text))
                        .add(R.drawable.ic_trash, getString(R.string.pair_replace_confirm, held), danger = true) { take() }
                        .add(R.drawable.ic_close, getString(R.string.pair_keep, held)) {},
                )
            }
        }
        showPairSheet(sheet.add(R.drawable.ic_close, getString(R.string.pair_not_now), danger = true) {})
    }

    /**
     * The one pairing sheet that is up, so a second link finds the door shut. A sheet on
     * its way out lets go only if nothing has taken its place: the first sheet of a
     * two-step replace is still animating away while the second is already on screen.
     *
     * The panel refuses a touch that arrived through something drawn over it. A pairing is
     * exactly the tap another app would like to place a window underneath.
     */
    private fun showPairSheet(sheet: io.github.kasecrab.razorback.ui.widget.ActionSheet) {
        pairSheet = sheet
        sheet.panel.filterTouchesWhenObscured = true
        sheet.onDismiss = { if (pairSheet === sheet) pairSheet = null }
        sheet.show()
    }

    override fun onStart() {
        super.onStart()
        TurnService.stop(this)
        // On screen again, so the link needs no notification to stay open.
        io.github.kasecrab.razorback.bg.RemoteService.stop(this)
        if (App.instance.remote.paired) App.instance.remote.start()
        Notifs.cancelReply(this)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        if (App.instance.engine.isStreaming) TurnService.start(this)
        // A session being watched on another machine goes on working either way; what
        // needs keeping alive is the socket that hears about it. Nothing watched, nothing
        // kept: the link is dropped and picked up again when the app comes back.
        val remote = App.instance.remote
        if (remote.paired && remote.attached.isNotEmpty()) {
            io.github.kasecrab.razorback.bg.RemoteService.start(this)
        } else {
            remote.stop()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (!uiContext.permissions.deliver(requestCode, grantResults)) super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private val onPref: (String) -> Unit = { if (it.startsWith("theme.")) retheme() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        retheme()
    }

    private fun retheme() {
        val next = ThemeResolver.resolve(this, App.instance.prefs)
        val cur = host.theme
        if (next.mode != cur.mode || next.accent != cur.accent || next.fontScale != cur.fontScale || next.reduceMotion != cur.reduceMotion) host.set(root, next)
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
        uiContext.imeBottom = ime.bottom
        stack.onInsetsChanged(bars.top, maxOf(bars.bottom, ime.bottom), bars.left, bars.right)
        uiContext.insetsChanged()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!uiContext.results.deliver(requestCode, resultCode, data)) super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        App.instance.prefs.removeOnChange(onPref)
        scope.cancel()
        root.setOnApplyWindowInsetsListener(null)
        root.setWindowInsetsAnimationCallback(null)
    }
}
