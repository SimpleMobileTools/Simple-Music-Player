package com.simplemobiletools.musicplayer.helpers

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import org.greenrobot.eventbus.EventBus

class RoomHelper(val context: Context) {
    fun insertTracksWithPlaylist(tracks: ArrayList<Track>) {
        context.tracksDAO.insertAll(tracks)
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
            Audio.Media.ALBUM_ID
        )

        if (isQPlus()) {
            projection.add(Audio.Media.BUCKET_DISPLAY_NAME)
        }

        val pathsMap = HashSet<String>()
        paths.mapTo(pathsMap) { it }

        val ITEMS_PER_GROUP = 50
        val songs = ArrayList<Track>(paths.size)
        val showFilename = context.config.showFilename

        val parts = paths.size / ITEMS_PER_GROUP
        for (i in 0..parts) {
            val sublist = paths.subList(i * ITEMS_PER_GROUP, Math.min((i + 1) * ITEMS_PER_GROUP, paths.size))
            val questionMarks = getQuestionMarks(sublist.size)
            val selection = "${Audio.Media.DATA} IN ($questionMarks)"
            val selectionArgs = sublist.toTypedArray()

            context.queryCursor(uri, projection.toTypedArray(), selection, selectionArgs) { cursor ->
                val mediaStoreId = cursor.getLongValue(Audio.Media._ID)
                val title = cursor.getStringValue(Audio.Media.TITLE)
                val artist = cursor.getStringValue(Audio.Media.ARTIST)
                val path = cursor.getStringValue(Audio.Media.DATA)
                val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
                val album = cursor.getStringValue(Audio.Media.ALBUM)
                val albumId = cursor.getLongValue(Audio.Media.ALBUM_ID)
                val coverArt = ContentUris.withAppendedId(artworkUri, albumId).toString()
                val folderName = if (isQPlus()) {
                    cursor.getStringValue(Audio.Media.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
                } else {
                    ""
                }

                val song = Track(0, mediaStoreId, title, artist, path, duration, album, coverArt, playlistId, 0, folderName, albumId)
                song.title = song.getProperTitle(showFilename)
                songs.add(song)
                pathsMap.remove(path)
            }
        }

        pathsMap.forEach {
            val unknown = MediaStore.UNKNOWN_STRING
            val title = context.getTitle(it) ?: unknown
            val song = Track(0, 0, title, context.getArtist(it) ?: unknown, it, context.getDuration(it) ?: 0, "", "", playlistId, 0, "", 0)
            song.title = song.getProperTitle(showFilename)
            songs.add(song)
        }

        return songs
    }

    private fun getQuestionMarks(cnt: Int) = "?" + ",?".repeat(Math.max(cnt - 1, 0))
}
