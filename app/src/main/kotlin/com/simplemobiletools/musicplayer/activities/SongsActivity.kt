package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import android.provider.MediaStore.Audio
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.ALBUM
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Song

// Artists -> Albums -> Songs
class SongsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songs)

        val albumType = object : TypeToken<Album>() {}.type
        val album = Gson().fromJson<Album>(intent.getStringExtra(ALBUM), albumType)
        title = album.title

        getSongs(album) {

        }
    }

    private fun getSongs(album: Album, callback: (songs: ArrayList<Song>) -> Unit) {
        ensureBackgroundThread {
            val songs = ArrayList<Song>()
            val uri = Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                Audio.Media._ID,
                Audio.Media.DURATION,
                Audio.Media.TITLE
            )

            val selection = "${Audio.Albums.ALBUM_ID} = ?"
            val selectionArgs = arrayOf(album.id.toString())

            try {
                val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        do {
                            val id = cursor.getLongValue(Audio.Media._ID)
                            val title = cursor.getStringValue(Audio.Media.TITLE)
                            val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
                            val path = ""
                            val artist = ""
                            val song = Song(id, title, artist, path, duration, "", 0)
                            songs.add(song)
                        } while (cursor.moveToNext())
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }

            runOnUiThread {
                callback(songs)
            }
        }
    }
}
