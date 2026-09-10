package io.github.kasecrab.razorback

import android.app.Application
import io.github.kasecrab.razorback.core.Prefs
import io.github.kasecrab.razorback.core.Secrets

class App : Application() {

    val prefs: Prefs by lazy { Prefs(this) }
    val secrets: Secrets by lazy { Secrets(prefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
