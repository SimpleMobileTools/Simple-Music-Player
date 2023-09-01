package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.DialogCustomSleepTimerPickerBinding

class SleepTimerCustomDialog(val activity: Activity, val callback: (seconds: Int) -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogCustomSleepTimerPickerBinding::inflate)

    init {
        binding.minutesHint.hint = activity.getString(R.string.minutes_raw).replaceFirstChar { it.uppercaseChar() }
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.sleep_timer) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(binding.minutes)
                }
            }
    }

    private fun dialogConfirmed() {
        val value = binding.minutes.value
        val minutes = Integer.valueOf(value.ifEmpty { "0" })
        callback(minutes * 60)
        activity.hideKeyboard()
        dialog?.dismiss()
    }
}
