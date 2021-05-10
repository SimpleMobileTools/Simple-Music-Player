package com.simplemobiletools.musicplayer.models

import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_ALBUM_COUNT
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE

@Entity(tableName = "artists", indices = [(Index(value = ["id"], unique = true))])
data class Artist(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "album_cnt") var albumCnt: Int,
    @ColumnInfo(name = "track_cnt") var trackCnt: Int,
    @ColumnInfo(name = "album_art_id") var albumArtId: Long) : Comparable<Artist> {
    companion object {
        var sorting = 0
    }

    override fun compareTo(other: Artist): Int {
        var result = when {
            sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                when {
                    title == MediaStore.UNKNOWN_STRING && other.title != MediaStore.UNKNOWN_STRING -> 1
                    title != MediaStore.UNKNOWN_STRING && other.title == MediaStore.UNKNOWN_STRING -> -1
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

    fun getBubbleText() = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        sorting and PLAYER_SORT_BY_ALBUM_COUNT != 0 -> albumCnt.toString()
        else -> trackCnt.toString()
    }
}
