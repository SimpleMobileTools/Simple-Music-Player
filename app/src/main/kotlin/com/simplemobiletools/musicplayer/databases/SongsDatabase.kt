package com.simplemobiletools.musicplayer.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.ALL_TRACKS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.helpers.RoomHelper
import com.simplemobiletools.musicplayer.interfaces.PlaylistsDao
import com.simplemobiletools.musicplayer.interfaces.QueueItemsDao
import com.simplemobiletools.musicplayer.interfaces.SongsDao
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.QueueItem
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.objects.MyExecutor
import java.util.concurrent.Executors

@Database(entities = [(Track::class), (Playlist::class), QueueItem::class], version = 5)
abstract class SongsDatabase : RoomDatabase() {

    abstract fun SongsDao(): SongsDao

    abstract fun PlaylistsDao(): PlaylistsDao

    abstract fun QueueItemsDao(): QueueItemsDao

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
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .addMigrations(MIGRATION_3_4)
                            .addMigrations(MIGRATION_4_5)
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
            val allTracksLabel = context.resources.getString(R.string.all_tracks)
            val playlist = Playlist(ALL_TRACKS_PLAYLIST_ID, allTracksLabel)
            context.playlistDAO.insert(playlist)

            val allTracks = ArrayList<Track>()
            context.getArtistsSync().forEach { artist ->
                context.getAlbumsSync(artist).forEach { album ->
                    context.getAlbumTracksSync(album.id).forEach {
                        it.playListId = ALL_TRACKS_PLAYLIST_ID
                        allTracks.add(it)
                    }
                }
            }

            RoomHelper(context).insertTracksWithPlaylist(allTracks)
        }

        // removing the "type" value of Song
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("CREATE TABLE songs_new (media_store_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, path TEXT NOT NULL, duration INTEGER NOT NULL, " +
                            "album TEXT NOT NULL, playlist_id INTEGER NOT NULL, PRIMARY KEY(path, playlist_id))")

                    execSQL("INSERT INTO songs_new (media_store_id, title, artist, path, duration, album, playlist_id) " +
                            "SELECT media_store_id, title, artist, path, duration, album, playlist_id FROM songs")

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
                database.execSQL("CREATE TABLE `queue_items` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `track_id` INTEGER NOT NULL, " +
                        "`track_order` INTEGER NOT NULL, `is_playing` INTEGER NOT NULL, `last_position` INTEGER NOT NULL, `was_played` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX `index_queue_items_id` ON `queue_items` (`id`)")
            }
        }

        // change the primary keys from path + playlist_id to id + playlist_id
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.apply {
                    execSQL("CREATE TABLE songs_new (media_store_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, path TEXT NOT NULL, duration INTEGER NOT NULL, " +
                            "album TEXT NOT NULL, playlist_id INTEGER NOT NULL, track_id INTEGER NOT NULL DEFAULT 0, " +
                            "cover_art TEXT default '' NOT NULL, PRIMARY KEY(media_store_id, playlist_id))")

                    execSQL("INSERT INTO songs_new (media_store_id, title, artist, path, duration, album, playlist_id) " +
                            "SELECT media_store_id, title, artist, path, duration, album, playlist_id FROM songs")

                    execSQL("DROP TABLE songs")
                    execSQL("ALTER TABLE songs_new RENAME TO tracks")
                }
            }
        }
    }
}
