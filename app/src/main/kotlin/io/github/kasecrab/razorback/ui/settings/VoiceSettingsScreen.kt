package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.orb.Orbs
import io.github.kasecrab.razorback.ui.voice.OrbPickerSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.ChoiceSheet
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.SliderView
import io.github.kasecrab.razorback.ui.widget.TopBar
import java.util.Locale

/** Which voice speaks, how fast, which ears listen, and what the orb looks like. */
class VoiceSettingsScreen(context: Context) : Screen(context) {

    private val prefs = App.instance.prefs
    private val bar = TopBar(context)
    private val voiceRow = NavRow(context)
    private val sttRow = NavRow(context)
    private val orbRow = NavRow(context)
    private val thinkingRow = NavRow(context)
    private val speedLabel = Caption(context)
    private val speed = SliderView(context)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.voice))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(32))

        list.addView(SectionHeader(context).apply { setText(R.string.voice_speaking) })
        voiceRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.voice_voice), VOICES, prefs[Keys.VOICE_TTS_VOICE]) {
                prefs[Keys.VOICE_TTS_VOICE] = it
                sync()
            }.show()
        }
        list.addView(voiceRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        speedLabel.setPadding(dp(16), dp(8), dp(16), 0)
        list.addView(speedLabel)
        speed.min = 0.7f
        speed.max = 1.5f
        speed.step = 0.05f
        speed.value = prefs[Keys.VOICE_SPEED]
        speed.onChange = {
            prefs[Keys.VOICE_SPEED] = it
            sync()
        }
        list.addView(speed, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, dp(8), 0) })

        thinkingRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.thinking), io.github.kasecrab.razorback.model.ThinkingLevel.entries.map { it.name to "${it.label} · ${it.hint}" }, prefs[Keys.VOICE_THINKING].name) {
                prefs[Keys.VOICE_THINKING] = io.github.kasecrab.razorback.model.ThinkingLevel.fromName(it)
                sync()
            }.show()
        }
        list.addView(thinkingRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.addView(SectionHeader(context).apply { setText(R.string.voice_listening) })
        sttRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.voice_stt_model), STT_MODELS, prefs[Keys.VOICE_STT_MODEL]) {
                prefs[Keys.VOICE_STT_MODEL] = it
                sync()
            }.show()
        }
        list.addView(sttRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val mute = SwitchRow(context)
        mute.set(context.getString(R.string.voice_mute_while_speaking), context.getString(R.string.voice_mute_while_speaking_hint), prefs[Keys.VOICE_MUTE_WHILE_SPEAKING]) {
            prefs[Keys.VOICE_MUTE_WHILE_SPEAKING] = it
        }
        list.addView(mute, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.addView(SectionHeader(context).apply { setText(R.string.cd_orb_style) })
        orbRow.setOnClickListener { OrbPickerSheet(context) { sync() }.show() }
        list.addView(orbRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        sync()
    }

    private fun sync() {
        val voice = prefs[Keys.VOICE_TTS_VOICE]
        voiceRow.set(R.drawable.ic_waveform, context.getString(R.string.voice_voice), VOICES.firstOrNull { it.first == voice }?.second ?: voice)
        speedLabel.text = context.getString(R.string.voice_speed, String.format(Locale.US, "%.2f", prefs[Keys.VOICE_SPEED]))
        val stt = prefs[Keys.VOICE_STT_MODEL]
        sttRow.set(R.drawable.ic_mic, context.getString(R.string.voice_stt_model), STT_MODELS.firstOrNull { it.first == stt }?.second ?: stt)
        orbRow.set(R.drawable.ic_image, context.getString(R.string.cd_orb_style), Orbs.byId(prefs[Keys.VOICE_ORB]).name)
        thinkingRow.set(R.drawable.ic_brain, context.getString(R.string.voice_thinking_row), prefs[Keys.VOICE_THINKING].label)
    }

    private companion object {
        val VOICES = listOf(
            "aura-2-thalia-en" to "Thalia · clear, confident",
            "aura-2-andromeda-en" to "Andromeda · casual, expressive",
            "aura-2-helena-en" to "Helena · caring, natural",
            "aura-2-apollo-en" to "Apollo · confident, comfortable",
            "aura-2-arcas-en" to "Arcas · natural, smooth",
            "aura-2-aries-en" to "Aries · warm, energetic",
            "aura-2-asteria-en" to "Asteria · clear, knowledgeable",
            "aura-2-athena-en" to "Athena · calm, smooth",
            "aura-2-hera-en" to "Hera · smooth, warm",
            "aura-2-luna-en" to "Luna · friendly, natural",
            "aura-2-orion-en" to "Orion · approachable, comfortable",
            "aura-2-orpheus-en" to "Orpheus · professional, clear",
            "aura-2-zeus-en" to "Zeus · deep, trustworthy",
        )
        val STT_MODELS = listOf(
            "flux-general-en" to "Flux · English",
            "flux-general-multi" to "Flux · Multilingual",
        )
    }
}
