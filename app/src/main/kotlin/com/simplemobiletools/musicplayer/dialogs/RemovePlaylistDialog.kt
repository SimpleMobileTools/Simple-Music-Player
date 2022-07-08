package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.dialog_remove_playlist.view.*

class RemovePlaylistDialog(val activity: Activity, val playlist: Playlist? = null, val callback: (deleteFiles: Boolean) -> Unit) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_remove_playlist, null).apply {
            remove_playlist_description.text = getDescriptionText()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, which -> callback(view.remove_playlist_checkbox.isChecked) }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.remove_playlist)
            }
    }

    private fun getDescriptionText(): String {
        return if (playlist == null) {
            activity.getString(R.string.remove_playlist_description)
        } else
            String.format(activity.resources.getString(R.string.remove_playlist_description_placeholder), playlist.title)
    }
}
