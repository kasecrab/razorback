package io.github.kasecrab.razorback.ui.models

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.TextField
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Every model from the provider, searchable and filterable; tap to use, star to keep. */
/** [select] false means a tap only reports the model through [onPicked] instead of making it the chat default. */
class ModelBrowserScreen(context: Context, private val select: Boolean = true, private val onPicked: ((ModelInfo) -> Unit)? = null) : Screen(context) {

    private val app = App.instance
    private val catalog = app.catalog
    private val favorites = app.favorites
    private val bar = TopBar(context)
    private val search = TextField(context)
    private val filters = LinearLayout(context)
    private val status = Caption(context)
    private val list = RecyclerView(context)
    private val adapter = Adapter()
    private val shown = ArrayList<ModelInfo>()
    private var query = ""
    private var loadJob: Job? = null

    private val fReasoning = filterChip(R.string.filter_reasoning)
    private val fTools = filterChip(R.string.filter_tools)
    private val fVision = filterChip(R.string.filter_vision)
    private val fImage = filterChip(R.string.filter_image_out)
    private val fFree = filterChip(R.string.filter_free)

    private val onCatalog: () -> Unit = { refresh() }
    private val onFavorites: () -> Unit = { adapter.notifyDataSetChanged() }

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.models), R.drawable.ic_refresh, context.getString(R.string.cd_refresh))
        bar.leading.setOnClickListener { context.nav.pop() }
        bar.trailing.setOnClickListener { load(force = true) }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        search.setHint(R.string.search_models)
        search.onTextChanged = {
            query = it.trim()
            refresh()
        }
        column.addView(search, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), dp(4), dp(16), dp(8)) })

        filters.orientation = LinearLayout.HORIZONTAL
        filters.setPadding(dp(16), 0, dp(16), dp(8))
        val scroller = HorizontalScrollView(context)
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(filters, ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroller, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        status.setPadding(dp(16), dp(8), dp(16), dp(8))
        status.visibility = View.GONE
        column.addView(status, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
        list.clipToPadding = false
        list.setPadding(0, 0, 0, dp(16))
        column.addView(list, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun filterChip(label: Int): Chip {
        val c = Chip(context)
        c.style = Chip.Style.OUTLINE
        c.setText(label)
        c.setOnClickListener {
            c.active = !c.active
            refresh()
        }
        filters.addView(c, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)).apply { marginEnd = dp(8) })
        return c
    }

    override fun onEnter() {
        catalog.onChange(onCatalog)
        favorites.onChange(onFavorites)
        refresh()
        load(force = false)
    }

    override fun onExit() {
        loadJob?.cancel()
        catalog.removeOnChange(onCatalog)
        favorites.removeOnChange(onFavorites)
    }

    private fun load(force: Boolean) {
        loadJob?.cancel()
        if (catalog.models.isEmpty() && App.instance.secrets.get(io.github.kasecrab.razorback.core.Secrets.OPENROUTER) == null) {
            // Nothing to fetch with: say what is missing, and let the line lead to the field.
            status.tone = Caption.Tone.ACCENT
            status.setText(R.string.models_need_key)
            status.visibility = View.VISIBLE
            status.setOnClickListener { io.github.kasecrab.razorback.ui.core.KeyNeeded.explain(context, io.github.kasecrab.razorback.core.Secrets.OPENROUTER) }
            return
        }
        status.setOnClickListener(null)
        status.isClickable = false
        if (catalog.models.isEmpty()) {
            status.tone = Caption.Tone.NORMAL
            status.setText(R.string.loading_models)
            status.visibility = View.VISIBLE
        }
        loadJob = context.uiScope.launch {
            val err = catalog.load(force)
            if (err != null && catalog.models.isEmpty()) {
                status.tone = Caption.Tone.DANGER
                status.text = err.message ?: err.javaClass.simpleName
                status.visibility = View.VISIBLE
            } else {
                status.visibility = View.GONE
            }
        }
    }

    private fun refresh() {
        shown.clear()
        val scored = ArrayList<Pair<Int, ModelInfo>>()
        for (m in catalog.models) {
            if (fReasoning.active && !m.supportsReasoning) continue
            if (fTools.active && !m.supportsTools) continue
            if (fVision.active && !m.acceptsImages) continue
            if (fImage.active && !m.producesImages) continue
            if (fFree.active && !m.isFree) continue
            if (query.isEmpty()) {
                scored.add(0 to m)
            } else {
                val s = maxOf(Fuzzy.score(query, m.id), Fuzzy.score(query, m.name))
                if (s >= 0) scored.add(s to m)
            }
        }
        if (query.isNotEmpty()) scored.sortByDescending { it.first }
        for (p in scored) shown.add(p.second)
        adapter.notifyDataSetChanged()
        if (catalog.models.isNotEmpty()) {
            status.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            if (shown.isEmpty()) {
                status.tone = Caption.Tone.NORMAL
                status.setText(R.string.no_models)
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount(): Int = shown.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = ModelRow(parent.context)
            row.layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            return Holder(row)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            context.ui().host.refresh(holder.row)
            val m = shown[position]
            holder.row.bind(m, selected = m.id == app.engine.model, favorite = favorites.contains(m.id))
            holder.row.setOnClickListener {
                if (select) app.engine.model = m.id
                onPicked?.invoke(m)
                context.nav.pop()
            }
            holder.row.star.setOnClickListener {
                favorites.toggle(m.id)
                notifyItemChanged(holder.bindingAdapterPosition)
            }
        }
    }

    private class Holder(val row: ModelRow) : RecyclerView.ViewHolder(row)
}
