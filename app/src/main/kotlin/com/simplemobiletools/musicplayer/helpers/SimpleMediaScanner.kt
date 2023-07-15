package com.simplemobiletools.musicplayer.helpers

import android.app.Application
import android.content.ContentUris
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.*
import android.provider.MediaStore
import android.provider.MediaStore.Audio
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
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap

class SimpleMediaScanner(private val context: Application) {

    private val config = context.config
    private var scanning = false
    private var onScanComplete: ((complete: Boolean) -> Unit)? = null

    private val newTracks = arrayListOf<Track>()
    private val newAlbums = arrayListOf<Album>()
    private val newArtists = arrayListOf<Artist>()

    fun isScanning(): Boolean = scanning

    // store new artists, albums and tracks into our local db, delete invalid items
    @Synchronized
    fun scan(callback: ((complete: Boolean) -> Unit)? = null) {
        onScanComplete = callback
        if (scanning) {
            return
        }

        scanning = true
        ensureBackgroundThread {
            try {
                scanMediaStore()
                onScanComplete?.invoke(false)
                // scan storage files directly because some files just don't show up in MediaStore
                scanFilesManually()

                performCleanup()
                onScanComplete?.invoke(true)
            } catch (ignored: Exception) {
            } finally {
                newTracks.clear()
                newAlbums.clear()
                newArtists.clear()
                scanning = false
            }
        }
    }

    private fun scanMediaStore() {
        newTracks += getTracksSync()
        newArtists += getArtistsSync()
        newAlbums += getAlbumsSync()

        // ignore tracks from excluded folders and tracks with no albums, artists
        val albumIds = newAlbums.map { it.id }
        val artistIds = newArtists.map { it.id }
        val excludedFolders = config.excludedFolders
        val tracksToExclude = mutableSetOf<Track>()
        for (track in newTracks) {
            if (track.path.getParentPath() in excludedFolders) {
                tracksToExclude.add(track)
                continue
            }

            if (track.albumId !in albumIds || track.artistId !in artistIds) {
                tracksToExclude.add(track)
            }
        }

        newTracks.removeAll(tracksToExclude)

        // update album, track count if any tracks were excluded
        for (album in newAlbums) {
            album.trackCnt = newTracks.filter { it.albumId == album.id }.size
        }

        for (artist in newArtists) {
            artist.trackCnt = newTracks.filter { it.artistId == artist.id }.size
            artist.albumCnt = newAlbums.filter { it.artistId == artist.id }.size
        }

        updateAllDatabases()
    }

    private fun updateAllDatabases() {
        context.tracksDAO.insertAll(newTracks)
        context.albumsDAO.insertAll(newAlbums)
        context.artistDAO.insertAll(newArtists)
        updateAllTracksPlaylist()
    }

    private fun updateAllTracksPlaylist() {
        if (!config.wasAllTracksPlaylistCreated) {
            val allTracksLabel = context.resources.getString(R.string.all_tracks)
            val playlist = Playlist(ALL_TRACKS_PLAYLIST_ID, allTracksLabel)
            context.playlistDAO.insert(playlist)
            config.wasAllTracksPlaylistCreated = true
        }

        // avoid re-adding tracks that have been explicitly removed from 'All tracks' playlist
        val excludedFolders = config.excludedFolders
        val tracksRemovedFromAllTracks = config.tracksRemovedFromAllTracksPlaylist.map { it.toLong() }
        val tracksWithPlaylist = newTracks
            .filter { it.mediaStoreId !in tracksRemovedFromAllTracks && it.playListId == 0 && it.path.getParentPath() !in excludedFolders }
            .onEach { it.playListId = ALL_TRACKS_PLAYLIST_ID }
        RoomHelper(context).insertTracksWithPlaylist(tracksWithPlaylist as ArrayList<Track>)
    }

