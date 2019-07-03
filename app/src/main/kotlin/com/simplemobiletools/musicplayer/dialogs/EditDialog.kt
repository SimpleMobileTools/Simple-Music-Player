package com.simplemobiletools.musicplayer.dialogs

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.songsDAO
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.dialog_rename_song.*
import kotlinx.android.synthetic.main.dialog_rename_song.view.*

class EditDialog(val activity: BaseSimpleActivity, val song: Song, val callback: (Song) -> Unit) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_rename_song, null).apply {
            song_title.setText(song.title)
            song_artist.setText(song.artist)

            val filename = song.path.getFilenameFromPath()
            file_name.setText(filename.substring(0, filename.lastIndexOf(".")))
            extension.setText(song.path.getFilenameExtension())
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
                            val newFilename = view.file_name.value
                            val newFileExtension = view.extension.value

                            if (newTitle.isEmpty() || newArtist.isEmpty() || newFilename.isEmpty() || newFileExtension.isEmpty()) {
                                activity.toast(R.string.rename_song_empty)
                                return@setOnClickListener
                            }

                            song.artist = newArtist
                            song.title = newTitle
                            updateContentResolver(context, song.mediaStoreId, newTitle, newArtist)

                            val oldPath = song.path
                            val newPath = "${oldPath.getParentPath()}/$newFilename.$newFileExtension"
                            if (oldPath == newPath) {
                                storeEditedSong(song, oldPath, newPath)
                                callback(song)
                                dismiss()
                                return@setOnClickListener
                            }

                            activity.renameFile(oldPath, newPath) {
                                if (it) {
                                    storeEditedSong(song, oldPath, newPath)
                                    song.path = newPath
                                    callback(song)
                                } else {
                                    activity.toast(R.string.rename_song_error)
                                }
                                dismiss()
                            }
                        }
                    }
                }
    }

    private fun storeEditedSong(song: Song, oldPath: String, newPath: String) {
        ensureBackgroundThread {
            try {
                activity.songsDAO.updateSongInfo(newPath, song.artist, song.title, oldPath)
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }
        }
    }

    private fun updateContentResolver(context: Context, songID: Long, newSongTitle: String, newSongArtist: String) {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val where = "${MediaStore.Audio.Media._ID} = ?"
        val args = arrayOf(songID.toString())

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newSongTitle)
            put(MediaStore.Audio.Media.ARTIST, newSongArtist)
        }
        context.contentResolver.update(uri, values, where, args)
    }
}
