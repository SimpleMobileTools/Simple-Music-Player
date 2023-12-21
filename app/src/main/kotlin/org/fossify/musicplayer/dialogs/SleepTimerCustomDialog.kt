package org.fossify.musicplayer.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.*
import org.fossify.musicplayer.databinding.DialogCustomSleepTimerPickerBinding

class SleepTimerCustomDialog(val activity: Activity, val callback: (seconds: Int) -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogCustomSleepTimerPickerBinding::inflate)

    init {
        binding.minutesHint.hint = activity.getString(org.fossify.commons.R.string.minutes_raw).replaceFirstChar { it.uppercaseChar() }
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, org.fossify.commons.R.string.sleep_timer) { alertDialog ->
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
