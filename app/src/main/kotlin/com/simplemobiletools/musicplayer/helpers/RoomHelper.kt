package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.songsDAO
import com.simplemobiletools.musicplayer.models.Song

class RoomHelper(val context: Context) {
    fun addSongToPlaylist(path: String) {
        addSongsToPlaylist(arrayListOf(path))
    }

    fun addSongsToPlaylist(paths: ArrayList<String>, playlistId: Int = context.config.currentPlaylist) {
        val songs = getSongsFromPaths(paths, playlistId)
        context.songsDAO.insertAll(songs)
    }

    fun getSongFromPath(path: String): Song? {
        val songs = getSongsFromPaths(arrayListOf(path), 0)
        return songs.firstOrNull()
    }

    private fun getSongsFromPaths(paths: List<String>, playlistId: Int): ArrayList<Song> {
        val uri = Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(Audio.Media._ID,
                Audio.Media.TITLE,
                Audio.Media.ARTIST,
                Audio.Media.DATA,
                Audio.Media.DURATION,
                Audio.Media.ALBUM)

        val pathsMap = HashSet<String>()
        paths.mapTo(pathsMap) { it }

        val ITEMS_PER_GROUP = 50
        val songs = ArrayList<Song>(paths.size)
        val showFilename = context.config.showFilename

        val parts = paths.size / ITEMS_PER_GROUP
        for (i in 0..parts) {
            val sublist = paths.subList(i * ITEMS_PER_GROUP, Math.min((i + 1) * ITEMS_PER_GROUP, paths.size))
            val questionMarks = getQuestionMarks(sublist.size)
            val selection = "${Audio.Media.DATA} IN ($questionMarks)"
            val selectionArgs = sublist.toTypedArray()

            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, columns, selection, selectionArgs, null)
                if (cursor?.moveToFirst() == true) {
                    do {
                        val mediaStoreId = cursor.getLongValue(Audio.Media._ID)
                        val title = cursor.getStringValue(Audio.Media.TITLE)
                        val artist = cursor.getStringValue(Audio.Media.ARTIST)
                        val path = cursor.getStringValue(Audio.Media.DATA)
                        val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
                        val album = cursor.getStringValue(Audio.Media.ALBUM)
                        val song = Song(mediaStoreId, title, artist, path, duration, album, playlistId, TYPE_FILE)
                        song.title = song.getProperTitle(showFilename)
                        songs.add(song)
                        pathsMap.remove(path)
                    } while (cursor.moveToNext())
                }
            } catch (e: Exception) {
            } finally {
                cursor?.close()
            }
        }

        pathsMap.forEach {
            val unknown = MediaStore.UNKNOWN_STRING
            val title = it.getFileSongTitle() ?: unknown
            val song = Song(0, title, it.getFileArtist() ?: unknown, it, it.getFileDurationSeconds() ?: 0, "", playlistId, TYPE_FILE)
            song.title = song.getProperTitle(showFilename)
            songs.add(song)
        }

        return songs
    }

    private fun getQuestionMarks(cnt: Int) = "?" + ",?".repeat(Math.max(cnt - 1, 0))
}
