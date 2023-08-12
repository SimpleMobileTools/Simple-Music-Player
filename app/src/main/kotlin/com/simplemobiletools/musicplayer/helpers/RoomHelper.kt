package com.simplemobiletools.musicplayer.helpers

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import org.greenrobot.eventbus.EventBus
import java.io.File
import kotlin.math.min

class RoomHelper(val context: Context) {
    fun insertTracksWithPlaylist(tracks: ArrayList<Track>) {
        context.audioHelper.insertTracks(tracks)
        EventBus.getDefault().post(Events.PlaylistsUpdated())
    }

    fun getTrackFromPath(path: String): Track? {
        val songs = getTracksFromPaths(arrayListOf(path), 0)
        return songs.firstOrNull()
    }

    private fun getTracksFromPaths(paths: List<String>, playlistId: Int): ArrayList<Track> {
        val uri = Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayListOf(
            Audio.Media._ID,
            Audio.Media.TITLE,
            Audio.Media.ARTIST,
            Audio.Media.DATA,
            Audio.Media.DURATION,
            Audio.Media.ALBUM,
            Audio.Media.ALBUM_ID,
            Audio.Media.ARTIST_ID,
            Audio.Media.YEAR
        )

        if (isQPlus()) {
            projection.add(Audio.Media.BUCKET_DISPLAY_NAME)
        }

        if (isRPlus()) {
            projection.add(Audio.Media.GENRE)
            projection.add(Audio.Media.GENRE_ID)
        }

        val pathsMap = HashSet<String>()
        paths.mapTo(pathsMap) { it }

        val ITEMS_PER_GROUP = 50
        val songs = ArrayList<Track>(paths.size)
        val showFilename = context.config.showFilename

        val parts = paths.size / ITEMS_PER_GROUP
        for (i in 0..parts) {
            val sublist = paths.subList(i * ITEMS_PER_GROUP, min((i + 1) * ITEMS_PER_GROUP, paths.size))
            val questionMarks = getQuestionMarks(sublist.size)
            val selection = "${Audio.Media.DATA} IN ($questionMarks)"
            val selectionArgs = sublist.toTypedArray()

            context.queryCursor(uri, projection.toTypedArray(), selection, selectionArgs, showErrors = true) { cursor ->
                val mediaStoreId = cursor.getLongValue(Audio.Media._ID)
                val title = cursor.getStringValue(Audio.Media.TITLE)
                val artist = cursor.getStringValue(Audio.Media.ARTIST)
                val artistId = cursor.getLongValue(Audio.Media.ARTIST_ID)
                val path = cursor.getStringValue(Audio.Media.DATA)
                val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
                val album = cursor.getStringValue(Audio.Media.ALBUM)
                val albumId = cursor.getLongValue(Audio.Media.ALBUM_ID)
                val coverArt = ContentUris.withAppendedId(artworkUri, albumId).toString()
                val year = cursor.getIntValueOrNull(Audio.Media.YEAR) ?: 0
                val dateAdded = cursor.getIntValueOrNull(Audio.Media.DATE_ADDED) ?: 0
                val folderName = if (isQPlus()) {
                    cursor.getStringValue(Audio.Media.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
                } else {
                    ""
                }

                val genre: String
                val genreId: Long
                if (isRPlus()) {
                    genre = cursor.getStringValue(Audio.Media.GENRE)
                    genreId = cursor.getLongValue(Audio.Media.GENRE_ID)
                } else {
                    genre = ""
                    genreId = 0
                }

                val song = Track(
                    id = 0, mediaStoreId = mediaStoreId, title = title, artist = artist, path = path, duration = duration, album = album, genre = genre,
                    coverArt = coverArt, playListId = playlistId, trackId = 0, folderName = folderName, albumId = albumId, artistId = artistId,
                    genreId = genreId, year = year, dateAdded = dateAdded, orderInPlaylist = 0
                )
                song.title = song.getProperTitle(showFilename)
                songs.add(song)
                pathsMap.remove(path)
            }
        }

        pathsMap.forEach {
            val unknown = MediaStore.UNKNOWN_STRING
            val title = context.getTitle(it) ?: unknown
            val artist = context.getArtist(it) ?: unknown
            val dateAdded = try {
                (File(it).lastModified() / 1000L).toInt()
            } catch (e: Exception) {
                0
            }

            val song = Track(
                id = 0, mediaStoreId = 0, title = title, artist = artist, path = it, duration = context.getDuration(it) ?: 0, album = "",
                genre = "", coverArt = "", playListId = playlistId, trackId = 0, folderName = "", albumId = 0, artistId = 0, genreId = 0,
                year = 0, dateAdded = dateAdded, orderInPlaylist = 0
            )
            song.title = song.getProperTitle(showFilename)
            songs.add(song)
        }

        return songs
    }

    private fun getQuestionMarks(cnt: Int) = "?" + ",?".repeat(Math.max(cnt - 1, 0))
}
