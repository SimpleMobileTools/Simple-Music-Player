package com.simplemobiletools.musicplayer.models

import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.helpers.SORT_BY_ARTIST
import com.simplemobiletools.commons.helpers.SORT_BY_PATH
import com.simplemobiletools.commons.helpers.SORT_BY_TITLE
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_IF_UNAVAILABLE
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_NEVER
import java.io.Serializable

@Entity(tableName = "songs", primaryKeys = ["path", "playlist_id"])
data class Song(
        @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
        @ColumnInfo(name = "title") var title: String,
        @ColumnInfo(name = "artist") var artist: String,
        @ColumnInfo(name = "path") var path: String,
        @ColumnInfo(name = "duration") val duration: Int,
        @ColumnInfo(name = "album") val album: String,
        @ColumnInfo(name = "playlist_id") val playListId: Int,
        @ColumnInfo(name = "type") val type: Int) : Serializable, Comparable<Song> {

    companion object {
        private const val serialVersionUID = 6717978793256852245L
        var sorting = 0
    }

    override fun compareTo(other: Song): Int {
        var res = when {
            sorting and SORT_BY_TITLE != 0 -> {
                if (title == MediaStore.UNKNOWN_STRING && other.title != MediaStore.UNKNOWN_STRING) {
                    1
                } else if (title != MediaStore.UNKNOWN_STRING && other.title == MediaStore.UNKNOWN_STRING) {
                    -1
                } else {
                    title.toLowerCase().compareTo(other.title.toLowerCase())
                }
            }
            sorting and SORT_BY_ARTIST != 0 -> {
                if (artist == MediaStore.UNKNOWN_STRING && other.artist != MediaStore.UNKNOWN_STRING) {
                    1
                } else if (artist != MediaStore.UNKNOWN_STRING && other.artist == MediaStore.UNKNOWN_STRING) {
                    -1
                } else {
                    artist.toLowerCase().compareTo(other.artist.toLowerCase())
                }
            }
            sorting and SORT_BY_PATH != 0 -> path.toLowerCase().compareTo(other.path.toLowerCase())
            else -> when {
                duration == other.duration -> 0
                duration > other.duration -> 1
                else -> -1
            }
        }

        if (sorting and SORT_DESCENDING != 0) {
            res *= -1
        }

        return res
    }

    fun getBubbleText() = when {
        sorting and SORT_BY_TITLE != 0 -> title
        sorting and SORT_BY_ARTIST != 0 -> artist
        sorting and SORT_BY_PATH != 0 -> path
        else -> duration.getFormattedDuration()
    }

    fun getProperTitle(showFilename: Int): String {
        return when (showFilename) {
            SHOW_FILENAME_NEVER -> title
            SHOW_FILENAME_IF_UNAVAILABLE -> if (title == MediaStore.UNKNOWN_STRING) path.getFilenameFromPath() else title
            else -> path.getFilenameFromPath()
        }
    }
}
