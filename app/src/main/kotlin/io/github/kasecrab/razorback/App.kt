package io.github.kasecrab.razorback

import android.app.Application
import io.github.kasecrab.razorback.chat.ChatEngine
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.data.ChatStore
import io.github.kasecrab.razorback.data.Db
import io.github.kasecrab.razorback.data.Favorites
import io.github.kasecrab.razorback.data.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.kasecrab.razorback.data.ModelCache
import io.github.kasecrab.razorback.data.PromptStore
import io.github.kasecrab.razorback.provider.ModelCatalog
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterProvider
import io.github.kasecrab.razorback.tools.ToolRegistry
import io.github.kasecrab.razorback.voice.VoiceCatalog
import io.github.kasecrab.razorback.voice.VoiceSession

class App : Application() {

    val prefs: Prefs by lazy { Prefs(this) }
    val secrets: Secrets by lazy { Secrets(prefs) }
    val openRouter: OpenRouterProvider by lazy { OpenRouterProvider { secrets.get(Secrets.OPENROUTER) } }
    val db: Db by lazy { Db(this) }
    val store: ChatStore by lazy { ChatStore(db) }
    val stats: Stats by lazy { Stats(db) }
    val prompts: PromptStore by lazy { PromptStore(db) }
    val catalog: ModelCatalog by lazy { ModelCatalog(ModelCache(filesDir)) { secrets.get(Secrets.OPENROUTER) } }
    val tools: ToolRegistry by lazy { ToolRegistry(prefs, secrets) }
    val engine: ChatEngine by lazy { ChatEngine(this, prefs, openRouter, store, catalog, tools, io.github.kasecrab.razorback.chat.Namer { secrets.get(Secrets.OPENROUTER) }) }
    val voice: VoiceSession by lazy { VoiceSession(this, prefs, secrets, engine, tools.endVoice) }
    val favorites: Favorites by lazy { Favorites(prefs) }
    val voices: VoiceCatalog by lazy { VoiceCatalog(filesDir) { secrets.get(Secrets.DEEPGRAM) } }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (BuildConfig.DEBUG) prefs.rawString(DEV_BASE_URL)?.let { io.github.kasecrab.razorback.provider.openrouter.OpenRouter.BASE = it }
        scope.launch { store.repairStreaming() }
    }

    companion object {
        const val DEV_BASE_URL = "dev.base_url"
        lateinit var instance: App
            private set
    }
}
