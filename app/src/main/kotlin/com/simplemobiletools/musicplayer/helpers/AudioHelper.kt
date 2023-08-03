package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import com.simplemobiletools.commons.extensions.getParentPath
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.models.*

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

        tracks.sortSafely(config.trackSorting)
        return tracks
    }

    fun getAllFolders(): ArrayList<Folder> {
        val tracks = context.audioHelper.getAllTracks()
        val foldersMap = tracks.groupBy { it.folderName }
        val folders = ArrayList<Folder>()
        val excludedFolders = config.excludedFolders
        for ((title, folderTracks) in foldersMap) {
            val path = (folderTracks.firstOrNull()?.path?.getParentPath() ?: "").removeSuffix("/")
            if (excludedFolders.contains(path)) {
                continue
            }

            val folder = Folder(title, folderTracks.size, path)
            folders.add(folder)
        }

        return folders
    }

    fun getFolderTracks(folder: String): ArrayList<Track> {
        val tracks = context.tracksDAO.getTracksFromFolder(folder)
            .distinctBy { "${it.path}/${it.mediaStoreId}" }
            .onEach {
                it.title = it.getProperTitle(showFilename)
            } as ArrayList<Track>

        tracks.sortSafely(config.getProperFolderSorting(folder))
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
        artists.sortSafely(config.artistSorting)
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
        albums.sortSafely(config.albumSorting)
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

    fun getAllGenres(): ArrayList<Genre> {
        val genres = context.genresDAO.getAll() as ArrayList<Genre>
        genres.sortSafely(config.genreSorting)
        return genres
    }

    fun getGenreTracks(genreId: Long): ArrayList<Track> {
        val tracks = context.tracksDAO.getGenreTracks(genreId)
            .distinctBy { "${it.path}/${it.mediaStoreId}" } as ArrayList<Track>

        tracks.sortSafely(config.trackSorting)
        return tracks
    }

    fun getGenreTracks(genres: List<Genre>): ArrayList<Track> {
        val tracks = genres.flatMap { context.tracksDAO.getGenreTracks(it.id) }
            .distinctBy { "${it.path}/${it.mediaStoreId}" } as ArrayList<Track>

        tracks.sortSafely(config.trackSorting)
        return tracks
    }

    private fun deleteGenre(id: Long) {
        context.genresDAO.deleteGenre(id)
    }

    fun deleteGenres(genres: List<Genre>) {
        genres.forEach {
            deleteGenre(it.id)
        }
    }

    fun insertGenres(genres: List<Genre>) {
        genres.forEach {
            context.genresDAO.insert(it)
        }
    }

    fun getPlaylistTracks(playlistId: Int): ArrayList<Track> {
        val tracks = context.tracksDAO.getTracksFromPlaylist(playlistId).onEach {
            it.title = it.getProperTitle(showFilename)
        } as ArrayList<Track>

        tracks.sortSafely(config.getProperPlaylistSorting(playlistId))
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

    fun updateQueue(items: List<QueueItem>, currentTrackId: Long? = null, startPosition: Long? = null) {
        context.queueDAO.deleteAllItems()
        context.queueDAO.insertAll(items)
        if (currentTrackId != null && startPosition != null) {
            val startPositionSeconds = (startPosition / 1000).toInt()
            context.queueDAO.saveCurrentTrackProgress(currentTrackId, startPositionSeconds)
        } else if (currentTrackId != null) {
            context.queueDAO.saveCurrentTrack(currentTrackId)
        }
    }
}
