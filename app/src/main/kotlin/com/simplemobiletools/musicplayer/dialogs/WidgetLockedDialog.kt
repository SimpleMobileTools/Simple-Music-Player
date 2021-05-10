package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.launchPurchaseThankYouIntent
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import kotlinx.android.synthetic.main.dialog_widget_locked.view.*

class WidgetLockedDialog(val activity: Activity, val callback: () -> Unit) {
    private var dialog: AlertDialog

    init {
        val view: View = activity.layoutInflater.inflate(R.layout.dialog_widget_locked, null)
        view.widget_locked_image.applyColorFilter(activity.config.textColor)

        dialog = AlertDialog.Builder(activity)
            .setPositiveButton(R.string.purchase, null)
            .setNegativeButton(R.string.cancel) { dialog, which -> dismissDialog() }
            .setOnDismissListener { dismissDialog() }
            .create().apply {
                activity.setupDialogStuff(view, this, cancelOnTouchOutside = false) {
                    view.widget_locked_description.text = Html.fromHtml(activity.getString(R.string.widget_locked))
                    view.widget_locked_description.movementMethod = LinkMovementMethod.getInstance()

                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        activity.launchPurchaseThankYouIntent()
                    }
                }
            }
    }

    fun dismissDialog() {
        dialog.dismiss()
        callback()
    }
}
