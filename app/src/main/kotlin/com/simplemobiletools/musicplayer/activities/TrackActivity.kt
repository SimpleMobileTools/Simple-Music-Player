package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.TRACK
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Song

class TrackActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track)

        val trackType = object : TypeToken<Song>() {}.type
        val song = Gson().fromJson<Song>(intent.getStringExtra(TRACK), trackType)
        title = song.title
    }
}
