package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.AlbumsAdapter
import com.simplemobiletools.musicplayer.extensions.getAlbums
import com.simplemobiletools.musicplayer.extensions.getSongsSync
import com.simplemobiletools.musicplayer.helpers.ALBUM
import com.simplemobiletools.musicplayer.helpers.ARTIST
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.activity_albums.*

// Artists -> Albums -> Songs
class AlbumsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_albums)

        val artistType = object : TypeToken<Artist>() {}.type
        val artist = Gson().fromJson<Artist>(intent.getStringExtra(ARTIST), artistType)
        title = artist.title

        getAlbums(artist) { albums ->
            runOnUiThread {
                AlbumsAdapter(this, albums, albums_list) {
                    Intent(this, SongsActivity::class.java).apply {
                        putExtra(ALBUM, Gson().toJson(it as Album))
                        startActivity(this)
                    }
                }.apply {
                    albums_list.adapter = this
                }
            }

            fillSongs(albums)
        }
    }

    private fun fillSongs(albums: ArrayList<Album>) {
        val songs = ArrayList<Song>()
        albums.forEach {
            songs.addAll(getSongsSync(it.id))
        }
    }
}
