package com.simplemobiletools.musicplayer.helpers

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Track

class SimpleMediaScanner(private val context: Application) {

    private val config = context.config

    // store new artists, albums and tracks into our local db, delete invalid items
    fun updateAllDatabases(callback: () -> Unit) {
        ensureBackgroundThread {
            updateCachedArtists { artists ->
                updateCachedAlbums(artists) { albums ->
                    updateCachedTracks(albums)
                }
            }

            callback()
        }
    }

    private fun updateCachedArtists(callback: (artists: ArrayList<Artist>) -> Unit) {
        val artists = getArtistsSync()
        artists.forEach { artist ->
            context.artistDAO.insert(artist)
        }

        // remove invalid artists from cache
        val cachedArtists = context.artistDAO.getAll() as ArrayList<Artist>
        val newIds = artists.map { it.id }
        val idsToRemove = arrayListOf<Long>()
        cachedArtists.forEach { artist ->
            if (!newIds.contains(artist.id)) {
                idsToRemove.add(artist.id)
            }
        }

        idsToRemove.forEach {
            context.artistDAO.deleteArtist(it)
        }

        callback(artists)
    }

    private fun getArtistsSync(): ArrayList<Artist> {
        val artists = ArrayList<Artist>()
        val uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST
        )

        context.queryCursor(uri, projection, showErrors = true) { cursor ->
            val id = cursor.getLongValue(MediaStore.Audio.Artists._ID)
            val title = cursor.getStringValue(MediaStore.Audio.Artists.ARTIST) ?: MediaStore.UNKNOWN_STRING
            var artist = Artist(id, title, 0, 0, 0)
            artist = fillArtistExtras(artist)
            if (artist.albumCnt > 0 && artist.trackCnt > 0) {
                artists.add(artist)
            }
        }

