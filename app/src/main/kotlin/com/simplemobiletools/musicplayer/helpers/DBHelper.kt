package com.simplemobiletools.musicplayer.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.models.Playlist

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
        val INITIAL_PLAYLIST_ID = 1

        fun newInstance(context: Context): DBHelper {
            return DBHelper(context)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_PLAYLISTS ($COL_ID INTEGER PRIMARY KEY, $COL_TITLE TEXT)")
        createSongsTable(db)
        addInitialPlaylist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    private fun createSongsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_SONGS ($COL_ID INTEGER PRIMARY KEY, $COL_PATH TEXT, $COL_PLAYLIST_ID INTEGER, " +
                "UNIQUE($COL_PATH, $COL_PLAYLIST_ID) ON CONFLICT IGNORE)")
    }

    private fun addInitialPlaylist(db: SQLiteDatabase) {
        val initialPlaylist = context.resources.getString(R.string.initial_playlist)
        val playlist = Playlist(INITIAL_PLAYLIST_ID, initialPlaylist)
        addPlaylist(playlist, db)
    }

    private fun addPlaylist(playlist: Playlist, db: SQLiteDatabase) {
        insertPlaylist(playlist, db)
    }

    fun insertPlaylist(playlist: Playlist, db: SQLiteDatabase = mDb): Int {
        val values = fillPlaylistValues(playlist)
        val insertedId = db.insert(TABLE_NAME_PLAYLISTS, null, values).toInt()
        return insertedId
    }

    private fun fillPlaylistValues(playlist: Playlist): ContentValues {
        return ContentValues().apply {
            put(COL_TITLE, playlist.title)
        }
    }

    fun addSongsToPlaylist(paths: ArrayList<String>) {
        val playlistId = context.config.currentPlaylist
        for (path in paths) {
            ContentValues().apply {
                put(COL_PATH, path)
                put(COL_PLAYLIST_ID, playlistId)
                mDb.insert(TABLE_NAME_SONGS, null, this)
            }
        }
    }
}
