package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.support.v7.app.AlertDialog
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.musicplayer.R
import kotlinx.android.synthetic.main.dialog_remove_playlist.view.*

class RemovePlaylistDialog(val activity: Activity, val callback: (deleteFiles: Boolean) -> Unit) : AlertDialog.Builder(activity) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_remove_playlist, null)

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, { dialog, which -> callback(view.remove_playlist_checkbox.isChecked) })
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            activity.setupDialogStuff(view, this, R.string.remove_playlist)
        }
    }
}
