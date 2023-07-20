package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import com.simplemobiletools.musicplayer.extensions.albumsDAO
import com.simplemobiletools.musicplayer.extensions.artistDAO
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.playlistDAO
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Track

class AudioHelper(private val context: Context) {

    private val config = context.config
    private val showFilename = config.showFilename

    fun insertTracks(tracks: List<Track>) {
        context.tracksDAO.insertAll(tracks)
    }

    fun getTrack(mediaStoreId: Long): Track? {
        return context.tracksDAO.getTrackWithMediaStoreId(mediaStoreId)
    }

    fun getAllTracks(): ArrayList<Track> {
        val tracks = context.tracksDAO.getAll()
            .distinctBy { "${it.path}/${it.mediaStoreId}" } as ArrayList<Track>

        Track.sorting = config.trackSorting
        tracks.sort()
        return tracks
    }

    fun getFolderTracks(folder: String): ArrayList<Track> {
        val tracks = context.tracksDAO.getTracksFromFolder(folder)
            .distinctBy { "${it.path}/${it.mediaStoreId}" }
            .onEach {
                it.title = it.getProperTitle(showFilename)
            } as ArrayList<Track>

        Track.sorting = config.getProperFolderSorting(folder)
        tracks.sort()
        return tracks
    }

    fun updateTrackInfo(newPath: String, artist: String, title: String, oldPath: String) {
        context.tracksDAO.updateSongInfo(newPath, artist, title, oldPath)
    }

    fun updateTrackFolder(folder: String, mediaStoreId: Long) {
        context.tracksDAO.updateFolderName(folder, mediaStoreId)
    }

    fun deleteTrack(mediaStoreId: Long) {
        context.tracksDAO.removeTrack(mediaStoreId)
    }

    fun deleteTracks(tracks: List<Track>) {
        tracks.forEach {
            deleteTrack(it.mediaStoreId)
        }
    }

    fun insertArtists(artists: List<Artist>) {
        context.artistDAO.insertAll(artists)
    }

    fun getAllArtists(): ArrayList<Artist> {
        val artists = context.artistDAO.getAll() as ArrayList<Artist>
        artists.sort()
        return artists
    }

    fun getArtistAlbums(artistId: Long): ArrayList<Album> {
        return context.albumsDAO.getArtistAlbums(artistId) as ArrayList<Album>
    }

    fun getArtistAlbums(artists: List<Artist>): ArrayList<Album> {
        return artists.flatMap { getArtistAlbums(it.id) } as ArrayList<Album>
    }

    fun getArtistTracks(artistId: Long): ArrayList<Track> {
        return context.tracksDAO.getTracksFromArtist(artistId) as ArrayList<Track>
    }

    fun getArtistTracks(artists: List<Artist>): ArrayList<Track> {
        return getAlbumTracks(
            albums = getArtistAlbums(artists)
        )
    }

    fun deleteArtist(id: Long) {
        context.artistDAO.deleteArtist(id)
    }

    fun deleteArtists(artists: List<Artist>) {
        artists.forEach {
            deleteArtist(it.id)
        }
    }

    fun insertAlbums(albums: List<Album>) {
        context.albumsDAO.insertAll(albums)
    }

    fun getAlbum(albumId: Long): Album? {
        return context.albumsDAO.getAlbumWithId(albumId)
    }

    fun getAllAlbums(): ArrayList<Album> {
        val albums = context.albumsDAO.getAll() as ArrayList<Album>
        albums.sort()
        return albums
    }

    fun getAlbumTracks(albumId: Long): ArrayList<Track> {
        val tracks = context.tracksDAO.getTracksFromAlbum(albumId)
            .distinctBy { "${it.path}/${it.mediaStoreId}" } as ArrayList<Track>
        tracks.sortWith(compareBy({ it.trackId }, { it.title.lowercase() }))
        return tracks
    }

    fun getAlbumTracks(albums: List<Album>): ArrayList<Track> {
        return albums.flatMap { getAlbumTracks(it.id) }
            .distinctBy { "${it.path}/${it.mediaStoreId}" } as ArrayList<Track>
    }

    private fun deleteAlbum(id: Long) {
        context.albumsDAO.deleteAlbum(id)
    }

    fun deleteAlbums(albums: List<Album>) {
        albums.forEach {
            deleteAlbum(it.id)
        }
    }

    fun insertPlaylist(playlist: Playlist): Long {
        return context.playlistDAO.insert(playlist)
    }

    fun updatePlaylist(playlist: Playlist) {
        context.playlistDAO.update(playlist)
    }

    fun getAllPlaylists(): ArrayList<Playlist> {
        return context.playlistDAO.getAll() as ArrayList<Playlist>
    }

    fun getPlaylistTracks(playlistId: Int): ArrayList<Track> {
        val tracks = context.tracksDAO.getTracksFromPlaylist(playlistId).onEach {
            it.title = it.getProperTitle(showFilename)
        } as ArrayList<Track>

        Track.sorting = config.getProperPlaylistSorting(playlistId)
        tracks.sort()
        return tracks
    }

    fun getPlaylistTrackCount(playlistId: Int): Int {
        return context.tracksDAO.getTracksCountFromPlaylist(playlistId)
    }

    fun updateOrderInPlaylist(playlistId: Int, trackId: Long) {
        context.tracksDAO.updateOrderInPlaylist(playlistId, trackId)
    }

    fun deletePlaylists(playlists: ArrayList<Playlist>) {
        context.playlistDAO.deletePlaylists(playlists)
        playlists.forEach {
            context.tracksDAO.removePlaylistSongs(it.id)
        }
    }

    fun removeInvalidAlbumsArtists() {
        val tracks = context.tracksDAO.getAll()
        val albums = context.albumsDAO.getAll()
        val artists = context.artistDAO.getAll()

        val invalidAlbums = albums.filter { album -> tracks.none { it.albumId == album.id } }
        deleteAlbums(invalidAlbums)

        val invalidArtists = artists.filter { artist -> tracks.none { it.artistId == artist.id } }
        deleteArtists(invalidArtists)
    }
}
