package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.DialogRemovePlaylistBinding
import com.simplemobiletools.musicplayer.models.Playlist

class RemovePlaylistDialog(val activity: Activity, val playlist: Playlist? = null, val callback: (deleteFiles: Boolean) -> Unit) {
    private val binding by activity.viewBinding(DialogRemovePlaylistBinding::inflate)

    init {
        binding.removePlaylistDescription.text = getDescriptionText()
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ -> callback(binding.removePlaylistCheckbox.isChecked) }
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.remove_playlist)
            }
    }

    private fun getDescriptionText(): String {
        return if (playlist == null) {
            activity.getString(R.string.remove_playlist_description)
        } else
            String.format(activity.resources.getString(R.string.remove_playlist_description_placeholder), playlist.title)
    }
}
