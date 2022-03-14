package com.simplemobiletools.musicplayer.extensions

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.*
import com.simplemobiletools.musicplayer.models.*
import com.simplemobiletools.musicplayer.services.MusicService
import java.io.File

@SuppressLint("NewApi")
fun Context.sendIntent(action: String) {
    Intent(this, MusicService::class.java).apply {
        this.action = action
        try {
            if (isOreoPlus()) {
                startForegroundService(this)
            } else {
                startService(this)
            }
        } catch (ignored: Exception) {
        }
    }
}

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.playlistDAO: PlaylistsDao get() = getTracksDB().PlaylistsDao()

val Context.tracksDAO: SongsDao get() = getTracksDB().SongsDao()

val Context.queueDAO: QueueItemsDao get() = getTracksDB().QueueItemsDao()

val Context.artistDAO: ArtistsDao get() = getTracksDB().ArtistsDao()

val Context.albumsDAO: AlbumsDao get() = getTracksDB().AlbumsDao()

fun Context.getTracksDB() = SongsDatabase.getInstance(this)

fun Context.getPlaylistIdWithTitle(title: String) = playlistDAO.getPlaylistWithTitle(title)?.id ?: -1

fun Context.deletePlaylists(playlists: ArrayList<Playlist>) {
    playlistDAO.deletePlaylists(playlists)
    playlists.forEach {
        tracksDAO.removePlaylistSongs(it.id)
    }
}

fun Context.broadcastUpdateWidgetTrack(newSong: Track?) {
    Intent(this, MyWidgetProvider::class.java).apply {
        putExtra(NEW_TRACK, newSong)
        action = TRACK_CHANGED
        sendBroadcast(this)
    }
}

fun Context.broadcastUpdateWidgetTrackState(isPlaying: Boolean) {
    Intent(this, MyWidgetProvider::class.java).apply {
        putExtra(IS_PLAYING, isPlaying)
        action = TRACK_STATE_CHANGED
        sendBroadcast(this)
    }
}

fun Context.getArtistsSync(): ArrayList<Artist> {
    val artists = ArrayList<Artist>()
    val uri = Audio.Artists.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        Audio.Artists._ID,
        Audio.Artists.ARTIST
    )

    queryCursor(uri, projection, showErrors = true) { cursor ->
        val id = cursor.getLongValue(Audio.Artists._ID)
        val title = cursor.getStringValue(Audio.Artists.ARTIST) ?: MediaStore.UNKNOWN_STRING
        var artist = Artist(id, title, 0, 0, 0)
        artist = fillArtistExtras(this, artist)
        if (artist.albumCnt > 0) {
            artists.add(artist)
        }
    }

    return artists
}

private fun fillArtistExtras(context: Context, artist: Artist): Artist {
    val uri = Audio.Albums.EXTERNAL_CONTENT_URI
    val projection = arrayOf(Audio.Albums._ID)
    val selection = "${Audio.Albums.ARTIST_ID} = ?"
    val selectionArgs = arrayOf(artist.id.toString())

    artist.albumCnt = context.getAlbumsCount(artist)

    context.queryCursor(uri, projection, selection, selectionArgs) { cursor ->
        val albumId = cursor.getLongValue(Audio.Albums._ID)
        if (artist.albumArtId == 0L) {
            artist.albumArtId = albumId
        }

        artist.trackCnt += context.getAlbumTracksCount(albumId)
    }

    return artist
}

fun Context.getAlbums(artist: Artist, callback: (artists: ArrayList<Album>) -> Unit) {
    ensureBackgroundThread {
        val albums = getAlbumsSync(artist)
        callback(albums)
    }
}

fun Context.getAlbumsSync(artist: Artist): ArrayList<Album> {
    val albums = ArrayList<Album>()
    val uri = Audio.Albums.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        Audio.Albums._ID,
        Audio.Albums.ARTIST,
        Audio.Albums.FIRST_YEAR,
        Audio.Albums.ALBUM
    )

    var selection = "${Audio.Albums.ARTIST} = ?"
    var selectionArgs = arrayOf(artist.title)

    if (isQPlus()) {
        selection = "${Audio.Albums.ARTIST_ID} = ?"
        selectionArgs = arrayOf(artist.id.toString())
    }

    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val id = cursor.getLongValue(Audio.Albums._ID)
        val artistName = cursor.getStringValue(Audio.Albums.ARTIST) ?: MediaStore.UNKNOWN_STRING
        val title = cursor.getStringValue(Audio.Albums.ALBUM)
        val coverArt = ContentUris.withAppendedId(artworkUri, id).toString()
        val year = cursor.getIntValue(Audio.Albums.FIRST_YEAR)
        val trackCnt = getAlbumTracksCount(id)
        val album = Album(id, artistName, title, coverArt, year, trackCnt, artist.id)
        albums.add(album)
    }

    return albums
}

