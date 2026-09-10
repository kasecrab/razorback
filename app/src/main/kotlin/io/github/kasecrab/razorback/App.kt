package io.github.kasecrab.razorback

import android.app.Application
import io.github.kasecrab.razorback.chat.ChatEngine
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.data.Favorites
import io.github.kasecrab.razorback.data.ModelCache
import io.github.kasecrab.razorback.provider.ModelCatalog
import io.github.kasecrab.razorback.provider.openrouter.OpenRouterProvider

class App : Application() {

    val prefs: Prefs by lazy { Prefs(this) }
    val secrets: Secrets by lazy { Secrets(prefs) }
    val openRouter: OpenRouterProvider by lazy { OpenRouterProvider { secrets.get(Secrets.OPENROUTER) } }
    val engine: ChatEngine by lazy { ChatEngine(prefs, openRouter) }
    val catalog: ModelCatalog by lazy { ModelCatalog(ModelCache(filesDir)) { secrets.get(Secrets.OPENROUTER) } }
    val favorites: Favorites by lazy { Favorites(prefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
