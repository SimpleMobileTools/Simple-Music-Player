package com.simplemobiletools.musicplayer.helpers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils
import com.simplemobiletools.musicplayer.extensions.config

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

    fun removePlaylists(ids: ArrayList<Int>) {
        val args = TextUtils.join(", ", ids.filter { it != ALL_SONGS_PLAYLIST_ID })
        val songSelection = "$COL_PLAYLIST_ID IN ($args)"
        mDb.delete(TABLE_NAME_SONGS, songSelection, null)
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
}
