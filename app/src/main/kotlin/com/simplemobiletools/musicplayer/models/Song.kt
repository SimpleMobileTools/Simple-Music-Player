package com.simplemobiletools.musicplayer.models

import com.simplemobiletools.musicplayer.helpers.SORT_BY_ARTIST
import com.simplemobiletools.musicplayer.helpers.SORT_BY_FILE_NAME
import com.simplemobiletools.musicplayer.helpers.SORT_BY_TITLE
import com.simplemobiletools.musicplayer.helpers.SORT_DESCENDING
import java.io.Serializable

class Song(val id: Long, var title: String, var artist: String, var path: String, val duration: Int) : Serializable, Comparable<Song> {
    companion object {
        private const val serialVersionUID = 6717978793256842245L
        var sorting: Int = 0
    }

    override fun compareTo(other: Song): Int {
        var res: Int
        if (sorting and SORT_BY_TITLE != 0) {
            res = title.toLowerCase().compareTo(other.title.toLowerCase())
        } else if (sorting and SORT_BY_ARTIST != 0) {
            res = artist.toLowerCase().compareTo(other.artist.toLowerCase())
        } else if (sorting and SORT_BY_FILE_NAME != 0) {
            res = path.toLowerCase().compareTo(other.path.toLowerCase())
        } else {
            res = if (duration == other.duration)
                0
            else if (duration > other.duration)
                1
            else
                -1
        }

        if (sorting and SORT_DESCENDING != 0) {
            res *= -1
        }
        return res
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
