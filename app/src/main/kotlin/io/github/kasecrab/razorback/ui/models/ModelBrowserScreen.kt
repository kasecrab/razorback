package io.github.kasecrab.razorback.ui.models

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.model.ModelInfo
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.Chip
import io.github.kasecrab.razorback.ui.widget.SearchBox
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where a model is found. Opens on the router's most used, with the smartest, the best
 * value, the free and the newest a tap away, and the whole list last; typing searches
 * everything. Tap to use, star to keep, hold for the model's page on the router.
 * [select] false means a tap only reports the model through [onPicked] instead of making it the chat default.
 */
class ModelBrowserScreen(context: Context, private val select: Boolean = true, private val onPicked: ((ModelInfo) -> Unit)? = null) : Screen(context) {

    private val app = App.instance
    private val catalog = app.catalog
    private val favorites = app.favorites
    private val bar = TopBar(context)
    private val search = SearchBox(context)
    private val tabs = LinearLayout(context)
    private val tabRow = HorizontalScrollView(context)
    private val hint = Caption(context)
    private val status = Caption(context)
    private val list = RecyclerView(context)
    private val adapter = Adapter()
    private val shown = ArrayList<ModelInfo>()
    private val chips = ArrayList<Pair<Ranking.Tab, Chip>>(6)
    private var tab = Ranking.Tab.POPULAR
    private var query = ""
    private var loadJob: Job? = null
    private var popularJob: Job? = null
    private val monthFmt = SimpleDateFormat("MMM yyyy", Locale.getDefault())

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
        column.addView(search, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), 0, dp(12), dp(8)) })

        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.setPadding(dp(12), 0, dp(12), 0)
        for ((t, label) in listOf(
            Ranking.Tab.POPULAR to R.string.tab_popular,
            Ranking.Tab.SMARTEST to R.string.tab_smartest,
            Ranking.Tab.VALUE to R.string.tab_value,
            Ranking.Tab.FREE to R.string.tab_free,
            Ranking.Tab.NEW to R.string.tab_new,
            Ranking.Tab.ALL to R.string.tab_all,
        )) {
            val c = Chip(context)
            c.setText(label)
            c.setOnClickListener { show(t) }
            tabs.addView(c, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)).apply { marginEnd = dp(8) })
            chips.add(t to c)
        }
        tabRow.isHorizontalScrollBarEnabled = false
        tabRow.addView(tabs, ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        column.addView(tabRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        hint.setPadding(dp(16), dp(10), dp(16), dp(4))
        column.addView(hint, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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
        show(tab)
    }

    override fun onEnter() {
        catalog.onChange(onCatalog)
        favorites.onChange(onFavorites)
        refresh()
        load(force = false)
    }

    override fun onExit() {
        loadJob?.cancel()
        popularJob?.cancel()
        catalog.removeOnChange(onCatalog)
        favorites.removeOnChange(onFavorites)
    }

    private fun show(t: Ranking.Tab) {
        tab = t
        for ((k, c) in chips) c.active = k == t
        hint.setText(
            when (t) {
                Ranking.Tab.POPULAR -> R.string.tab_popular_hint
                Ranking.Tab.SMARTEST -> R.string.tab_smartest_hint
                Ranking.Tab.VALUE -> R.string.tab_value_hint
                Ranking.Tab.FREE -> R.string.tab_free_hint
                Ranking.Tab.NEW -> R.string.tab_new_hint
                Ranking.Tab.ALL -> R.string.tab_all_hint
            },
        )
        if (t == Ranking.Tab.POPULAR && catalog.popular.isEmpty()) loadPopular(force = false)
        refresh()
        list.scrollToPosition(0)
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
        if (force) loadPopular(force = true)
        loadJob = context.uiScope.launch {
            val err = catalog.load(force)
            if (err != null && catalog.models.isEmpty()) {
                status.tone = Caption.Tone.DANGER
                status.text = err.message ?: err.javaClass.simpleName
                status.visibility = View.VISIBLE
            } else {
                status.visibility = View.GONE
                refresh()
            }
        }
    }

    /** The most used list is asked for on its own, so the rest of the browser never waits on it. */
    private fun loadPopular(force: Boolean) {
        popularJob?.cancel()
        popularJob = context.uiScope.launch {
            val err = catalog.loadPopular(force)
            if (err != null && catalog.popular.isEmpty() && tab == Ranking.Tab.POPULAR && query.isEmpty()) {
                status.tone = Caption.Tone.DANGER
                status.text = err.message ?: err.javaClass.simpleName
                status.visibility = View.VISIBLE
            }
            refresh()
        }
    }

    private fun refresh() {
        shown.clear()
        val models = catalog.models
        val searching = query.isNotEmpty()
        tabRow.visibility = if (searching) View.GONE else View.VISIBLE
        hint.visibility = if (searching) View.GONE else View.VISIBLE
        if (searching) {
            val scored = ArrayList<Pair<Int, ModelInfo>>()
            for (m in Ranking.all(models)) {
                val s = maxOf(Fuzzy.score(query, m.id), Fuzzy.score(query, m.name))
                if (s >= 0) scored.add(s to m)
            }
            scored.sortByDescending { it.first }
            for (p in scored) shown.add(p.second)
        } else {
            shown.addAll(
                when (tab) {
                    Ranking.Tab.POPULAR -> Ranking.popular(models, catalog.popular)
                    Ranking.Tab.SMARTEST -> Ranking.smartest(models)
                    Ranking.Tab.VALUE -> Ranking.value(models)
                    Ranking.Tab.FREE -> Ranking.free(models)
                    Ranking.Tab.NEW -> Ranking.newest(models)
                    Ranking.Tab.ALL -> Ranking.all(models)
                },
            )
        }
        adapter.notifyDataSetChanged()
        if (models.isNotEmpty()) {
            val waiting = !searching && tab == Ranking.Tab.POPULAR && catalog.popular.isEmpty() && popularJob?.isActive == true
            status.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            if (shown.isEmpty()) {
                status.tone = Caption.Tone.NORMAL
                status.setText(if (waiting) R.string.loading_models else R.string.no_models)
            }
        }
    }

    /** What the row says under the name, beyond the price: the number the list is ordered by. */
    private fun note(m: ModelInfo): String? = when {
        query.isNotEmpty() -> null
        tab == Ranking.Tab.NEW -> age(m.created)
        tab == Ranking.Tab.SMARTEST || tab == Ranking.Tab.VALUE || tab == Ranking.Tab.FREE ->
            m.intelligence?.let { context.getString(R.string.model_score, it.toInt()) }
        else -> null
    }

    private fun age(createdSeconds: Long): String? {
        if (createdSeconds <= 0L) return null
        val days = ((System.currentTimeMillis() / 1000 - createdSeconds) / 86_400).toInt()
        return when {
            days < 1 -> context.getString(R.string.age_today)
            days < 7 -> context.resources.getQuantityString(R.plurals.age_days, days, days)
            days < 60 -> context.resources.getQuantityString(R.plurals.age_weeks, days / 7, days / 7)
            else -> monthFmt.format(Date(createdSeconds * 1000))
        }
    }

    /** Holding a row offers the model's page on the router and its id for pasting elsewhere. */
    private fun more(m: ModelInfo) {
        ActionSheet(context)
            .add(R.drawable.ic_external, context.getString(R.string.open_on_openrouter)) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/${m.id}"))
                runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show() }
            }
            .add(R.drawable.ic_copy, context.getString(R.string.copy_model_id)) {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("model", m.id))
            }
            .show()
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
            holder.row.bind(m, selected = m.id == app.engine.model, favorite = favorites.contains(m.id), note = note(m))
            holder.row.setOnClickListener {
                if (select) app.engine.model = m.id
                onPicked?.invoke(m)
                context.nav.pop()
            }
            holder.row.setOnLongClickListener {
                more(m)
                true
            }
            holder.row.star.setOnClickListener {
                favorites.toggle(m.id)
                notifyItemChanged(holder.bindingAdapterPosition)
            }
        }
    }

    private class Holder(val row: ModelRow) : RecyclerView.ViewHolder(row)
}
