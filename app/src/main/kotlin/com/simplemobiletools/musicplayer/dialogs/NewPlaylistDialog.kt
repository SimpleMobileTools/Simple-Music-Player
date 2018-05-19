package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.support.v7.app.AlertDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.showKeyboard
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.extensions.songsDB
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.dialog_new_playlist.view.*

class NewPlaylistDialog(val activity: Activity, var playlist: Playlist? = null, val callback: (playlistId: Int) -> Unit) {
    var isNewPlaylist = playlist == null

    init {
        if (playlist == null)
            playlist = Playlist(0, "")

        val view = activity.layoutInflater.inflate(R.layout.dialog_new_playlist, null).apply {
            new_playlist_title.setText(playlist!!.title)
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
                    val dialogTitle = if (isNewPlaylist) R.string.create_playlist else R.string.rename_playlist
                    activity.setupDialogStuff(view, this, dialogTitle) {
                        showKeyboard(view.new_playlist_title)
                        getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val title = view.new_playlist_title.value
                            val playlistIdWithTitle = activity.dbHelper.getPlaylistIdWithTitle(title)
                            var isPlaylistTitleTaken = isNewPlaylist && playlistIdWithTitle != -1
                            if (!isPlaylistTitleTaken)
                                isPlaylistTitleTaken = !isNewPlaylist && playlist!!.id != playlistIdWithTitle && playlistIdWithTitle != -1

                            if (title.isEmpty()) {
                                activity.toast(R.string.empty_name)
                                return@setOnClickListener
                            } else if (isPlaylistTitleTaken) {
                                activity.toast(R.string.playlist_name_exists)
                                return@setOnClickListener
                            }

                            playlist!!.title = title

                            Thread {
                                val eventTypeId = if (isNewPlaylist) {
                                    activity.songsDB.PlaylistsDao().insert(playlist!!).toInt()
                                } else {
                                    activity.dbHelper.updatePlaylist(playlist!!)
                                }

                                if (eventTypeId != -1) {
                                    dismiss()
                                    callback(eventTypeId)
                                } else {
                                    activity.toast(R.string.unknown_error_occurred)
                                }
                            }.start()
                        }
                    }
                }
    }
}