fun Context.getAlbumsCount(artist: Artist): Int {
    val uri = Audio.Albums.EXTERNAL_CONTENT_URI
    val projection = arrayOf(Audio.Albums._ID)
    var selection = "${Audio.Albums.ARTIST} = ?"
    var selectionArgs = arrayOf(artist.title)

    if (isQPlus()) {
        selection = "${Audio.Albums.ARTIST_ID} = ?"
        selectionArgs = arrayOf(artist.id.toString())
    }

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            return cursor.count
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return 0
}

fun Context.getAlbumTracksSync(albumId: Long): ArrayList<Track> {
    val tracks = ArrayList<Track>()
    val uri = Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayListOf(
        Audio.Media._ID,
        Audio.Media.DURATION,
        Audio.Media.DATA,
        Audio.Media.TITLE,
        Audio.Media.ARTIST,
        Audio.Media.ALBUM,
        Audio.Media.TRACK
    )

    if (isQPlus()) {
        projection.add(Audio.Media.BUCKET_DISPLAY_NAME)
    }

    val selection = "${Audio.Albums.ALBUM_ID} = ?"
    val selectionArgs = arrayOf(albumId.toString())
    val coverUri = ContentUris.withAppendedId(artworkUri, albumId)
    val coverArt = coverUri.toString()
    val showFilename = config.showFilename

    queryCursor(uri, projection.toTypedArray(), selection, selectionArgs, showErrors = true) { cursor ->
        val id = cursor.getLongValue(Audio.Media._ID)
        val title = cursor.getStringValue(Audio.Media.TITLE)
        val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
        val trackId = cursor.getIntValue(Audio.Media.TRACK) % 1000
        val path = cursor.getStringValue(Audio.Media.DATA)
        val artist = cursor.getStringValue(Audio.Media.ARTIST) ?: MediaStore.UNKNOWN_STRING
        val album = cursor.getStringValue(Audio.Media.ALBUM)
        val folderName = if (isQPlus()) {
            cursor.getStringValue(Audio.Media.BUCKET_DISPLAY_NAME) ?: MediaStore.UNKNOWN_STRING
        } else {
            ""
        }

        val track = Track(0, id, title, artist, path, duration, album, coverArt, 0, trackId, folderName, albumId)
        track.title = track.getProperTitle(showFilename)
        tracks.add(track)
    }

    return tracks
}

