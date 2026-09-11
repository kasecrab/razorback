package io.github.kasecrab.razorback.ui.chat

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.ui.core.Fonts
import io.github.kasecrab.razorback.ui.core.Haptics
import io.github.kasecrab.razorback.ui.core.Theme
import io.github.kasecrab.razorback.ui.core.Type
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.models.ModelBrowserScreen
import io.github.kasecrab.razorback.ui.models.ModelRow
import io.github.kasecrab.razorback.ui.settings.NavRow
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Sheet

/** Favourite models to switch between quickly, with a door to the full browser. */
class ModelPickerSheet(context: Context) : Sheet(context) {

    private val app = App.instance
    private val title = TextView(context)

    init {
        title.typeface = Fonts.medium
        title.setText(R.string.models)
        title.setPadding(dp(20), dp(4), dp(20), dp(8))
        body.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val rows = LinearLayout(context)
        rows.orientation = LinearLayout.VERTICAL
        val favs = app.favorites.all
        if (favs.isEmpty()) {
            val hint = Caption(context)
            hint.setText(R.string.no_favorites_hint)
            hint.setPadding(dp(20), dp(8), dp(20), dp(12))
            rows.addView(hint)
        }
        val current = app.engine.model
        val ids = LinkedHashSet<String>()
        if (favs.none { it.id == current }) ids.add(current)
        for (f in favs) ids.add(f.id)
        for (id in ids) {
            val info = app.catalog.find(id) ?: placeholder(id)
            val row = ModelRow(context)
            row.bind(info, selected = id == current, favorite = app.favorites.contains(id))
            row.setOnClickListener {
                Haptics.confirm(row)
                app.engine.model = id
                app.favorites.get(id)?.thinking?.let {
                    app.engine.thinking = it
                    app.engine.fitThinking()
                }
                dismiss()
            }
            row.star.setOnClickListener {
                Haptics.toggle(row.star, app.favorites.toggle(id))
                row.bind(info, selected = id == app.engine.model, favorite = app.favorites.contains(id))
            }
            rows.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        val browse = NavRow(context)
        browse.set(R.drawable.ic_search, context.getString(R.string.browse_models))
        browse.setOnClickListener {
            dismiss()
            context.nav.push(ModelBrowserScreen(context))
        }
        rows.addView(browse, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun placeholder(id: String) = ModelInfo(id, id.substringAfter('/'), 0, 0.0, 0.0, 0.0, true, true, setOf("text"), setOf("text"))

    override fun onThemeChanged(theme: Theme) {
        super.onThemeChanged(theme)
        title.setTextColor(theme.textPrimary)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(Type.TITLE))
    }
}
