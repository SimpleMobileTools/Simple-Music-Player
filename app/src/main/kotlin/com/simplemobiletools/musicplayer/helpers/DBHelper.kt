package com.simplemobiletools.musicplayer.helpers

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.MediaStore
import android.text.TextUtils
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.playlistChanged
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Song
import java.io.File

class DBHelper private constructor(val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val TABLE_NAME_PLAYLISTS = "playlists"
    private val COL_ID = "id"
    private val COL_TITLE = "title"

    private val TABLE_NAME_SONGS = "songs"
    private val COL_PATH = "path"
    private val COL_PLAYLIST_ID = "playlist_id"

    private val mDb: SQLiteDatabase = writableDatabase

    companion object {
        private val DB_VERSION = 1
        val DB_NAME = "playlists.db"
        val ALL_SONGS_ID = 1

        fun newInstance(context: Context) = DBHelper(context)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_PLAYLISTS ($COL_ID INTEGER PRIMARY KEY, $COL_TITLE TEXT)")
        createSongsTable(db)
        addAllSongsPlaylist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    private fun createSongsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_SONGS ($COL_ID INTEGER PRIMARY KEY, $COL_PATH TEXT, $COL_PLAYLIST_ID INTEGER, " +
                "UNIQUE($COL_PATH, $COL_PLAYLIST_ID) ON CONFLICT IGNORE)")
    }

    private fun addAllSongsPlaylist(db: SQLiteDatabase) {
        val allSongs = context.resources.getString(R.string.all_songs)
        val playlist = Playlist(ALL_SONGS_ID, allSongs)
        addPlaylist(playlist, db)
    }

    private fun addPlaylist(playlist: Playlist, db: SQLiteDatabase) {
        insertPlaylist(playlist, db)
    }

    fun insertPlaylist(playlist: Playlist, db: SQLiteDatabase = mDb): Int {
        val values = ContentValues().apply { put(COL_TITLE, playlist.title) }
        return db.insert(TABLE_NAME_PLAYLISTS, null, values).toInt()
    }

    fun removePlaylist(id: Int) {
        removePlaylists(arrayListOf(id))
    }

    fun removePlaylists(ids: ArrayList<Int>) {
        val args = TextUtils.join(", ", ids.filter { it != ALL_SONGS_ID })
        val selection = "$COL_ID IN ($args)"
        mDb.delete(TABLE_NAME_PLAYLISTS, selection, null)

        val songSelection = "$COL_PLAYLIST_ID IN ($args)"
        mDb.delete(TABLE_NAME_SONGS, songSelection, null)

        if (ids.contains(context.config.currentPlaylist)) {
            context.playlistChanged(DBHelper.ALL_SONGS_ID)
        }
    }

    fun updatePlaylist(playlist: Playlist): Int {
        val selectionArgs = arrayOf(playlist.id.toString())
        val values = ContentValues().apply { put(COL_TITLE, playlist.title) }
        val selection = "$COL_ID = ?"
        return mDb.update(TABLE_NAME_PLAYLISTS, values, selection, selectionArgs)
    }

    fun addSongToPlaylist(path: String) {
        addSongsToPlaylist(ArrayList<String>().apply { add(path) })
    }

    fun addSongsToPlaylist(paths: ArrayList<String>, playlistId: Int = context.config.currentPlaylist) {
        try {
            mDb.beginTransaction()
            val values = ContentValues()
            for (path in paths) {
                values.apply {
                    put(COL_PATH, path)
                    put(COL_PLAYLIST_ID, playlistId)
                    mDb.insert(TABLE_NAME_SONGS, null, this)
                }
            }
            mDb.setTransactionSuccessful()
        } finally {
            mDb.endTransaction()
        }
    }

    fun getPlaylists(callback: (types: ArrayList<Playlist>) -> Unit) {
        val playlists = ArrayList<Playlist>(3)
        val cols = arrayOf(COL_ID, COL_TITLE)
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_PLAYLISTS, cols, null, null, null, null, "$COL_TITLE ASC")
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(COL_ID)
                    val title = cursor.getStringValue(COL_TITLE)
                    val playlist = Playlist(id, title)
                    playlists.add(playlist)
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
        callback(playlists)
    }

    fun getPlaylistIdWithTitle(title: String): Int {
        val cols = arrayOf(COL_ID)
        val selection = "$COL_TITLE = ? COLLATE NOCASE"
        val selectionArgs = arrayOf(title)
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_PLAYLISTS, cols, selection, selectionArgs, null, null, null)
            if (cursor?.moveToFirst() == true) {
                return cursor.getIntValue(COL_ID)
            }
        } finally {
            cursor?.close()
        }
        return -1
    }

    fun getPlaylistWithId(id: Int): Playlist? {
        val cols = arrayOf(COL_TITLE)
        val selection = "$COL_ID = ?"
        val selectionArgs = arrayOf(id.toString())
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_PLAYLISTS, cols, selection, selectionArgs, null, null, null)
            if (cursor?.moveToFirst() == true) {
                val title = cursor.getStringValue(COL_TITLE)
                return Playlist(id, title)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun removeSongFromPlaylist(path: String, playlistId: Int) {
        removeSongsFromPlaylist(ArrayList<String>().apply { add(path) }, playlistId)
    }

    fun removeSongsFromPlaylist(paths: ArrayList<String>, playlistId: Int = context.config.currentPlaylist) {
        val SPLICE_SIZE = 200
        for (i in 0 until paths.size step SPLICE_SIZE) {
            val curPaths = paths.subList(i, Math.min(i + SPLICE_SIZE, paths.size))
            val questionMarks = getQuestionMarks(curPaths.size)
            var selection = "$COL_PATH IN ($questionMarks)"
            if (playlistId != -1) {
                selection += " AND $COL_PLAYLIST_ID = $playlistId"
            }
            val selectionArgs = curPaths.toTypedArray()

            mDb.delete(TABLE_NAME_SONGS, selection, selectionArgs)
        }
    }

    fun getPlaylistSongPaths(playlistId: Int): ArrayList<String> {
        val paths = ArrayList<String>()
        val cols = arrayOf(COL_PATH)
        val selection = "$COL_PLAYLIST_ID = ?"
        val selectionArgs = arrayOf(playlistId.toString())
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_SONGS, cols, selection, selectionArgs, null, null, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val path = cursor.getStringValue(COL_PATH) ?: continue
                    if (File(path).exists()) {
                        paths.add(path)
                    } else {
                        removeSongFromPlaylist(path, -1)
                    }
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
        return paths
    }

    fun getSongs(): ArrayList<Song> {
        val SPLICE_SIZE = 200
        val paths = getPlaylistSongPaths(context.config.currentPlaylist)
        val songs = ArrayList<Song>(paths.size)
        if (paths.isEmpty()) {
            return songs
        }

        for (i in 0 until paths.size step SPLICE_SIZE) {
            val curPaths = paths.subList(i, Math.min(i + SPLICE_SIZE, paths.size))
            songs.addAll(getSongsFromPaths(curPaths))
        }
        return songs
    }

    fun getSongFromPath(path: String): Song? {
        val songs = getSongsFromPaths(arrayListOf(path))
        return if (songs.isNotEmpty()) {
            songs.first()
        } else {
            null
        }
    }

    private fun getSongsFromPaths(paths: List<String>): ArrayList<Song> {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION)
        val pathsMap = HashSet<String>()
        paths.mapTo(pathsMap, { it })

        val ITEMS_PER_GROUP = 50
        val songs = ArrayList<Song>(paths.size)

        val parts = paths.size / ITEMS_PER_GROUP
        for (i in 0..parts) {
            val sublist = paths.subList(i * ITEMS_PER_GROUP, Math.min((i + 1) * ITEMS_PER_GROUP, paths.size))
            val questionMarks = getQuestionMarks(sublist.size)
            val selection = "${MediaStore.Audio.Media.DATA} IN ($questionMarks)"
            val selectionArgs = sublist.toTypedArray()

            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, columns, selection, selectionArgs, null)
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        val id = cursor.getLongValue(MediaStore.Audio.Media._ID)
                        val title = cursor.getStringValue(MediaStore.Audio.Media.TITLE)
                        val artist = cursor.getStringValue(MediaStore.Audio.Media.ARTIST)
                        val path = cursor.getStringValue(MediaStore.Audio.Media.DATA)
                        val duration = cursor.getIntValue(MediaStore.Audio.Media.DURATION) / 1000
                        val song = Song(id, title, artist, path, duration)
                        songs.add(song)
                        pathsMap.remove(path)
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor?.close()
            }
        }

        pathsMap.forEach {
            val file = File(it)
            val unknown = context.getString(R.string.unknown)
            val song = Song(0, file.getSongTitle() ?: unknown, file.getArtist() ?: unknown, it, file.getDurationSeconds())
            songs.add(song)
        }

        return songs
    }

    private fun getQuestionMarks(cnt: Int) = "?" + ",?".repeat(Math.max(cnt - 1, 0))
}
