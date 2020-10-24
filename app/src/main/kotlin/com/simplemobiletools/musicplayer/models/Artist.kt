package com.simplemobiletools.musicplayer.models

import android.provider.MediaStore
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_ALBUM_COUNT
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE

data class Artist(val id: Long, val title: String, var albumCnt: Int, var trackCnt: Int, var albumArtId: Long) : Comparable<Artist> {
    companion object {
        var sorting = 0
    }

    override fun compareTo(other: Artist): Int {
        var result = when {
            sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                when {
                    title == MediaStore.UNKNOWN_STRING -> 1
                    other.title == MediaStore.UNKNOWN_STRING -> -1
                    else -> AlphanumericComparator().compare(title.toLowerCase(), other.title.toLowerCase())
                }
            }
            sorting and PLAYER_SORT_BY_ALBUM_COUNT != 0 -> albumCnt.compareTo(other.albumCnt)
            else -> trackCnt.compareTo(other.trackCnt)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }
}
