package com.simplemobiletools.musicplayer.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.playlistDAO
import com.simplemobiletools.musicplayer.helpers.ALL_SONGS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.interfaces.PlaylistsDao
import com.simplemobiletools.musicplayer.interfaces.SongsDao
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.objects.MyExecutor
import java.util.concurrent.Executors

@Database(entities = [(Song::class), (Playlist::class)], version = 1)
abstract class SongsDatabase : RoomDatabase() {

    abstract fun SongsDao(): SongsDao

    abstract fun PlaylistsDao(): PlaylistsDao

    companion object {
        private var db: SongsDatabase? = null

        fun getInstance(context: Context): SongsDatabase {
            if (db == null) {
                synchronized(SongsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, SongsDatabase::class.java, "songs.db")
                                .setQueryExecutor(MyExecutor.myExecutor)
                                .addCallback(object : Callback() {
                                    override fun onCreate(db: SupportSQLiteDatabase) {
                                        super.onCreate(db)
                                        Executors.newSingleThreadExecutor().execute {
                                            addInitialPlaylist(context)
                                        }
                                    }
                                })
                                .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }

        private fun addInitialPlaylist(context: Context) {
            val allSongs = context.resources.getString(R.string.all_songs)
            val playlist = Playlist(ALL_SONGS_PLAYLIST_ID, allSongs)
            context.playlistDAO.insert(playlist)
        }
    }
}
