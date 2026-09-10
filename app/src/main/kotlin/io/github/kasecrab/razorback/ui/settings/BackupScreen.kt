package io.github.kasecrab.razorback.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import io.github.kasecrab.razorback.App
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.backup.Exporter
import io.github.kasecrab.razorback.backup.Importer
import io.github.kasecrab.razorback.ui.core.Screen
import io.github.kasecrab.razorback.ui.core.dp
import io.github.kasecrab.razorback.ui.core.nav
import io.github.kasecrab.razorback.ui.core.ui
import io.github.kasecrab.razorback.ui.core.uiScope
import io.github.kasecrab.razorback.ui.widget.ActionSheet
import io.github.kasecrab.razorback.ui.widget.Caption
import io.github.kasecrab.razorback.ui.widget.TopBar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupScreen(context: Context) : Screen(context) {

    private val bar = TopBar(context)
    private val status = Caption(context)
    private var includeKeys = false

    init {
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        bar.set(R.drawable.ic_arrow_back, context.getString(R.string.cd_back), context.getString(R.string.backup))
        bar.leading.setOnClickListener { context.nav.pop() }
        column.addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        row(list, R.drawable.ic_download, R.string.backup_export_full, R.string.backup_export_full_hint) {
            create("razorback-${stamp()}.zip", "application/zip") { uri -> Exporter.exportFull(context, uri) }
        }
        row(list, R.drawable.ic_settings, R.string.backup_export_settings, R.string.backup_export_settings_hint) {
            create("razorback-settings-${stamp()}.json", "application/json") { uri -> Exporter.exportSettings(context, uri, includeKeys) }
        }
        val keys = SwitchRow(context)
        keys.set(context.getString(R.string.backup_include_keys), null, includeKeys) { includeKeys = it }
        list.addView(keys, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        row(list, R.drawable.ic_file, R.string.backup_import, R.string.backup_import_hint) { pickImport() }
        status.setPadding(dp(16), dp(12), dp(16), dp(12))
        list.addView(status)
        val scroll = ScrollView(context)
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun row(list: LinearLayout, icon: Int, title: Int, subtitle: Int, onClick: () -> Unit) {
        val r = NavRow(context)
        r.set(icon, context.getString(title), context.getString(subtitle))
        r.setOnClickListener { onClick() }
        list.addView(r, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    private fun create(name: String, mime: String, write: suspend (android.net.Uri) -> Unit) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name)
        context.ui().results.start(intent) { code, data ->
            val uri = data?.data
            if (code != Activity.RESULT_OK || uri == null) return@start
            run { write(uri) }
        }
    }

    private fun pickImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/json", "application/octet-stream"))
        context.ui().results.start(intent) { code, data ->
            val uri = data?.data
            if (code != Activity.RESULT_OK || uri == null) return@start
            ActionSheet(context)
                .add(R.drawable.ic_download, context.getString(R.string.backup_merge)) { run { finishImport(Importer.import(context, uri, Importer.Mode.MERGE)) } }
                .add(R.drawable.ic_trash, context.getString(R.string.backup_replace), danger = true) { run { finishImport(Importer.import(context, uri, Importer.Mode.REPLACE)) } }
                .show()
        }
    }

    private fun finishImport(r: Importer.Result) {
        App.instance.engine.newConversation()
        App.instance.favorites.reload()
        val parts = r.tables.entries.joinToString(", ") { "${it.value} ${it.key}" }
        status.text = context.getString(R.string.backup_done) + if (parts.isNotEmpty()) ": $parts" else ""
    }

    private fun run(block: suspend () -> Unit) {
        status.tone = Caption.Tone.NORMAL
        status.setText(R.string.backup_working)
        context.uiScope.launch {
            val r = runCatching { block() }
            r.onSuccess { if (status.text == context.getString(R.string.backup_working)) status.setText(R.string.backup_done) }
            r.onFailure {
                status.tone = Caption.Tone.DANGER
                status.text = context.getString(R.string.backup_failed, it.message ?: it.javaClass.simpleName)
                Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
