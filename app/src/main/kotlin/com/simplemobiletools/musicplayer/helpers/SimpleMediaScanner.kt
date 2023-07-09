package com.simplemobiletools.musicplayer.helpers

import android.app.Application
import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.*
import android.provider.MediaStore
import android.provider.MediaStore.Files
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Track
import java.io.File
import java.io.FileInputStream

class SimpleMediaScanner(private val context: Application) {

    private val config = context.config
    private var scanning = false
    private var onScanComplete: (() -> Unit)? = null

    private val allTracks = arrayListOf<Track>()
    private val tracksGroupedByArtists = mutableMapOf<String, List<Track>>()
    private val tracksGroupedByAlbums = mutableMapOf<String, List<Track>>()

    // store new artists, albums and tracks into our local db, delete invalid items
    @Synchronized
    fun scan(callback: (() -> Unit)? = null) {
        onScanComplete = callback
        if (scanning) {
            return
        }

        scanning = true
        ensureBackgroundThread {
            if (config.scanFilesManually) {
                scanFilesManually()
            } else {
                scanMediaStoreFiles()
            }

            tracksGroupedByArtists.putAll(allTracks.groupBy { it.artist })
            tracksGroupedByAlbums.putAll(allTracks.groupBy { it.album })
            updateAllDatabases()
        }
    }

    fun isScanning(): Boolean = scanning

    private fun updateAllDatabases() {
        val artists = updateCachedArtists()
        val albums = updateCachedAlbums(artists)
        val tracks = updateCachedTracks(albums)
        performCleanup(artists, albums, tracks)
        onScanComplete?.invoke()
    }

