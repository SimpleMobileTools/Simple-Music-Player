package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.DialogNewPlaylistBinding
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.getPlaylistIdWithTitle
import com.simplemobiletools.musicplayer.models.Playlist

class NewPlaylistDialog(val activity: Activity, var playlist: Playlist? = null, val callback: (playlistId: Int) -> Unit) {
    private var isNewPlaylist = playlist == null
    private val binding by activity.viewBinding(DialogNewPlaylistBinding::inflate)

    init {
        if (playlist == null) {
            playlist = Playlist(0, "")
        }

        binding.newPlaylistTitle.setText(playlist!!.title)
        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                val dialogTitle = if (isNewPlaylist) R.string.create_new_playlist else R.string.rename_playlist
                activity.setupDialogStuff(binding.root, this, dialogTitle) { alertDialog ->
                    alertDialog.showKeyboard(binding.newPlaylistTitle)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = binding.newPlaylistTitle.value
                        ensureBackgroundThread {
                            val playlistIdWithTitle = activity.getPlaylistIdWithTitle(title)
                            var isPlaylistTitleTaken = isNewPlaylist && playlistIdWithTitle != -1
                            if (!isPlaylistTitleTaken) {
                                isPlaylistTitleTaken = !isNewPlaylist && playlist!!.id != playlistIdWithTitle && playlistIdWithTitle != -1
                            }

                            if (title.isEmpty()) {
                                activity.toast(com.simplemobiletools.commons.R.string.empty_name)
                                return@ensureBackgroundThread
                            } else if (isPlaylistTitleTaken) {
                                activity.toast(R.string.playlist_name_exists)
                                return@ensureBackgroundThread
                            }

                            playlist!!.title = title

                            val eventTypeId = if (isNewPlaylist) {
                                activity.audioHelper.insertPlaylist(playlist!!).toInt()
                            } else {
                                activity.audioHelper.updatePlaylist(playlist!!)
                                playlist!!.id
                            }

                            if (eventTypeId != -1) {
                                alertDialog.dismiss()
                                callback(eventTypeId)
                            } else {
                                activity.toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
                            }
                        }
                    }
                }
            }
    }
}
