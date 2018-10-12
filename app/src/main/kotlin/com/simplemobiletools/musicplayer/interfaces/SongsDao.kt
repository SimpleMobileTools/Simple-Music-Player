package com.simplemobiletools.musicplayer.interfaces

import androidx.room.*
import com.simplemobiletools.musicplayer.models.Song

@Dao
interface SongsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(songs: List<Song>)

    @Query("SELECT * FROM songs WHERE playlist_id = :playlistId")
    fun getSongsFromPlaylist(playlistId: Int): List<Song>

    @Delete
    fun removeSongsFromPlaylists(songs: List<Song>)

    @Query("DELETE FROM songs WHERE playlist_id = :playlistId")
    fun removePlaylistSongs(playlistId: Int)

    // this removes the given song from every playlist
    @Query("DELETE FROM songs WHERE path = :path")
    fun removeSongPath(path: String)

    @Query("UPDATE songs SET path = :newPath, artist = :artist, title = :title WHERE path = :oldPath")
    fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String)
}
