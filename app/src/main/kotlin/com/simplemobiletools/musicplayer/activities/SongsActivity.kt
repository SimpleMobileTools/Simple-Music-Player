package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SongsAdapter
import com.simplemobiletools.musicplayer.extensions.getSongs
import com.simplemobiletools.musicplayer.helpers.ALBUM
import com.simplemobiletools.musicplayer.models.Album
import kotlinx.android.synthetic.main.activity_songs.*

// Artists -> Albums -> Songs
class SongsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_songs)

        val albumType = object : TypeToken<Album>() {}.type
        val album = Gson().fromJson<Album>(intent.getStringExtra(ALBUM), albumType)
        title = album.title

        getSongs(album.id) { songs ->
            runOnUiThread {
                SongsAdapter(this, songs, songs_list) {

                }.apply {
                    songs_list.adapter = this
                }
            }
        }
    }
}
