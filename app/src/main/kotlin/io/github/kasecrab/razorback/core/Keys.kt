package io.github.kasecrab.razorback.core

import io.github.kasecrab.razorback.model.ThinkingLevel
import io.github.kasecrab.razorback.ui.core.ThemeMode

/** Every preference key in one place, with its default. */
object Keys {
    val MODEL = Key("model.id", "deepseek/deepseek-v4-flash-0731")
    val THINKING = EnumKey("model.thinking", ThinkingLevel.OFF, ThinkingLevel.entries.toTypedArray())
    val MAX_TOKENS = Key("model.max_tokens", 8192)
    val SYSTEM_PROMPT = Key("prompt.system", "")
    val FAVORITES = Key("model.favorites", "")

    val THEME_MODE = EnumKey("theme.mode", ThemeMode.DARK, ThemeMode.entries.toTypedArray())
    val THEME_FOLLOW_SYSTEM = Key("theme.follow_system", true)
    val ACCENT = Key("theme.accent", 0)
    val FONT_SCALE = Key("theme.font_scale", 1.0f)
    val REDUCE_MOTION = Key("theme.reduce_motion", false)
}
