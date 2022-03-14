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

    @Query("SELECT * FROM tracks WHERE album_id = :albumId")
    fun getTracksFromAlbum(albumId: Long): List<Track>

    @Query("SELECT COUNT(*) FROM tracks WHERE playlist_id = :playlistId")
    fun getTracksCountFromPlaylist(playlistId: Int): Int

    @Query("SELECT * FROM tracks WHERE folder_name = :folderName COLLATE NOCASE GROUP BY media_store_id")
    fun getTracksFromFolder(folderName: String): List<Track>

    @Query("SELECT * FROM tracks WHERE media_store_id = :mediaStoreId")
    fun getTrackWithMediaStoreId(mediaStoreId: Long): Track?

    @Delete
    fun removeSongsFromPlaylists(songs: List<Track>)

    @Query("DELETE FROM tracks WHERE media_store_id = :mediaStoreId")
    fun removeTrack(mediaStoreId: Long)

    @Query("DELETE FROM tracks WHERE playlist_id = :playlistId")
    fun removePlaylistSongs(playlistId: Int)

    @Query("UPDATE tracks SET path = :newPath, artist = :artist, title = :title WHERE path = :oldPath")
    fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String)

    @Query("UPDATE tracks SET cover_art = :coverArt WHERE media_store_id = :id")
    fun updateCoverArt(coverArt: String, id: Long)

    @Query("UPDATE tracks SET folder_name = :folderName WHERE media_store_id = :id")
    fun updateFolderName(folderName: String, id: Long)
}
