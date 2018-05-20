package com.simplemobiletools.musicplayer.interfaces

import android.arch.persistence.room.*
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

    @Query("SELECT * FROM playlists WHERE title = :title COLLATE NOCASE")
    fun getPlaylistWithTitle(title: String): Playlist?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistWithId(id: Int): Playlist?

    @Update
    fun update(playlist: Playlist)
}
