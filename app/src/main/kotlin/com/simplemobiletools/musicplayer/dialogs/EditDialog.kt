package com.simplemobiletools.musicplayer.dialogs

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.support.v7.app.AlertDialog
import android.view.WindowManager
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.dialog_rename_song.view.*
import java.io.File

class EditDialog(val activity: BaseSimpleActivity, val song: Song, val callback: (Song) -> Unit) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_rename_song, null).apply {
            title.setText(song.title)
            artist.setText(song.artist)

            val filename = song.path.getFilenameFromPath()
            file_name.setText(filename.substring(0, filename.lastIndexOf(".")))
            extension.setText(song.path.getFilenameExtension())
        }

        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            activity.setupDialogStuff(view, this, R.string.rename_song) {
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val newTitle = view.title.value
                    val newArtist = view.artist.value
                    val newFilename = view.file_name.value
                    val newFileExtension = view.extension.value

                    if (newTitle.isEmpty() || newArtist.isEmpty() || newFilename.isEmpty() || newFileExtension.isEmpty()) {
                        activity.toast(R.string.rename_song_empty)
                        return@setOnClickListener
                    }

                    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    song.artist = newArtist
                    song.title = newTitle

                    if (updateContentResolver(context, uri, song.id, newTitle, newArtist)) {
                        context.contentResolver.notifyChange(uri, null)

                        val file = File(song.path)
                        val newFile = File(file.parent, "$newFilename.$newFileExtension")
                        if (file == newFile) {
                            callback(song)
                            dismiss()
                            return@setOnClickListener
                        }

                        if (file.renameTo(newFile)) {
                            context.scanFiles(arrayListOf(file, newFile)) {
                                song.path = newFile.absolutePath
                                callback(song)
                            }
                            dismiss()
                            return@setOnClickListener
                        }

                        activity.toast(R.string.rename_song_error)
                    }
                }
            }
        }
    }

    private fun updateContentResolver(context: Context, uri: Uri, songID: Long, newSongTitle: String, newSongArtist: String): Boolean {
        val where = "${MediaStore.Images.Media._ID} = ?"
        val args = arrayOf(songID.toString())

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, newSongTitle)
            put(MediaStore.Audio.Media.ARTIST, newSongArtist)
        }
        return context.contentResolver.update(uri, values, where, args) == 1
    }
}
