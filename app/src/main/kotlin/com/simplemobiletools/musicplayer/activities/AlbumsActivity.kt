package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import android.provider.MediaStore.Audio
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.AlbumsAdapter
import com.simplemobiletools.musicplayer.helpers.ARTIST
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import kotlinx.android.synthetic.main.activity_artist.*

class AlbumsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artist)

        val artistType = object : TypeToken<Artist>() {}.type
        val artist = Gson().fromJson<Artist>(intent.getStringExtra(ARTIST), artistType)
        title = artist.title

        getAlbums(artist) { albums ->
            AlbumsAdapter(this, albums, albums_list) {

            }.apply {
                albums_list.adapter = this
            }
        }
    }

    private fun getAlbums(artist: Artist, callback: (artists: ArrayList<Album>) -> Unit) {
        ensureBackgroundThread {
            val albums = ArrayList<Album>()
            val uri = Audio.Albums.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                Audio.Albums._ID,
                Audio.Albums.ARTIST,
                Audio.Albums.ALBUM)

            var selection = "${Audio.Albums.ARTIST} = ?"
            var selectionArgs = arrayOf(artist.title)

            if (isQPlus()) {
                selection = "${Audio.Albums.ARTIST_ID} = ?"
                selectionArgs = arrayOf(artist.id.toString())
            }

            try {
                val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        do {
                            val id = cursor.getIntValue(Audio.Albums._ID)
                            val artistName = cursor.getStringValue(Audio.Albums.ARTIST)
                            val title = cursor.getStringValue(Audio.Albums.ALBUM)
                            val album = Album(id, artistName, title)
                            albums.add(album)
                        } while (cursor.moveToNext())
                    }
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }

            runOnUiThread {
                callback(albums)
            }
        }
    }
}
