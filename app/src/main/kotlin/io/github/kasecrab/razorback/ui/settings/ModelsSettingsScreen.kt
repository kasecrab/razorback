package io.github.kasecrab.razorback.ui.settings

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.model.ThinkingLevel
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.models.ModelBrowserScreen
import io.github.kasecrab.razorback.ui.models.ModelRow
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.ChoiceSheet
import io.github.kasecrab.razorback.ui.widget.SectionHeader
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.launch

/** Favourites in order, each with its own thinking level, and the model new chats start with. */
class ModelsSettingsScreen(context: Context) : Screen(context) {

    private val app = App.instance
    private val bar = TopBar(context)
    private val defaultRow = NavRow(context)
    private val thinkingRow = NavRow(context)
    private val favs = LinearLayout(context)
    private val empty = Caption(context)
    private val onFavorites: () -> Unit = { rebuild() }
    private val onCatalog: () -> Unit = { rebuild() }

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.models), R.drawable.ic_search, context.getString(R.string.browse_models))
        bar.leading.setOnClickListener { context.nav.pop() }
        bar.trailing.setOnClickListener { context.nav.push(ModelBrowserScreen(context)) }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(32))
        list.addView(SectionHeader(context).apply { setText(R.string.models_defaults) })
        defaultRow.setOnClickListener { context.nav.push(ModelBrowserScreen(context, onPicked = { rebuild() })) }
        list.addView(defaultRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        thinkingRow.setOnClickListener {
            ChoiceSheet(context, context.getString(R.string.thinking), ThinkingLevel.entries.map { it.name to "${it.label} · ${it.hint}" }, app.engine.thinking.name) {
                app.engine.thinking = ThinkingLevel.fromName(it)
                rebuild()
            }.show()
        }
        list.addView(thinkingRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        list.addView(SectionHeader(context).apply { setText(R.string.favorites) })
        empty.setText(R.string.no_favorites_hint)
        empty.setPadding(dp(16), 0, dp(16), dp(8))
        list.addView(empty)
        favs.orientation = LinearLayout.VERTICAL
        list.addView(favs, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val hint = Caption(context)
        hint.setText(R.string.favorites_hint)
        hint.setPadding(dp(16), dp(8), dp(16), 0)
        list.addView(hint)
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onEnter() {
        app.favorites.onChange(onFavorites)
        app.catalog.onChange(onCatalog)
        rebuild()
        context.uiScope.launch { app.catalog.load() }
    }

    override fun onResume() = rebuild()

    override fun onExit() {
        app.favorites.removeOnChange(onFavorites)
        app.catalog.removeOnChange(onCatalog)
    }

    private fun info(id: String): ModelInfo = app.catalog.find(id) ?: ModelInfo(id, id.substringAfter('/'), 0, 0.0, 0.0, 0.0, true, true, setOf("text"), setOf("text"))

    private fun rebuild() {
        val model = app.engine.model
        defaultRow.set(R.drawable.ic_star, context.getString(R.string.default_model), info(model).name)
        thinkingRow.set(R.drawable.ic_brain, context.getString(R.string.thinking), app.engine.thinking.label)
        favs.removeAllViews()
        val all = app.favorites.all
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        for ((i, f) in all.withIndex()) {
            val row = ModelRow(context)
            val m = info(f.id)
            row.bind(m, selected = f.id == model, favorite = true)
            row.star.setOnClickListener { Haptics.toggle(app.favorites.toggle(f.id)) }
            row.setOnClickListener { menu(i, f.id, m) }
            favs.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun menu(index: Int, id: String, m: ModelInfo) {
        val sheet = ActionSheet(context)
        sheet.add(R.drawable.ic_check, context.getString(R.string.use_as_default)) { app.engine.model = id; rebuild() }
        val pinned = app.favorites.get(id)?.thinking
        sheet.add(R.drawable.ic_brain, context.getString(R.string.pin_thinking, pinned?.label ?: context.getString(R.string.none))) {
            val options = listOf("" to context.getString(R.string.none)) + ThinkingLevel.entries.map { it.name to it.label }
            ChoiceSheet(context, context.getString(R.string.thinking), options, pinned?.name ?: "") {
                app.favorites.setThinking(id, if (it.isEmpty()) null else ThinkingLevel.fromName(it))
            }.show()
        }
        if (index > 0) sheet.add(R.drawable.ic_arrow_up, context.getString(R.string.move_up)) { app.favorites.move(index, index - 1) }
        sheet.add(R.drawable.ic_trash, context.getString(R.string.action_delete), danger = true) { app.favorites.toggle(id) }
        sheet.show()
    }
}
