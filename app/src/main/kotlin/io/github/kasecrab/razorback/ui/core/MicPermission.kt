package io.github.kasecrab.razorback.ui.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.kasecrab.razorback.R
import io.github.kasecrab.razorback.ui.widget.ActionSheet

/** What to show when the microphone was refused: why it is needed, and the way to allow it. */
object MicPermission {
    fun explain(context: Context) {
        ActionSheet(context)
            .header(context.getString(R.string.mic_needed_title), context.getString(R.string.mic_needed_text))
            .add(R.drawable.ic_settings, context.getString(R.string.open_app_settings)) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            .add(R.drawable.ic_close, context.getString(R.string.not_now)) {}
            .show()
    }
}
