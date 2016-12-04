package com.simplemobiletools.musicplayer.dialogs

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.WindowManager
import com.simplemobiletools.filepicker.extensions.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.rename_song.view.*
import java.io.File

class EditDialog(val activity: SimpleActivity, val song: Song, val callback: (Song) -> Unit) {
    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.rename_song, null)
        view.title.setText(song.title)
        view.artist.setText(song.artist)

        val filename = song.path.getFilenameFromPath()
        view.file_name.setText(filename.substring(0, filename.lastIndexOf(".")))
        view.extension.setText(song.path.getFilenameExtension())

        AlertDialog.Builder(activity)
                .setTitle(activity.resources.getString(R.string.rename_song))
                .setView(view)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            setCanceledOnTouchOutside(true)
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener({
                val newTitle = view.title.value
                val newArtist = view.artist.value
                val newFilename = view.file_name.value
                val newFileExtension = view.extension.value

                if (newTitle.isEmpty() || newArtist.isEmpty() || newFilename.isEmpty() || newFileExtension.isEmpty()) {
                    context.toast(R.string.rename_song_empty)
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
                        callback.invoke(song)
                        dismiss()
                        return@setOnClickListener
                    }

                    if (file.renameTo(newFile)) {
                        context.scanFiles(arrayListOf(file, newFile)) {
                            song.path = newFile.absolutePath
                            callback.invoke(song)
                        }
                        dismiss()
                        return@setOnClickListener
                    }

                    context.toast(R.string.rename_song_error)
                }
            })
        }
    }

    private fun updateContentResolver(context: Context, uri: Uri, songID: Long, newSongTitle: String, newSongArtist: String): Boolean {
        val where = "${MediaStore.Images.Media._ID} = ?"
        val args = arrayOf(songID.toString())

        val values = ContentValues()
        values.put(MediaStore.Audio.Media.TITLE, newSongTitle)
        values.put(MediaStore.Audio.Media.ARTIST, newSongArtist)
        return context.contentResolver.update(uri, values, where, args) == 1
    }
}
