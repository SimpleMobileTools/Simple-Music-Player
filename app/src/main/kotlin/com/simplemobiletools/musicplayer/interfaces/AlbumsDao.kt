package com.simplemobiletools.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.models.Album

@Dao
interface AlbumsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(album: Album): Long

    @Query("SELECT * FROM albums")
    fun getAll(): List<Album>

    @Query("SELECT * FROM albums WHERE artist_id = :artistId")
    fun getArtistAlbums(artistId: Long): List<Album>

    @Query("DELETE FROM albums WHERE id = :id")
    fun deleteAlbum(id: Long)
}