    private fun getTracksSync(): ArrayList<Track> {
        val tracks = arrayListOf<Track>()
        val uri = Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayListOf(
            Audio.Media._ID,
            Audio.Media.DURATION,
            Audio.Media.DATA,
            Audio.Media.TITLE,
            Audio.Media.ARTIST,
            Audio.Media.ALBUM,
            Audio.Media.ALBUM_ID,
            Audio.Media.ARTIST_ID,
            Audio.Media.TRACK,
            Audio.Media.YEAR
        )

        if (isQPlus()) {
            projection.add(Audio.Media.BUCKET_DISPLAY_NAME)
        }

        val showFilename = config.showFilename
        context.queryCursor(uri, projection.toTypedArray(), showErrors = true) { cursor ->
            val id = cursor.getLongValue(Audio.Media._ID)
            val title = cursor.getStringValue(Audio.Media.TITLE)
            val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
            val trackId = cursor.getIntValue(Audio.Media.TRACK) % 1000
            val path = cursor.getStringValue(Audio.Media.DATA)
            val artist = cursor.getStringValue(Audio.Media.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val album = cursor.getStringValue(Audio.Media.ALBUM)
            val albumId = cursor.getLongValue(Audio.Media.ALBUM_ID)
            val artistId = cursor.getLongValue(Audio.Media.ARTIST_ID)
            val year = cursor.getIntValue(Audio.Media.YEAR)
            val folderName = if (isQPlus()) {
                cursor.getStringValue(Audio.Media.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
            } else {
                ""
            }

            val coverUri = ContentUris.withAppendedId(artworkUri, albumId)
            val coverArt = coverUri.toString()

            val track = Track(0, id, title, artist, path, duration, album, coverArt, 0, trackId, folderName, albumId, artistId, year, 0)
            track.title = track.getProperTitle(showFilename)
            tracks.add(track)
        }

        return tracks
    }

    private fun getArtistsSync(): ArrayList<Artist> {
        val artists = arrayListOf<Artist>()
        val uri = Audio.Artists.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            Audio.Artists._ID,
            Audio.Artists.ARTIST,
            Audio.Artists.NUMBER_OF_TRACKS,
            Audio.Artists.NUMBER_OF_ALBUMS
        )

        context.queryCursor(uri, projection, showErrors = true) { cursor ->
            val id = cursor.getLongValue(Audio.Artists._ID)
            val title = cursor.getStringValue(Audio.Artists.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val albumCnt = cursor.getIntValue(Audio.Artists.NUMBER_OF_TRACKS)
            val trackCnt = cursor.getIntValue(Audio.Artists.NUMBER_OF_ALBUMS)
            val artist = Artist(id, title, albumCnt, trackCnt, 0)
            if (artist.albumCnt > 0 && artist.trackCnt > 0) {
                newArtists.add(artist)
            }
        }

        return artists
    }

    private fun getAlbumsSync(): ArrayList<Album> {
        val albums = arrayListOf<Album>()
        val uri = Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayListOf(
            Audio.Albums._ID,
            Audio.Albums.ARTIST,
            Audio.Albums.FIRST_YEAR,
            Audio.Albums.ALBUM,
            Audio.Albums.NUMBER_OF_SONGS
        )

        if (isQPlus()) {
            projection.add(Audio.Albums.ARTIST_ID)
        }

        context.queryCursor(uri, projection.toTypedArray(), null, null, showErrors = true) { cursor ->
            val id = cursor.getLongValue(Audio.Albums._ID)
            val artistName = cursor.getStringValue(Audio.Albums.ARTIST) ?: MediaStore.UNKNOWN_STRING
            val title = cursor.getStringValue(Audio.Albums.ALBUM)
            val coverArt = ContentUris.withAppendedId(artworkUri, id).toString()
            val year = cursor.getIntValue(Audio.Albums.FIRST_YEAR)
            val trackCnt = cursor.getIntValue(Audio.Albums.NUMBER_OF_SONGS)
            val artistId = if (isQPlus()) {
                cursor.getLongValue(Audio.Albums.ARTIST_ID)
            } else {
                0
            }

            if (trackCnt > 0) {
                val album = Album(id, artistName, title, coverArt, year, trackCnt, artistId)
                albums.add(album)
            }
        }

        return albums
    }

    private fun scanFilesManually() {
        val trackPaths = newTracks.map { it.path }
        val artistNames = newArtists.map { it.title }
        val albumNames = newAlbums.map { it.title }

        val tracks = findTracksManually(pathsToIgnore = trackPaths)
        if (tracks.isNotEmpty()) {
            val artists = splitIntoArtists(tracks)
            val albums = splitIntoAlbums(tracks)

            newTracks += tracks.filter { it.path !in trackPaths }
            newAlbums += albums.filter { it.title !in albumNames }
            newArtists += artists.filter { it.title !in artistNames }

            updateAllDatabases()
        }
    }

    private fun findTracksManually(pathsToIgnore: List<String>): ArrayList<Track> {
        val audioFilePaths = arrayListOf<String>()
        val excludedPaths = pathsToIgnore.toMutableList().apply { addAll(0, config.excludedFolders) }

        for (rootPath in arrayOf(context.internalStoragePath, context.sdCardPath)) {
            if (rootPath.isEmpty()) {
                continue
            }

            val rootFile = File(rootPath)
            findAudioFiles(rootFile, audioFilePaths, excludedPaths)
        }

        val tracksSet = ConcurrentHashMap.newKeySet<Track>()
        val paths = audioFilePaths.toTypedArray()
        Arrays.stream(paths).parallel().forEach { path ->
            val retriever = MediaMetadataRetriever()
            var inputStream: FileInputStream? = null

            try {
                retriever.setDataSource(path)
            } catch (ignored: Exception) {
                try {
                    inputStream = FileInputStream(path)
                    retriever.setDataSource(inputStream.fd)
                } catch (ignored: Exception) {
                    retriever.release()
                    inputStream?.close()
                    return@forEach
                }
            }

            val id = 0L
            val title = retriever.extractMetadata(METADATA_KEY_TITLE) ?: path.getFilenameFromPath()
            val artist = retriever.extractMetadata(METADATA_KEY_ARTIST) ?: retriever.extractMetadata(METADATA_KEY_ALBUMARTIST) ?: MediaStore.UNKNOWN_STRING
            val duration = retriever.extractMetadata(METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000)?.toInt() ?: 0
            val folderName = path.getParentPath().getFilenameFromPath()
            val album = retriever.extractMetadata(METADATA_KEY_ALBUM) ?: folderName
            val trackNumber = retriever.extractMetadata(METADATA_KEY_CD_TRACK_NUMBER)
            val trackId = trackNumber?.split("/")?.first()?.toIntOrNull() ?: 0
            val year = retriever.extractMetadata(METADATA_KEY_YEAR)?.toIntOrNull() ?: 0

            if (title.isNotEmpty()) {
                val track = Track(0, id, title, artist, path, duration, album, "", 0, trackId, folderName, 0, 0, year, 0, FLAG_MANUAL_CACHE)
                // use hashCode() as id for tracking purposes, there's a very slim chance of collision
                track.mediaStoreId = track.hashCode().toLong()
                tracksSet.add(track)
            }

            try {
                inputStream?.close()
                retriever.release()
            } catch (ignored: Exception) {
            }
        }

        val tracks = arrayListOf<Track>()
        tracksSet.stream().forEach {
            tracks.add(it)
        }

        return tracks
    }

    private fun findAudioFiles(file: File, destination: ArrayList<String>, excludedPaths: MutableList<String>) {
        if (file.isHidden) {
            return
        }

        val path = file.absolutePath
        if (path in excludedPaths || path.getParentPath() in excludedPaths) {
            return
        }

        if (file.isFile) {
            if (path.isAudioFast()) {
                destination.add(path)
            }
        } else if (!file.containsNoMedia()) {
            file.listFiles().orEmpty().forEach { child ->
                findAudioFiles(child, destination, excludedPaths)
            }
        }
    }

    private fun splitIntoArtists(tracks: ArrayList<Track>): ArrayList<Artist> {
        val artists = arrayListOf<Artist>()
        val artistsGroupedByTracks = tracks.groupBy { it.artist }
        for ((artistName, tracksByArtist) in artistsGroupedByTracks) {
            val trackCnt = tracksByArtist.size
            if (trackCnt > 0) {
                val albumCnt = tracksByArtist.groupBy { it.album }.size
                val artist = Artist(0, artistName, albumCnt, trackCnt, 0)
                val artistId = artist.hashCode().toLong()
                artist.id = artistId
                tracksByArtist.onEach { it.artistId = artistId }
                artists.add(artist)
            }
        }

        return artists
    }

    private fun splitIntoAlbums(tracks: ArrayList<Track>): ArrayList<Album> {
        val albums = arrayListOf<Album>()
        val tracksGroupedByAlbums = tracks.groupBy { it.album }
        for ((albumName, tracksInAlbum) in tracksGroupedByAlbums) {
            val trackCnt = tracksInAlbum.size
            if (trackCnt > 0) {
                val track = tracksInAlbum.first()
                val artistName = track.artist
                val year = track.year
                val album = Album(0, artistName, albumName, "", year, trackCnt, track.artistId)
                val albumId = album.hashCode().toLong()
                album.id = albumId
                tracksInAlbum.onEach { it.albumId = albumId }
                albums.add(album)
            }
        }

        return albums
    }

    private fun performCleanup() {
        // remove invalid tracks
        val newTrackIds = newTracks.map { it.mediaStoreId } as ArrayList<Long>
        val newTrackPaths = newTracks.map { it.path } as ArrayList<String>
        val invalidTracks = context.tracksDAO.getAll().filter { it.mediaStoreId !in newTrackIds || it.path !in newTrackPaths }
        context.tracksDAO.removeTracks(invalidTracks)
        newTracks.removeAll(invalidTracks.toSet())

        // remove invalid albums
        val newAlbumIds = newAlbums.map { it.id }
        val invalidAlbums = context.albumsDAO.getAll().filter { it.id !in newAlbumIds }.toMutableList()
        for (album in newAlbums) {
            val albumId = album.id
            val albumTracks = newTracks.filter { it.albumId == albumId }
            if (albumTracks.isEmpty()) {
                invalidAlbums.add(album)
            }
        }
        context.albumsDAO.deleteAll(invalidAlbums)
        newAlbums.removeAll(invalidAlbums.toSet())

        // remove invalid artists
        val newArtistIds = newArtists.map { it.id }
        val invalidArtists = context.artistDAO.getAll().filter { it.id !in newArtistIds } as ArrayList<Artist>
        for (artist in newArtists) {
            val artistId = artist.id
            val albumsByArtist = newAlbums.filter { it.artistId == artistId }
            if (albumsByArtist.isEmpty()) {
                invalidArtists.add(artist)
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

        context.artistDAO.deleteAll(invalidArtists)
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
