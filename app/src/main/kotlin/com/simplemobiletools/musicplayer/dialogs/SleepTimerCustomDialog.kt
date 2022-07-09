package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.R
import kotlinx.android.synthetic.main.dialog_custom_sleep_timer_picker.view.*

class SleepTimerCustomDialog(val activity: Activity, val callback: (seconds: Int) -> Unit) {
    private var dialog: AlertDialog? = null
    private val view = activity.layoutInflater.inflate(R.layout.dialog_custom_sleep_timer_picker, null)

    init {
        view.minutes_hint.hint = activity.getString(R.string.minutes_raw).replaceFirstChar { it.uppercaseChar() }
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.sleep_timer) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(view.minutes)
                }
            }
    }

    private fun dialogConfirmed() {
        val value = view.minutes.value
        val minutes = Integer.valueOf(if (value.isEmpty()) "0" else value)
        callback(minutes * 60)
        activity.hideKeyboard()
        dialog?.dismiss()
    }
}
