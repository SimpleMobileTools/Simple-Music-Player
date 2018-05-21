package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Song

class DBHelper private constructor(val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val TABLE_NAME_PLAYLISTS = "playlists"
    private val COL_ID = "id"
    private val COL_TITLE = "title"

    private val TABLE_NAME_SONGS = "songs"
    private val COL_PATH = "path"
    private val COL_PLAYLIST_ID = "playlist_id"

    private val mDb = writableDatabase

    companion object {
        private const val DB_VERSION = 1
        const val DB_NAME = "playlists.db"

        fun newInstance(context: Context) = DBHelper(context)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_PLAYLISTS ($COL_ID INTEGER PRIMARY KEY, $COL_TITLE TEXT)")
        createSongsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    private fun createSongsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_SONGS ($COL_ID INTEGER PRIMARY KEY, $COL_PATH TEXT, $COL_PLAYLIST_ID INTEGER, " +
                "UNIQUE($COL_PATH, $COL_PLAYLIST_ID) ON CONFLICT IGNORE)")
    }

    fun getAllPlaylists(callback: (types: ArrayList<Playlist>) -> Unit) {
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

    fun getAllSongs(callback: (songs: ArrayList<Song>) -> Unit) {
        val songs = ArrayList<Song>()
        val cols = arrayOf(COL_PATH, COL_PLAYLIST_ID)
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_SONGS, cols, null, null, null, null, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val path = cursor.getStringValue(COL_PATH)
                    val playlistId = cursor.getIntValue(COL_PLAYLIST_ID)
                    val song = Song(0, "", "", path, 0, "", playlistId, TYPE_FILE)
                    songs.add(song)
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
        callback(songs)
    }

    fun clearDatabase() {
        mDb.execSQL("DELETE FROM $TABLE_NAME_PLAYLISTS")
        mDb.execSQL("DELETE FROM $TABLE_NAME_SONGS")
    }
}
