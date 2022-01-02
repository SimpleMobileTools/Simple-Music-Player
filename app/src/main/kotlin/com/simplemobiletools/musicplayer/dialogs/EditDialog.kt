package com.simplemobiletools.musicplayer.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.helpers.TagHelper
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.dialog_rename_song.*
import kotlinx.android.synthetic.main.dialog_rename_song.view.*

class EditDialog(val activity: BaseSimpleActivity, val track: Track, val callback: (track: Track) -> Unit) {
    private val tagHelper = TagHelper(activity)

    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_rename_song, null).apply {
            song_title.setText(track.title)
            song_artist.setText(track.artist)
            song_album.setText(track.album)
            val filename = track.path.getFilenameFromPath()
            file_name.setText(filename.substring(0, filename.lastIndexOf(".")))
            extension.setText(track.path.getFilenameExtension())
            if (isRPlus()) {
                arrayOf(file_name_label, file_name, extension_label, extension).forEach {
                    it.beGone()
                }
            }
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.rename_song) {
                    showKeyboard(song_title)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = view.song_title.value
                        val newArtist = view.song_artist.value
                        val newAlbum = view.song_album.value
                        val newFilename = view.file_name.value
                        val newFileExtension = view.extension.value

                        if (newTitle.isEmpty() || newArtist.isEmpty() || newFilename.isEmpty() || newFileExtension.isEmpty()) {
                            activity.toast(R.string.rename_song_empty)
                            return@setOnClickListener
                        }

                        if (track.title != newTitle || track.artist != newArtist || track.album != newAlbum) {
                            updateContentResolver(track, newArtist, newTitle, newAlbum) {
                                track.artist = newArtist
                                track.title = newTitle
                                track.album = newAlbum
                                val oldPath = track.path
                                val newPath = "${oldPath.getParentPath()}/$newFilename.$newFileExtension"
                                if (oldPath == newPath) {
                                    storeEditedSong(track, oldPath, newPath)
                                    callback(track)
                                    dismiss()
                                    return@updateContentResolver
                                }

                                if (!isRPlus()) {
                                    activity.renameFile(oldPath, newPath, false) { success, andd ->
                                        if (success) {
                                            storeEditedSong(track, oldPath, newPath)
                                            track.path = newPath
                                            callback(track)
                                        } else {
                                            activity.toast(R.string.rename_song_error)
                                        }
                                        dismiss()
                                    }
                                }
                            }
                        } else {
                            dismiss()
                        }
                    }
                }
            }
    }

    private fun storeEditedSong(track: Track, oldPath: String, newPath: String) {
        ensureBackgroundThread {
            try {
                activity.tracksDAO.updateSongInfo(newPath, track.artist, track.title, oldPath)
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
        }
    }

    private fun updateContentResolver(track: Track, newArtist: String, newTitle: String, newAlbum: String, onUpdateMediaStore: () -> Unit) {
        ensureBackgroundThread {
            try {
                activity.handleRecoverableSecurityException { granted ->
                    if (granted) {
                        tagHelper.writeTag(track, newArtist, newTitle, newAlbum)
                        activity.runOnUiThread {
                            onUpdateMediaStore.invoke()
                        }
                    }
                }
            } catch (e: Exception) {
                activity.toast(R.string.unknown_error_occurred)
            }
        }
    }
}
