package com.simplemobiletools.musicplayer.interfaces

import androidx.room.*
import com.simplemobiletools.musicplayer.models.Track

@Dao
interface SongsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)

    @Query("SELECT * FROM tracks")
    fun getAll(): List<Track>

    @Query("SELECT * FROM tracks WHERE playlist_id = :playlistId")
    fun getTracksFromPlaylist(playlistId: Int): List<Track>

    @Query("SELECT COUNT(*) FROM tracks WHERE playlist_id = :playlistId")
    fun getTracksCountFromPlaylist(playlistId: Int): Int

    @Delete
    fun removeSongsFromPlaylists(songs: List<Track>)

    @Query("DELETE FROM tracks WHERE media_store_id = :id")
    fun removeTrack(id: Long)

    @Query("DELETE FROM tracks WHERE playlist_id = :playlistId")
    fun removePlaylistSongs(playlistId: Int)

    // this removes the given song from every playlist
    @Query("DELETE FROM tracks WHERE path = :path")
    fun removeSongPath(path: String)

    @Query("UPDATE tracks SET path = :newPath, artist = :artist, title = :title WHERE path = :oldPath")
    fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String)

    @Query("UPDATE tracks SET cover_art = :coverArt WHERE media_store_id = :id")
    fun updateCoverArt(coverArt: String, id: Long)
}
