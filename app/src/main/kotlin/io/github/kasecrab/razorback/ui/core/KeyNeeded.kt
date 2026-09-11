package io.github.kasecrab.razorback.ui.core

import android.content.Context
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Secrets
import io.github.kasecrab.razorback.ui.settings.ProvidersScreen
import io.github.kasecrab.razorback.ui.widget.ActionSheet

/** What to show when a vendor's key is missing: which one, why, and the way to the field for it. */
object KeyNeeded {

    /** True when the key is there; otherwise the sheet is shown and false comes back. */
    fun check(context: Context, name: String): Boolean {
        if (App.instance.secrets.get(name) != null) return true
        explain(context, name)
        return false
    }

    fun explain(context: Context, name: String) {
        val vendor = when (name) {
            Secrets.OPENROUTER -> "OpenRouter"
            Secrets.DEEPGRAM -> "Deepgram"
            else -> name
        }
        val purpose = context.getString(if (name == Secrets.DEEPGRAM) R.string.key_needed_deepgram else R.string.key_needed_openrouter)
        ActionSheet(context)
            .header(context.getString(R.string.key_needed_title, vendor), context.getString(R.string.key_needed_text, purpose, vendor))
            .add(R.drawable.ic_key, context.getString(R.string.open_providers)) { context.nav.push(ProvidersScreen(context)) }
            .add(R.drawable.ic_close, context.getString(R.string.not_now)) {}
            .show()
    }
}
