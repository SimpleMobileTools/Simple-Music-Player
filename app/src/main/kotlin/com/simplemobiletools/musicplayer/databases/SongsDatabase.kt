package com.simplemobiletools.musicplayer.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplemobiletools.musicplayer.interfaces.*
import com.simplemobiletools.musicplayer.models.*
import com.simplemobiletools.musicplayer.objects.MyExecutor

@Database(entities = [Track::class, Playlist::class, QueueItem::class, Artist::class, Album::class], version = 10)
abstract class SongsDatabase : RoomDatabase() {

    abstract fun SongsDao(): SongsDao

    abstract fun PlaylistsDao(): PlaylistsDao

    abstract fun QueueItemsDao(): QueueItemsDao

    abstract fun ArtistsDao(): ArtistsDao

    abstract fun AlbumsDao(): AlbumsDao

    companion object {
        private var db: SongsDatabase? = null

        fun getInstance(context: Context): SongsDatabase {
            if (db == null) {
                synchronized(SongsDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, SongsDatabase::class.java, "songs.db")
                            .setQueryExecutor(MyExecutor.myExecutor)
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .addMigrations(MIGRATION_4_5)
                            .addMigrations(MIGRATION_5_6)
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }

        // removing the "type" value of Song
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                        "CREATE TABLE songs_new (media_store_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, path TEXT NOT NULL, duration INTEGER NOT NULL, " +
                            "album TEXT NOT NULL, playlist_id INTEGER NOT NULL, PRIMARY KEY(path, playlist_id))"
                    )

                    execSQL(
                        "INSERT INTO songs_new (media_store_id, title, artist, path, duration, album, playlist_id) " +
                            "SELECT media_store_id, title, artist, path, duration, album, playlist_id FROM songs"
                    )

                    execSQL("DROP TABLE songs")
                    execSQL("ALTER TABLE songs_new RENAME TO songs")

                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_id` ON `playlists` (`id`)")
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN track_id INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE songs ADD COLUMN cover_art TEXT default '' NOT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE `queue_items` (`track_id` INTEGER NOT NULL PRIMARY KEY, `track_order` INTEGER NOT NULL, `is_current` INTEGER NOT NULL, `last_position` INTEGER NOT NULL)")
            }
        }

        // change the primary keys from path + playlist_id to media_store_id + playlist_id
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                        "CREATE TABLE songs_new (media_store_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, path TEXT NOT NULL, duration INTEGER NOT NULL, " +
                            "album TEXT NOT NULL, cover_art TEXT default '' NOT NULL, playlist_id INTEGER NOT NULL, track_id INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(media_store_id, playlist_id))"
                    )

                    execSQL(
                        "INSERT OR IGNORE INTO songs_new (media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id) " +
                            "SELECT media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id FROM songs"
                    )

                    execSQL("DROP TABLE songs")
                    execSQL("ALTER TABLE songs_new RENAME TO tracks")
                }
            }
        }

        // adding an autoincrementing "id" field, replace primary keys with indices
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL(
                        "CREATE TABLE tracks_new (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `media_store_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `path` TEXT NOT NULL, `duration` INTEGER NOT NULL, " +
                            "`album` TEXT NOT NULL, `cover_art` TEXT default '' NOT NULL, `playlist_id` INTEGER NOT NULL, `track_id` INTEGER NOT NULL DEFAULT 0)"
                    )

                    execSQL(
                        "INSERT OR IGNORE INTO tracks_new (media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id) " +
                            "SELECT media_store_id, title, artist, path, duration, album, cover_art, playlist_id, track_id FROM tracks"
                    )

                    execSQL("DROP TABLE tracks")
                    execSQL("ALTER TABLE tracks_new RENAME TO tracks")

                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_id` ON `tracks` (`media_store_id`, `playlist_id`)")
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("CREATE TABLE `artists` (`id` INTEGER NOT NULL PRIMARY KEY, `title` TEXT NOT NULL, `album_cnt` INTEGER NOT NULL, `track_cnt` INTEGER NOT NULL, `album_art_id` INTEGER NOT NULL)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_id` ON `artists` (`id`)")

                    execSQL("CREATE TABLE `albums` (`id` INTEGER NOT NULL PRIMARY KEY, `artist` TEXT NOT NULL, `title` TEXT NOT NULL, `cover_art` TEXT NOT NULL, `year` INTEGER NOT NULL)")
                    execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_albums_id` ON `albums` (`id`)")
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN folder_name TEXT default '' NOT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE albums ADD COLUMN track_cnt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE albums ADD COLUMN artist_id INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tracks ADD COLUMN album_id INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
