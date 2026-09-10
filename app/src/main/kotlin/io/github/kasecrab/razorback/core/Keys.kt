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
    val WEB_SEARCH = Key("tools.web_search", true)

    val VOICE_STT_MODEL = Key("voice.stt_model", "flux-general-en")
    val VOICE_TTS_VOICE = Key("voice.tts_voice", "aura-2-thalia-en")
    val VOICE_SPEED = Key("voice.speed", 1.0f)
    val VOICE_MUTE_WHILE_SPEAKING = Key("voice.mute_while_speaking", false)
    val VOICE_ORB = Key("voice.orb", "sol")

    val THEME_MODE = EnumKey("theme.mode", ThemeMode.DARK, ThemeMode.entries.toTypedArray())
    val THEME_FOLLOW_SYSTEM = Key("theme.follow_system", true)
    /** "p<index>" for a palette colour, "dyn" for wallpaper colours, "h<degrees>" for a custom hue. */
    val ACCENT = Key("theme.accent", "p0")
    val FONT_SCALE = Key("theme.font_scale", 1.0f)
    val REDUCE_MOTION = Key("theme.reduce_motion", false)
}