        return artists
    }

    private fun fillArtistExtras(artist: Artist): Artist {
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Albums._ID)
        val selection = "${MediaStore.Audio.Albums.ARTIST_ID} = ?"
        val selectionArgs = arrayOf(artist.id.toString())

        artist.albumCnt = getAlbumsCount(artist)

        context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
            val albumId = cursor.getLongValue(MediaStore.Audio.Albums._ID)
            if (artist.albumArtId == 0L) {
                artist.albumArtId = albumId
            }

            artist.trackCnt += getAlbumTracksCount(albumId)
        }

        return artist
    }

    private fun updateCachedAlbums(artists: ArrayList<Artist>, callback: (albums: ArrayList<Album>) -> Unit) {
        val albums = ArrayList<Album>()
        artists.forEach { artist ->
            val albumsByArtist = getAlbumsSync(artist)
            albums.addAll(albumsByArtist)
        }

        albums.forEach { album ->
            context.albumsDAO.insert(album)
        }

        // remove invalid albums from cache
        val cachedAlbums = context.albumsDAO.getAll() as ArrayList<Album>
        val newIds = albums.map { it.id }
        val idsToRemove = arrayListOf<Long>()
        cachedAlbums.forEach { album ->
            if (!newIds.contains(album.id)) {
                idsToRemove.add(album.id)
            }
        }

        idsToRemove.forEach {
            context.albumsDAO.deleteAlbum(it)
        }

        callback(albums)
    }

    private fun getAlbumsSync(artist: Artist): ArrayList<Album> {
        val albums = ArrayList<Album>()
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.FIRST_YEAR,
            MediaStore.Audio.Albums.ALBUM
        )

        var selection = "${MediaStore.Audio.Albums.ARTIST} = ?"
        var selectionArgs = arrayOf(artist.title)

        if (isQPlus()) {
            selection = "${MediaStore.Audio.Albums.ARTIST_ID} = ?"
            selectionArgs = arrayOf(artist.id.toString())
        }

        context.queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
            val id = cursor.getLongValue(MediaStore.Audio.Albums._ID)
            val artistName = cursor.getStringValue(MediaStore.Audio.Albums.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val title = cursor.getStringValue(MediaStore.Audio.Albums.ALBUM)
            val coverArt = ContentUris.withAppendedId(artworkUri, id).toString()
            val year = cursor.getIntValue(MediaStore.Audio.Albums.FIRST_YEAR)
            val trackCnt = getAlbumTracksCount(id)
            if (trackCnt > 0) {
                val album = Album(id, artistName, title, coverArt, year, trackCnt, artist.id)
                albums.add(album)
            }
        }

        return albums
    }

    private fun getAlbumsCount(artist: Artist): Int {
        val uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Albums._ID)
        var selection = "${MediaStore.Audio.Albums.ARTIST} = ?"
        var selectionArgs = arrayOf(artist.title)

        if (isQPlus()) {
            selection = "${MediaStore.Audio.Albums.ARTIST_ID} = ?"
            selectionArgs = arrayOf(artist.id.toString())
        }

        try {
            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                return cursor.count
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        }

        return 0
    }

    private fun getAlbumTracksSync(albumId: Long): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK
        )

        if (isQPlus()) {
            projection.add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
        }

        val selection = "${MediaStore.Audio.Albums.ALBUM_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val coverUri = ContentUris.withAppendedId(artworkUri, albumId)
        val coverArt = coverUri.toString()
        val showFilename = context.config.showFilename

        context.queryCursor(uri, projection.toTypedArray(), selection, selectionArgs, showErrors = true) { cursor ->
            val id = cursor.getLongValue(MediaStore.Audio.Media._ID)
            val title = cursor.getStringValue(MediaStore.Audio.Media.TITLE)
            val duration = cursor.getIntValue(MediaStore.Audio.Media.DURATION) / 1000
            val trackId = cursor.getIntValue(MediaStore.Audio.Media.TRACK) % 1000
            val path = cursor.getStringValue(MediaStore.Audio.Media.DATA)
            val artist = cursor.getStringValue(MediaStore.Audio.Media.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val album = cursor.getStringValue(MediaStore.Audio.Media.ALBUM)
            val folderName = if (isQPlus()) {
                cursor.getStringValue(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
            } else {
                ""
            }

            val track = Track(0, id, title, artist, path, duration, album, coverArt, 0, trackId, folderName, albumId, 0)
            track.title = track.getProperTitle(showFilename)
            tracks.add(track)
        }

        return tracks
    }

    private fun getAlbumTracksCount(albumId: Long): Int {
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Albums.ALBUM_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        var validTracks = 0
        val excludedFolders = context.config.excludedFolders

        context.queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
            val path = cursor.getStringValue(MediaStore.Audio.Media.DATA)
            if (!excludedFolders.contains(path.getParentPath())) {
                validTracks++
            }
        }

        return validTracks
    }

    private fun updateCachedTracks(albums: ArrayList<Album>) {
        val tracks = albums.flatMap { getAlbumTracksSync(it.id) } as ArrayList<Track>
        val newIds = tracks.map { it.mediaStoreId } as ArrayList<Long>

        // get tracks from MediaStore.Files table as well
        getAllAudioFiles().forEach { track ->
            val trackId = track.mediaStoreId
            if (trackId !in newIds) {
                tracks.add(track)
                newIds.add(trackId)
            }
        }

        // insert all tracks and remove any invalid tracks from cache
        context.tracksDAO.insertAll(tracks)
        val invalidTracks = context.tracksDAO.getAll().filter { it.mediaStoreId !in newIds }
        context.tracksDAO.removeTracks(invalidTracks)

        if (!config.wasAllTracksPlaylistCreated) {
            val allTracksLabel = context.resources.getString(R.string.all_tracks)
            val playlist = Playlist(ALL_TRACKS_PLAYLIST_ID, allTracksLabel)
            context.playlistDAO.insert(playlist)
            tracks.forEach {
                it.playListId = ALL_TRACKS_PLAYLIST_ID
            }
            RoomHelper(context).insertTracksWithPlaylist(tracks)
            config.wasAllTracksPlaylistCreated = true
        }
    }

    private fun getAllAudioFiles(): ArrayList<Track> {
        val tracks = arrayListOf<Track>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.ARTIST,
            MediaStore.Files.FileColumns.ALBUM,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
        val showFilename = config.showFilename

        context.queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
            val id = cursor.getLongValue(MediaStore.Files.FileColumns._ID)
            val title = cursor.getStringValue(MediaStore.Files.FileColumns.TITLE)
            val duration = cursor.getIntValue(MediaStore.Files.FileColumns.DURATION) / 1000
            val path = cursor.getStringValue(MediaStore.Files.FileColumns.DATA)
            val artist = cursor.getStringValue(MediaStore.Files.FileColumns.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val album = cursor.getStringValue(MediaStore.Files.FileColumns.ALBUM)
            val folderName = if (isQPlus()) {
                cursor.getStringValue(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
            } else {
                ""
            }

            if (duration > 5) {
                val track = Track(0, id, title, artist, path, duration, album, "", 0, 0, folderName, 0, 0)
                track.title = track.getProperTitle(showFilename)
                tracks.add(track)
            }
        }

        return tracks
    }
}
