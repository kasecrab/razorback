package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.core.Prefs

/** Builds the theme the preferences ask for, following the system when told to. */
object ThemeResolver {

    fun resolve(context: Context, prefs: Prefs): Theme {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val mode = if (prefs[Keys.THEME_FOLLOW_SYSTEM]) {
            if (night) prefs[Keys.THEME_MODE].takeIf { it != ThemeMode.LIGHT } ?: ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            prefs[Keys.THEME_MODE]
        }
        return Theme.build(mode, accent(context, prefs[Keys.ACCENT], mode), prefs[Keys.FONT_SCALE], prefs[Keys.REDUCE_MOTION])
    }

    fun accent(context: Context, spec: String, mode: ThemeMode): Int = when {
        spec == "dyn" -> context.getColor(if (mode == ThemeMode.LIGHT) android.R.color.system_accent1_600 else android.R.color.system_accent1_300)
        spec.startsWith("h") -> Color.HSVToColor(floatArrayOf(spec.substring(1).toFloatOrNull() ?: 240f, 0.62f, if (mode == ThemeMode.LIGHT) 0.82f else 0.95f))
        spec.startsWith("p") -> Accents.all.getOrNull(spec.substring(1).toIntOrNull() ?: 0)?.color ?: Accents.default.color
        else -> Accents.default.color
    }
}
