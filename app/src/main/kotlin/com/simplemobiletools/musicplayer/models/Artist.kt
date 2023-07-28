package com.simplemobiletools.musicplayer.models

import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.extensions.sortSafely
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_ALBUM_COUNT
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE

@Entity(tableName = "artists", indices = [(Index(value = ["id"], unique = true))])
data class Artist(
    @PrimaryKey(autoGenerate = true) var id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "album_cnt") var albumCnt: Int,
    @ColumnInfo(name = "track_cnt") var trackCnt: Int,
    @ColumnInfo(name = "album_art") var albumArt: String
) {
    companion object {
        fun getComparator(sorting: Int) = Comparator<Artist> { first, second ->
            var result = when {
                sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                    when {
                        first.title == MediaStore.UNKNOWN_STRING && second.title != MediaStore.UNKNOWN_STRING -> 1
                        first.title != MediaStore.UNKNOWN_STRING && second.title == MediaStore.UNKNOWN_STRING -> -1
                        else -> AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                    }
                }

                sorting and PLAYER_SORT_BY_ALBUM_COUNT != 0 -> first.albumCnt.compareTo(second.albumCnt)
                else -> first.trackCnt.compareTo(second.trackCnt)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }

    fun getBubbleText(sorting: Int) = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        sorting and PLAYER_SORT_BY_ALBUM_COUNT != 0 -> albumCnt.toString()
        else -> trackCnt.toString()
    }
}

fun ArrayList<Artist>.sortSafely(sorting: Int) = sortSafely(Artist.getComparator(sorting))
