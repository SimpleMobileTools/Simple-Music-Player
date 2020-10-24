package com.simplemobiletools.musicplayer.models

import android.provider.MediaStore
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_ARTIST_TITLE
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE

data class Album(val id: Long, val artist: String, val title: String, val coverArt: String, val year: Int) : ListItem(), Comparable<Album> {
    companion object {
        var sorting = 0
    }

    override fun compareTo(other: Album): Int {
        var result = when {
            sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                when {
                    title == MediaStore.UNKNOWN_STRING && other.title != MediaStore.UNKNOWN_STRING -> 1
                    title != MediaStore.UNKNOWN_STRING && other.title == MediaStore.UNKNOWN_STRING -> -1
                    else -> AlphanumericComparator().compare(title.toLowerCase(), other.title.toLowerCase())
                }
            }
            sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> {
                when {
                    artist == MediaStore.UNKNOWN_STRING && other.artist != MediaStore.UNKNOWN_STRING -> 1
                    artist != MediaStore.UNKNOWN_STRING && other.artist == MediaStore.UNKNOWN_STRING -> -1
                    else -> AlphanumericComparator().compare(artist.toLowerCase(), other.artist.toLowerCase())
                }
            }
            else -> year.compareTo(other.year)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }

    fun getBubbleText() = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> artist
        else -> year.toString()
    }
}
