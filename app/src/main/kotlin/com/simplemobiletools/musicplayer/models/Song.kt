package com.simplemobiletools.musicplayer.models

import java.io.Serializable

class Song(val id: Long, var title: String, var artist: String, var path: String, val duration: Int) : Serializable {
    companion object {
        private const val serialVersionUID = 6717978793256842145L
    }

    override fun toString() = "Song {id=$id, title=$title, artist=$artist, path=$path, duration=$duration}"

    override fun equals(o: Any?): Boolean {
        return if (this === o)
            true
        else if (o == null)
            false
        else
            id == (o as Song).id
    }
}
