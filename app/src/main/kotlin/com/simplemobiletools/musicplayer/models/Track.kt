package com.simplemobiletools.musicplayer.models

import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_ARTIST_TITLE
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_IF_UNAVAILABLE
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_NEVER
import java.io.Serializable

@Entity(tableName = "tracks", indices = [Index(value = ["media_store_id", "playlist_id"], unique = true)])
data class Track(
    @PrimaryKey(autoGenerate = true) var id: Long,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "artist") var artist: String,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "duration") var duration: Int,
    @ColumnInfo(name = "album") var album: String,
    @ColumnInfo(name = "cover_art") val coverArt: String,
    @ColumnInfo(name = "playlist_id") var playListId: Int,
    @ColumnInfo(name = "track_id") val trackId: Int,  // order id within the tracks' album
    @ColumnInfo(name = "folder_name") var folderName: String,
    @ColumnInfo(name = "album_id") val albumId: Long
) : Serializable, Comparable<Track>, ListItem() {

    companion object {
        private const val serialVersionUID = 6717978793256852245L
        var sorting = 0
    }

    override fun compareTo(other: Track): Int {
        var res = when {
            sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                when {
                    title == MediaStore.UNKNOWN_STRING && other.title != MediaStore.UNKNOWN_STRING -> 1
                    title != MediaStore.UNKNOWN_STRING && other.title == MediaStore.UNKNOWN_STRING -> -1
                    else -> AlphanumericComparator().compare(title.toLowerCase(), other.title.toLowerCase())
                }
            }
            sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> {
                when {
                    artist == MediaStore.UNKNOWN_STRING && artist != MediaStore.UNKNOWN_STRING -> 1
                    artist != MediaStore.UNKNOWN_STRING && artist == MediaStore.UNKNOWN_STRING -> -1
                    else -> AlphanumericComparator().compare(artist.toLowerCase(), other.artist.toLowerCase())
                }
            }
            else -> duration.compareTo(other.duration)
        }

        if (sorting and SORT_DESCENDING != 0) {
            res *= -1
        }

        return res
    }

    fun getBubbleText() = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> artist
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