fun Context.getAlbumTracksCount(albumId: Long): Int {
    val uri = Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(Audio.Media._ID)
    val selection = "${Audio.Albums.ALBUM_ID} = ?"
    val selectionArgs = arrayOf(albumId.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            return cursor.count
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return 0
}

fun Context.resetQueueItems(newTracks: List<Track>, callback: () -> Unit) {
    ensureBackgroundThread {
        queueDAO.deleteAllItems()
        addQueueItems(newTracks, callback)
    }
}

fun Context.addQueueItems(newTracks: List<Track>, callback: () -> Unit) {
    ensureBackgroundThread {
        val itemsToInsert = ArrayList<QueueItem>()
        var order = 0
        newTracks.forEach {
            val queueItem = QueueItem(it.mediaStoreId, order++, false, 0)
            itemsToInsert.add(queueItem)
        }

        tracksDAO.insertAll(newTracks)
        queueDAO.insertAll(itemsToInsert)
        sendIntent(UPDATE_QUEUE_SIZE)
        callback()
    }
}

fun Context.removeQueueItems(tracks: List<Track>, callback: () -> Unit) {
    ensureBackgroundThread {
        tracks.forEach {
            queueDAO.removeQueueItem(it.mediaStoreId)
            MusicService.mTracks.remove(it)
        }
        callback()
    }
}

// store new artists, albums and tracks into our local db, delete invalid items
fun Context.updateAllDatabases(callback: () -> Unit) {
    ensureBackgroundThread {
        updateCachedArtists { artists ->
            updateCachedAlbums(artists)
            callback()
        }
    }
}

fun Context.updateCachedArtists(callback: (artists: ArrayList<Artist>) -> Unit) {
    val artists = getArtistsSync()
    artists.forEach { artist ->
        artistDAO.insert(artist)
    }

    // remove invalid artists from cache
    val cachedArtists = artistDAO.getAll() as ArrayList<Artist>
    val newIds = artists.map { it.id }
    val idsToRemove = arrayListOf<Long>()
    cachedArtists.forEach { artist ->
        if (!newIds.contains(artist.id)) {
            idsToRemove.add(artist.id)
        }
    }

    idsToRemove.forEach {
        artistDAO.deleteArtist(it)
    }

    callback(artists)
}

fun Context.updateCachedAlbums(artists: ArrayList<Artist>) {
    val albums = ArrayList<Album>()
    artists.forEach { artist ->
        albums.addAll(getAlbumsSync(artist))
    }

    albums.forEach { album ->
        albumsDAO.insert(album)
    }

    // remove invalid albums from cache
    val cachedAlbums = albumsDAO.getAll() as ArrayList<Album>
    val newIds = albums.map { it.id }
    val idsToRemove = arrayListOf<Long>()
    cachedAlbums.forEach { album ->
        if (!newIds.contains(album.id)) {
            idsToRemove.add(album.id)
        }
    }

    idsToRemove.forEach {
        albumsDAO.deleteAlbum(it)
    }

    updateCachedTracks(albums)
}

fun Context.updateCachedTracks(albums: ArrayList<Album>) {
    val tracks = ArrayList<Track>()
    albums.forEach { album ->
        tracks.addAll(getAlbumTracksSync(album.id))
    }

    tracks.forEach { track ->
        tracksDAO.insert(track)
    }

    // remove invalid tracks from cache
    val cachedTracks = tracksDAO.getAll() as ArrayList<Track>
    val newIds = tracks.map { it.mediaStoreId }
    val idsToRemove = arrayListOf<Long>()
    cachedTracks.forEach { track ->
        if (!newIds.contains(track.mediaStoreId)) {
            idsToRemove.add(track.mediaStoreId)
        }
    }

    idsToRemove.forEach {
        tracksDAO.removeTrack(it)
    }

    if (!config.wasAllTracksPlaylistCreated) {
        val allTracksLabel = resources.getString(R.string.all_tracks)
        val playlist = Playlist(ALL_TRACKS_PLAYLIST_ID, allTracksLabel)
        playlistDAO.insert(playlist)
        tracks.forEach {
            it.playListId = ALL_TRACKS_PLAYLIST_ID
        }
        RoomHelper(this).insertTracksWithPlaylist(tracks)
        config.wasAllTracksPlaylistCreated = true
    }
}

fun Context.getMediaStoreIdFromPath(path: String): Long {
    var id = 0L
    val projection = arrayOf(
        Audio.Media._ID
    )

    val uri = getFileUri(path)
    val selection = "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs = arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                id = cursor.getLongValue(Audio.Media._ID)
            }
        }
    } catch (ignored: Exception) {
    }

    return id
}

fun Context.getFolderTracks(path: String, rescanWrongPaths: Boolean, callback: (tracks: ArrayList<Track>) -> Unit) {
    val folderTracks = getFolderTrackPaths(File(path))
    val allTracks = tracksDAO.getAll()
    val wantedTracks = ArrayList<Track>()
    val wrongPaths = ArrayList<String>()    // rescan paths that are not present in the MediaStore

    folderTracks.forEach { trackPath ->
        var trackAdded = false
        val mediaStoreId = getMediaStoreIdFromPath(trackPath)
        if (mediaStoreId != 0L) {
            allTracks.firstOrNull { it.mediaStoreId == mediaStoreId }?.apply {
                id = 0
                wantedTracks.add(this)
                trackAdded = true
            }
        }

        if (!trackAdded) {
            val track = RoomHelper(this).getTrackFromPath(trackPath)
            if (track != null && track.mediaStoreId != 0L) {
                wantedTracks.add(track)
            } else {
                wrongPaths.add(trackPath)
            }
        }
    }

    if (wrongPaths.isEmpty() || !rescanWrongPaths) {
        callback(wantedTracks)
    } else {
        rescanPaths(wrongPaths) {
            getFolderTracks(path, false) { tracks ->
                callback(tracks)
            }
        }
    }
}

private fun getFolderTrackPaths(folder: File): ArrayList<String> {
    val trackFiles = ArrayList<String>()
    val files = folder.listFiles() ?: return trackFiles
    files.forEach {
        if (it.isDirectory) {
            trackFiles.addAll(getFolderTrackPaths(it))
        } else if (it.isAudioFast()) {
            trackFiles.add(it.absolutePath)
        }
    }
    return trackFiles
}
