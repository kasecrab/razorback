package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.core.Ids
import io.github.kasecrab.razorback.core.Keys
import io.github.kasecrab.razorback.model.Prompt
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.TextArea
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The system prompt every chat starts with, and prompts saved for reuse. */
class PromptsScreen(context: Context) : Screen(context) {

    private val app = App.instance
    private val bar = TopBar(context)
    private val system = TextArea(context)
    private val saved = LinearLayout(context)
    private val empty = Caption(context)
    private var job: Job? = null

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.prompts), R.drawable.ic_plus, context.getString(R.string.cd_add))
        bar.leading.setOnClickListener { context.nav.pop() }
        bar.trailing.setOnClickListener { context.nav.push(PromptEditScreen(context, null)) }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(32))
        list.addView(SectionHeader(context).apply { setText(R.string.system_prompt) })
        system.setHint(R.string.system_prompt_hint)
        system.text = app.prefs[Keys.SYSTEM_PROMPT]
        system.onTextChanged = { app.prefs[Keys.SYSTEM_PROMPT] = it }
        list.addView(system, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), 0, dp(16), 0) })
        list.addView(SectionHeader(context).apply { setText(R.string.saved_prompts) })
        empty.setText(R.string.saved_prompts_empty)
        empty.setPadding(dp(16), 0, dp(16), dp(8))
        list.addView(empty)
        saved.orientation = LinearLayout.VERTICAL
        list.addView(saved, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onEnter() = reload()

    override fun onResume() = reload()

    override fun onExit() {
        job?.cancel()
    }

    private fun reload() {
        job?.cancel()
        job = context.uiScope.launch {
            val prompts = app.prompts.list()
            saved.removeAllViews()
            empty.visibility = if (prompts.isEmpty()) View.VISIBLE else View.GONE
            for (p in prompts) {
                val row = NavRow(context)
                row.set(R.drawable.ic_file, p.name, p.text.lineSequence().firstOrNull()?.take(80))
                row.setOnClickListener { context.nav.push(PromptEditScreen(context, p)) }
                row.setOnLongClickListener {
                    ActionSheet(context)
                        .add(R.drawable.ic_trash, context.getString(R.string.action_delete), danger = true) {
                            context.uiScope.launch {
                                app.prompts.delete(p.id)
                                reload()
                            }
                        }
                        .show()
                    true
                }
                saved.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        }
    }
}

/** Name and text of one saved prompt. */
class PromptEditScreen(context: Context, private val existing: Prompt?) : Screen(context) {

    private val bar = TopBar(context)
    private val name = io.github.kasecrab.razorback.ui.widget.TextField(context)
    private val text = TextArea(context)

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), existing?.name ?: context.getString(R.string.new_prompt), R.drawable.ic_check, context.getString(R.string.cd_save))
        bar.leading.setOnClickListener { context.nav.pop() }
        bar.trailing.setOnClickListener { save() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(16), dp(8), dp(16), dp(32))
        name.setHint(R.string.prompt_name)
        name.text = existing?.name ?: ""
        list.addView(name, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })
        text.setHint(R.string.prompt_text)
        text.text = existing?.text ?: ""
        text.edit.minLines = 8
        list.addView(text, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun save() {
        val n = name.text.trim().ifEmpty { text.text.lineSequence().firstOrNull()?.take(40)?.trim().orEmpty() }
        val t = text.text.trim()
        if (n.isEmpty() || t.isEmpty()) return
        val now = System.currentTimeMillis()
        val p = existing?.also {
            it.name = n
            it.text = t
            it.updatedAt = now
        } ?: Prompt(Ids.next(), n, t, now, now)
        context.uiScope.launch {
            App.instance.prompts.save(p)
            context.nav.pop()
        }
    }
}