    private fun scanMediaStoreFiles() {
        val uri = Files.getContentUri("external")
        val projection = arrayOf(
            Files.FileColumns._ID,
            Files.FileColumns.DURATION,
            Files.FileColumns.DATA,
            Files.FileColumns.TITLE,
            Files.FileColumns.ARTIST,
            Files.FileColumns.ALBUM_ARTIST,
            Files.FileColumns.ALBUM,
            Files.FileColumns.BUCKET_DISPLAY_NAME
        )

        val selection = "${Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
        val showFilename = config.showFilename
        val excludedFolders = config.excludedFolders

        context.queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
            val id = cursor.getLongValue(Files.FileColumns._ID)
            val title = cursor.getStringValue(Files.FileColumns.TITLE)
            val duration = cursor.getIntValue(Files.FileColumns.DURATION) / 1000
            val path = cursor.getStringValue(Files.FileColumns.DATA)
            val parentPath = path.getParentPath()
            val artist = cursor.getStringValue(Files.FileColumns.ALBUM_ARTIST) ?: cursor.getStringValue(Files.FileColumns.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val folderName = if (isQPlus()) {
                cursor.getStringValue(Files.FileColumns.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
            } else {
                parentPath.getFilenameFromPath()
            }

            val album = cursor.getStringValue(Files.FileColumns.ALBUM) ?: folderName
            if (parentPath !in excludedFolders) {
                val track = Track(0, id, title, artist, path, duration, album, "", 0, 0, folderName, 0, 0)
                track.title = track.getProperTitle(showFilename)
                allTracks.add(track)
            }
        }
    }

    private fun scanFilesManually() {
        val audioFiles = arrayListOf<File>()
        for (rootPath in arrayOf(context.internalStoragePath, context.sdCardPath)) {
            if (rootPath.isEmpty()) {
                continue
            }

            val rootFile = File(rootPath)
            findAudioFiles(rootFile, audioFiles)
        }

        try {
            val retriever = MediaMetadataRetriever()
            for (file in audioFiles) {
                val path = file.absolutePath
                var inputStream: FileInputStream? = null

                try {
                    retriever.setDataSource(path)
                } catch (ignored: Exception) {
                    try {
                        inputStream = file.inputStream()
                        retriever.setDataSource(inputStream.fd)
                    } catch (ignored: Exception) {
                        continue
                    }
                }

                val id = 0L
                val title = retriever.extractMetadata(METADATA_KEY_TITLE) ?: path.getFilenameFromPath()
                val artist = retriever.extractMetadata(METADATA_KEY_ALBUMARTIST) ?: retriever.extractMetadata(METADATA_KEY_ARTIST) ?: MediaStore.UNKNOWN_STRING
                val duration = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLong()?.div(1000)?.toInt() ?: 0
                val folderName = file.parent?.getFilenameFromPath()
                val album = retriever.extractMetadata(METADATA_KEY_ALBUM) ?: folderName ?: MediaStore.UNKNOWN_STRING
                val trackNumber = retriever.extractMetadata(METADATA_KEY_CD_TRACK_NUMBER)
                val trackId = trackNumber?.split("/")?.first()?.toInt() ?: 0

                if (title.isNotEmpty() && folderName != null) {
                    val track = Track(0, id, title, artist, path, duration, album, "", 0, trackId, folderName, 0, 0, FLAG_MANUAL_CACHE)
                    allTracks.add(track)
                }

                try {
                    inputStream?.close()
                } catch (ignored: Exception) {
                }
            }

            retriever.release()
        } catch (ignored: Exception) {
        }
    }

    private fun findAudioFiles(file: File, destination: ArrayList<File>) {
        if (file.isHidden || file.absolutePath.getParentPath() in config.excludedFolders) {
            return
        }

        if (file.isFile) {
            if (file.isAudioSlow()) {
                destination.add(file)
            }
        } else if (!file.containsNoMedia()) {
            file.listFiles().orEmpty().forEach { child ->
                findAudioFiles(child, destination)
            }
        }
    }

    private fun updateCachedArtists(): ArrayList<Artist> {
        val artists = getArtistsSync()
        artists.forEach { artist ->
            context.artistDAO.insert(artist)
        }

        // remove invalid artists from cache
        val cachedArtists = context.artistDAO.getAll() as ArrayList<Artist>
        val newIds = artists.map { it.id }
        cachedArtists.forEach { artist ->
            val id = artist.id
            if (id !in newIds) {
                context.artistDAO.deleteArtist(id)
            }
        }

        return artists
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

        // get artists from other sources (e.g. MediaStore.Files)
        val foundArtists = artists.map { it.title }
        for ((artistName, tracks) in tracksGroupedByArtists) {
            val albumCnt = tracks.groupBy { it.album }.size
            val trackCnt = tracks.size
            val artist = Artist(0, artistName, albumCnt, trackCnt, 0)
            if (artistName !in foundArtists) {
                artist.id = artist.hashCode().toLong()
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

    private fun updateCachedAlbums(artists: ArrayList<Artist>): ArrayList<Album> {
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
        cachedAlbums.forEach { album ->
            val id = album.id
            if (id !in newIds) {
                context.albumsDAO.deleteAlbum(id)
            }
        }

        return albums
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

        // get albums from other sources (e.g. MediaStore.Files)
        val foundAlbums = albums.map { it.title }
        val tracksByArtist = tracksGroupedByArtists[artist.title] ?: return albums
        val groupedByAlbums = tracksByArtist.groupBy { it.album }
        for ((albumName, tracks) in groupedByAlbums) {
            val trackCnt = tracks.size
            val album = Album(0, artist.title, albumName, "", 0, trackCnt, artist.id)
            if (albumName !in foundAlbums) {
                album.id = album.hashCode().toLong()
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
        val showFilename = config.showFilename

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

    private fun getAlbumTracksFromOtherSources(album: Album): ArrayList<Track> {
        val tracks = arrayListOf<Track>()
        val albumId = album.id
        val albumTracks = tracksGroupedByAlbums[album.title] ?: return tracks
        albumTracks.forEach { track ->
            track.albumId = albumId
            if (track.mediaStoreId == 0L) {
                // use hashCode as id for tracking purposes, there's a very slim chance of collision
                val trackId = track.hashCode().toLong()
                track.mediaStoreId = trackId
            }
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
        val excludedFolders = config.excludedFolders

        context.queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
            val path = cursor.getStringValue(MediaStore.Audio.Media.DATA)
            if (!excludedFolders.contains(path.getParentPath())) {
                validTracks++
            }
        }

        return validTracks
    }

    private fun updateCachedTracks(albums: ArrayList<Album>): ArrayList<Track> {
        val tracks = albums.flatMap { getAlbumTracksSync(it.id) } as ArrayList<Track>
        val trackIds = tracks.map { it.mediaStoreId } as ArrayList<Long>
        val trackPaths = tracks.map { it.path } as ArrayList<String>

        // get tracks from other sources e.g. MediaStore.Files table or directly from storage files if manual scan is enabled
        val tracksFromOtherSources = albums.flatMap { getAlbumTracksFromOtherSources(it) } as ArrayList<Track>
        tracksFromOtherSources.forEach { track ->
            val trackId = track.mediaStoreId
            val trackPath = track.path
            if (trackId !in trackIds && trackPath !in trackPaths) {
                tracks.add(track)
                trackIds.add(trackId)
                trackPaths.add(trackPath)
            }
        }

        // insert all tracks and remove any invalid tracks from cache
        context.tracksDAO.insertAll(tracks)
        val invalidTracks = context.tracksDAO.getAll().filter { it.mediaStoreId !in trackIds || it.path !in trackPaths }
        context.tracksDAO.removeTracks(invalidTracks)
        tracks.removeAll(invalidTracks.toSet())

        if (!config.wasAllTracksPlaylistCreated) {
            val allTracksLabel = context.resources.getString(R.string.all_tracks)
            val playlist = Playlist(ALL_TRACKS_PLAYLIST_ID, allTracksLabel)
            context.playlistDAO.insert(playlist)
            config.wasAllTracksPlaylistCreated = true
        }

        // avoid re-adding tracks that have been explicitly removed from 'All tracks' playlist
        val excludedFolders = config.excludedFolders
        val tracksRemovedFromAllTracks = config.tracksRemovedFromAllTracksPlaylist.map { it.toLong() }
        val tracksWithPlaylist = tracks
            .filter { it.mediaStoreId !in tracksRemovedFromAllTracks && it.playListId == 0 && it.path.getParentPath() !in excludedFolders }
            .onEach { it.playListId = ALL_TRACKS_PLAYLIST_ID }
        RoomHelper(context).insertTracksWithPlaylist(tracksWithPlaylist as ArrayList<Track>)

        return tracks
    }

    private fun performCleanup(artists: ArrayList<Artist>, albums: ArrayList<Album>, tracks: ArrayList<Track>) {
        allTracks.clear()
        tracksGroupedByArtists.clear()
        tracksGroupedByAlbums.clear()
        scanning = false

        // remove albums without any tracks
        val invalidAlbums = mutableSetOf<Album>()
        for (album in albums) {
            val albumId = album.id
            val albumTracks = tracks.filter { it.albumId == albumId }
            if (albumTracks.isEmpty()) {
                invalidAlbums.add(album)
                context.albumsDAO.deleteAlbum(albumId)
            }
        }
        albums.removeAll(invalidAlbums)

        // remove artists without any albums
        for (artist in artists) {
            val artistId = artist.id
            val albumsByArtist = albums.filter { it.artistId == artistId }
            if (albumsByArtist.isEmpty()) {
                context.artistDAO.deleteArtist(artistId)
                continue
            }

            // update album, track counts
            val albumCnt = albumsByArtist.size
            val trackCnt = albumsByArtist.sumOf { it.trackCnt }
            if (trackCnt != artist.trackCnt || albumCnt != artist.albumCnt) {
                context.artistDAO.deleteArtist(artistId)
                val updated = artist.copy(trackCnt = trackCnt, albumCnt = albumCnt)
                context.artistDAO.insert(updated)
            }
        }
    }

    companion object {
        private var instance: SimpleMediaScanner? = null

        fun getInstance(app: Application): SimpleMediaScanner {
            return if (instance != null) {
                instance!!
            } else {
                instance = SimpleMediaScanner(app)
                instance!!
            }
        }
    }
}
