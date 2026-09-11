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
import io.github.kasecrab.razorback.ui.voice.VoicePickerSheet
import io.github.kasecrab.razorback.voice.Speed
import io.github.kasecrab.razorback.voice.Voices
import io.github.kasecrab.razorback.ui.widget.Chip
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
    private val turnRow = NavRow(context)
    private val micRow = NavRow(context)
    private val dictationRow = NavRow(context)
    private val languageRow = NavRow(context)
    private val orbRow = NavRow(context)
    private val thinkingRow = NavRow(context)
    private val modelRow = NavRow(context)
    private val promptRow = NavRow(context)
    private val speedLabel = Caption(context)
    private val speed = SliderView(context)
    private val presets = ArrayList<Chip>(PRESETS.size)

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
        voiceRow.setOnClickListener { VoicePickerSheet(context) { sync() }.show() }
        list.addView(voiceRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        speedLabel.setPadding(dp(16), dp(8), dp(16), 0)
        list.addView(speedLabel)
        speed.min = Speed.MIN
        speed.max = Speed.MAX
        speed.step = 0.05f
        speed.value = prefs[Keys.VOICE_SPEED]
        speed.onChange = {
            prefs[Keys.VOICE_SPEED] = it
            sync()
        }
        list.addView(speed, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, dp(8), 0) })
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_HORIZONTAL
        row.setPadding(dp(16), 0, dp(16), dp(8))
        for (p in PRESETS) {
            val chip = Chip(context)
            chip.style = Chip.Style.PLAIN
            chip.text = if (p == p.toInt().toFloat()) "${p.toInt()}×" else "${p}×"
            chip.setPadding(dp(10), 0, dp(10), 0)
            chip.setOnClickListener {
                prefs[Keys.VOICE_SPEED] = p
                speed.value = p
                sync()
            }
            presets.add(chip)
            row.addView(chip, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        list.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        modelRow.setOnClickListener {
            context.nav.push(io.github.kasecrab.razorback.ui.models.ModelBrowserScreen(context, select = false) { prefs[Keys.VOICE_MODEL] = it.id })
        }
        modelRow.setOnLongClickListener {
            prefs[Keys.VOICE_MODEL] = ""
            sync()
            true
        }
        list.addView(modelRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        thinkingRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.thinking), io.github.kasecrab.razorback.model.ThinkingLevel.entries.map { it.name to "${it.label} · ${it.hint}" }, prefs[Keys.VOICE_THINKING].name) {
                prefs[Keys.VOICE_THINKING] = io.github.kasecrab.razorback.model.ThinkingLevel.fromName(it)
                sync()
            }.show()
        }
        list.addView(thinkingRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        promptRow.setOnClickListener { context.nav.push(VoicePromptScreen(context)) }
        list.addView(promptRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.addView(SectionHeader(context).apply { setText(R.string.voice_listening) })
        sttRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.voice_stt_model), STT_MODELS, prefs[Keys.VOICE_STT_MODEL]) {
                prefs[Keys.VOICE_STT_MODEL] = it
                sync()
            }.show()
        }
        list.addView(sttRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        turnRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.voice_turn), TURNS.map { it.first to context.getString(it.second) }, prefs[Keys.VOICE_TURN]) {
                prefs[Keys.VOICE_TURN] = it
                sync()
            }.show()
        }
        list.addView(turnRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        micRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.voice_mic), MICS.map { it.first to context.getString(it.second) }, prefs[Keys.VOICE_MIC]) {
                prefs[Keys.VOICE_MIC] = it
                sync()
            }.show()
        }
        list.addView(micRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        list.addView(SectionHeader(context).apply { setText(R.string.voice_dictation) })
        dictationRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.voice_dictation_model), DICTATION_MODELS, prefs[Keys.VOICE_DICTATION_MODEL]) {
                prefs[Keys.VOICE_DICTATION_MODEL] = it
                sync()
            }.show()
        }
        list.addView(dictationRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        languageRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.voice_language), LANGUAGES, prefs[Keys.VOICE_LANGUAGE]) {
                prefs[Keys.VOICE_LANGUAGE] = it
                sync()
            }.show()
        }
        list.addView(languageRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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

    override fun onResume() = sync()

    private fun sync() {
        val voice = prefs[Keys.VOICE_TTS_VOICE]
        val v = Voices.byId(voice)
        voiceRow.set(R.drawable.ic_waveform, context.getString(R.string.voice_voice), if (v != null) "${v.name} · ${v.accent} · ${v.traits.lowercase()}" else voice)
        val sp = prefs[Keys.VOICE_SPEED]
        speedLabel.text = context.getString(R.string.voice_speed, String.format(Locale.US, "%.2f", sp))
        for ((i, chip) in presets.withIndex()) chip.active = kotlin.math.abs(PRESETS[i] - sp) < 0.01f
        val stt = prefs[Keys.VOICE_STT_MODEL]
        sttRow.set(R.drawable.ic_mic, context.getString(R.string.voice_stt_model), STT_MODELS.firstOrNull { it.first == stt }?.second ?: stt)
        val turn = prefs[Keys.VOICE_TURN]
        turnRow.set(R.drawable.ic_check, context.getString(R.string.voice_turn), context.getString(TURNS.firstOrNull { it.first == turn }?.second ?: R.string.voice_turn_balanced))
        val mic = prefs[Keys.VOICE_MIC]
        micRow.set(R.drawable.ic_waveform, context.getString(R.string.voice_mic), context.getString(MICS.firstOrNull { it.first == mic }?.second ?: R.string.voice_mic_call))
        val dm = prefs[Keys.VOICE_DICTATION_MODEL]
        dictationRow.set(R.drawable.ic_edit, context.getString(R.string.voice_dictation_model), DICTATION_MODELS.firstOrNull { it.first == dm }?.second ?: dm)
        val lang = prefs[Keys.VOICE_LANGUAGE]
        languageRow.set(R.drawable.ic_globe, context.getString(R.string.voice_language), LANGUAGES.firstOrNull { it.first == lang }?.second ?: lang)
        orbRow.set(R.drawable.ic_image, context.getString(R.string.cd_orb_style), Orbs.byId(prefs[Keys.VOICE_ORB]).name)
        thinkingRow.set(R.drawable.ic_brain, context.getString(R.string.voice_thinking_row), prefs[Keys.VOICE_THINKING].label)
        val vm = prefs[Keys.VOICE_MODEL]
        modelRow.set(R.drawable.ic_star, context.getString(R.string.voice_model_row), if (vm.isEmpty()) context.getString(R.string.voice_model_same) else vm)
        promptRow.set(R.drawable.ic_edit, context.getString(R.string.voice_prompt_row), context.getString(if (prefs[Keys.VOICE_PROMPT].isBlank()) R.string.voice_prompt_builtin else R.string.voice_prompt_custom))
    }

    private companion object {
        val PRESETS = floatArrayOf(1f, 1.5f, 2f, 2.5f, 3f)
        val STT_MODELS = listOf(
            "nova-3" to "Nova-3 · hears as accurately as dictation, uses the language below",
            "flux-general-en" to "Flux · English, quickest turn-taking",
            "flux-general-multi" to "Flux · Multilingual, switches between ten languages",
        )
        val MICS = listOf(
            "call" to R.string.voice_mic_call,
            "clean" to R.string.voice_mic_clean,
        )
        val TURNS = listOf(
            "quick" to R.string.voice_turn_quick,
            "balanced" to R.string.voice_turn_balanced,
            "patient" to R.string.voice_turn_patient,
        )
        val DICTATION_MODELS = listOf(
            "nova-3" to "Nova-3 · best all round",
            "nova-3-medical" to "Nova-3 Medical · clinical vocabulary, English",
            "nova-2" to "Nova-2 · previous generation",
        )
        val LANGUAGES = listOf(
            "" to "English",
            "multi" to "Detect and switch (Nova-3)",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "hi" to "Hindi",
            "hi-Latn" to "Hindi, Latin script",
            "it" to "Italian",
            "ja" to "Japanese",
            "ko" to "Korean",
            "nl" to "Dutch",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "zh" to "Chinese",
        )
    }
}
