package com.simplemobiletools.musicplayer.models

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.extensions.sortSafely
import com.simplemobiletools.musicplayer.extensions.toMediaItem
import com.simplemobiletools.musicplayer.helpers.*
import java.io.File
import java.io.Serializable

@Entity(tableName = "tracks", indices = [Index(value = ["media_store_id", "playlist_id"], unique = true)])
data class Track(
    @PrimaryKey(autoGenerate = true) var id: Long,
    @ColumnInfo(name = "media_store_id") var mediaStoreId: Long,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "artist") var artist: String,
    @ColumnInfo(name = "path") var path: String,
    @ColumnInfo(name = "duration") var duration: Int,
    @ColumnInfo(name = "album") var album: String,
    @ColumnInfo(name = "genre") var genre: String,
    @ColumnInfo(name = "cover_art") val coverArt: String,
    @ColumnInfo(name = "playlist_id") var playListId: Int,
    @ColumnInfo(name = "track_id") val trackId: Int,  // order id within the tracks' album
    @ColumnInfo(name = "folder_name") var folderName: String,
    @ColumnInfo(name = "album_id") var albumId: Long,
    @ColumnInfo(name = "artist_id") var artistId: Long,
    @ColumnInfo(name = "genre_id") var genreId: Long,
    @ColumnInfo(name = "year") var year: Int,
    @ColumnInfo(name = "date_added") var dateAdded: Int,
    @ColumnInfo(name = "order_in_playlist") var orderInPlaylist: Int,
    @ColumnInfo(name = "flags") var flags: Int = 0
) : Serializable, ListItem() {

    companion object {
        private const val serialVersionUID = 6717978793256852245L

        fun getComparator(sorting: Int) = Comparator<Track> { first, second ->
            var result = when {
                sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                    when {
                        first.title == MediaStore.UNKNOWN_STRING && second.title != MediaStore.UNKNOWN_STRING -> 1
                        first.title != MediaStore.UNKNOWN_STRING && second.title == MediaStore.UNKNOWN_STRING -> -1
                        else -> AlphanumericComparator().compare(first.title.lowercase(), second.title.lowercase())
                    }
                }

                sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> {
                    when {
                        first.artist == MediaStore.UNKNOWN_STRING && second.artist != MediaStore.UNKNOWN_STRING -> 1
                        first.artist != MediaStore.UNKNOWN_STRING && second.artist == MediaStore.UNKNOWN_STRING -> -1
                        else -> AlphanumericComparator().compare(first.artist.lowercase(), second.artist.lowercase())
                    }
                }

                sorting and PLAYER_SORT_BY_TRACK_ID != 0 -> first.trackId.compareTo(second.trackId)
                sorting and PLAYER_SORT_BY_DATE_ADDED != 0 -> first.dateAdded.compareTo(second.dateAdded)
                sorting and PLAYER_SORT_BY_CUSTOM != 0 -> first.orderInPlaylist.compareTo(second.orderInPlaylist)
                else -> first.duration.compareTo(second.duration)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }

    fun getBubbleText(sorting: Int) = when {
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

    fun getUri(): Uri = if (mediaStoreId == 0L || flags and FLAG_MANUAL_CACHE != 0) {
        Uri.fromFile(File(path))
    } else {
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    }

    fun isCurrent() = flags and FLAG_IS_CURRENT != 0
}

fun ArrayList<Track>.sortSafely(sorting: Int) = sortSafely(Track.getComparator(sorting))

fun Collection<Track>.toMediaItems() = map { it.toMediaItem() }

fun Collection<Track>.toMediaItemsFast() = map {
    MediaItem.Builder()
        .setMediaId(it.mediaStoreId.toString())
        .build()
}
