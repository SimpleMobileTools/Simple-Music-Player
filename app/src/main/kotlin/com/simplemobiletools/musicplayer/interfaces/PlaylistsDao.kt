package com.simplemobiletools.musicplayer.interfaces

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Delete
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query
import com.simplemobiletools.musicplayer.models.Playlist

@Dao
interface PlaylistsDao {
    @Insert
    fun insert(playlist: Playlist): Long

    @Delete
    fun deletePlaylists(playlist: List<Playlist>)

    @Query("SELECT * FROM playlists")
    fun getAll(): List<Playlist>

    @Query("DELETE FROM playlists WHERE id = :id")
    fun deletePlaylistById(id: Int)
}
