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
    /** Which search vendor answers first, "brave" or "exa"; the other one is the backup. */
    val SEARCH_PROVIDER = Key("tools.search_provider", Secrets.BRAVE)

    /** Ears for voice mode: "nova-3" (same accuracy as dictation), "flux-general-en" or "flux-general-multi" (built-in turn taking). */
    val VOICE_STT_MODEL = Key("voice.stt_model", "nova-3")
    /**
     * "call" goes through the phone's call path, whose echo canceller takes the speaker out
     * of what the microphone hears, so the person can cut in over a reply; "clean" records
     * plainly, as dictation does, and hears a little more faithfully.
     */
    val VOICE_MIC = Key("voice.mic", "call")
    /** How sure Flux must be that the person has finished before the model answers: quick, balanced, patient. */
    val VOICE_TURN = Key("voice.turn", "balanced")
    /** Nova model for typing by voice. */
    val VOICE_DICTATION_MODEL = Key("voice.dictation_model", "nova-3")
    /** Language for typing by voice: empty is English, "multi" detects and switches. */
    val VOICE_LANGUAGE = Key("voice.language", "")
    val VOICE_TTS_VOICE = Key("voice.tts_voice", "aura-2-thalia-en")
    val VOICE_SPEED = Key("voice.speed", 1.0f)
    val VOICE_MUTE_WHILE_SPEAKING = Key("voice.mute_while_speaking", false)
    val VOICE_ORB = Key("voice.orb", "sol")
    /** Spoken replies want to start fast; deep reasoning is opt-in here. */
    val VOICE_THINKING = EnumKey("voice.thinking", ThinkingLevel.OFF, ThinkingLevel.entries.toTypedArray())
    /** Empty means the chat model; a fast small model makes spoken replies start sooner. */
    /** The model voice mode talks to; empty means the same as the chat. */
    val VOICE_MODEL = Key("voice.model", "openai/gpt-oss-120b")
    /** How the model should talk in voice mode; empty means the built-in text. */
    val VOICE_PROMPT = Key("voice.prompt", "")

    val THEME_MODE = EnumKey("theme.mode", ThemeMode.DARK, ThemeMode.entries.toTypedArray())
    val THEME_FOLLOW_SYSTEM = Key("theme.follow_system", true)
    /** "p<index>" for a palette colour, "dyn" for wallpaper colours, "h<degrees>" for a custom hue. */
    val ACCENT = Key("theme.accent", "p0")
    val FONT_SCALE = Key("theme.font_scale", 1.0f)
    val REDUCE_MOTION = Key("theme.reduce_motion", false)
}
