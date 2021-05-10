package com.simplemobiletools.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.models.Artist

@Dao
interface ArtistsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(artist: Artist): Long

    @Query("SELECT * FROM artists")
    fun getAll(): List<Artist>
}
