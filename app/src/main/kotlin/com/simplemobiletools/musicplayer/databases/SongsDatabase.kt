package com.simplemobiletools.musicplayer.databases

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import com.simplemobiletools.musicplayer.interfaces.SongsDao
import com.simplemobiletools.musicplayer.models.Song

@Database(entities = [(Song::class)], version = 1)
abstract class SongsDatabase : RoomDatabase() {

    abstract fun SongsDao(): SongsDao

    companion object {
        private var db: SongsDatabase? = null

        fun getInstance(context: Context): SongsDatabase {
            if (db == null) {
                synchronized(SongsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, SongsDatabase::class.java, "songs.db")
                                .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }
    }
}
