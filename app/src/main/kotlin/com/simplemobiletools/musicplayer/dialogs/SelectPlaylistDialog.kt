package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.dialog_select_playlist.view.dialog_select_playlist_linear
import kotlinx.android.synthetic.main.dialog_select_playlist.view.dialog_select_playlist_new_radio
import kotlinx.android.synthetic.main.item_select_playlist.view.select_playlist_item_radio_button

class SelectPlaylistDialog(val activity: Activity, val callback: (playlistId: Int) -> Unit) {
    private var dialog: AlertDialog? = null

    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_select_playlist, null)
        ensureBackgroundThread {
            val playlists = activity.audioHelper.getAllPlaylists()
            activity.runOnUiThread {
                initDialog(playlists, view)

                if (playlists.isEmpty()) {
                    showNewPlaylistDialog()
                }
            }
        }

        view.dialog_select_playlist_new_radio.setOnClickListener {
            view.dialog_select_playlist_new_radio.isChecked = false
            showNewPlaylistDialog()
        }
    }

    private fun initDialog(playlists: ArrayList<Playlist>, view: View) {
        playlists.forEach {
            activity.layoutInflater.inflate(R.layout.item_select_playlist, null).apply {
                val playlist = it
                select_playlist_item_radio_button.apply {
                    text = playlist.title
                    isChecked = false
                    id = playlist.id

                    setOnClickListener {
                        callback(playlist.id)
                        dialog?.dismiss()
                    }
                }

                view.dialog_select_playlist_linear.addView(
                    this,
                    RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }
        }

        activity.getAlertDialogBuilder().apply {
            activity.setupDialogStuff(view, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }

    private fun showNewPlaylistDialog() {
        NewPlaylistDialog(activity) {
            callback(it)
            dialog?.dismiss()
        }
    }
}
