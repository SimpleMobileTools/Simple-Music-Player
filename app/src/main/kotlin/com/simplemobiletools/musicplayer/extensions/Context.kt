package com.simplemobiletools.musicplayer.extensions

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import android.util.TypedValue
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.PlaylistsDao
import com.simplemobiletools.musicplayer.interfaces.QueueItemsDao
import com.simplemobiletools.musicplayer.interfaces.SongsDao
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

fun Context.playlistChanged(newID: Int, callSetup: Boolean = true) {
    config.currentPlaylist = newID
    sendIntent(PAUSE)
    Intent(this, MusicService::class.java).apply {
        putExtra(CALL_SETUP_AFTER, callSetup)
        action = REFRESH_LIST
        startService(this)
    }
}

fun Context.getActionBarHeight(): Int {
    val textSizeAttr = intArrayOf(R.attr.actionBarSize)
    val attrs = obtainStyledAttributes(TypedValue().data, textSizeAttr)
    val actionBarSize = attrs.getDimensionPixelSize(0, -1)
    attrs.recycle()
    return actionBarSize
}

fun Context.getTracksDB() = SongsDatabase.getInstance(this)

fun Context.getPlaylistIdWithTitle(title: String) = playlistDAO.getPlaylistWithTitle(title)?.id ?: -1

fun Context.getPlaylistTracks(playlistId: Int): ArrayList<Track> {
    val validTracks = ArrayList<Track>()
    if (isQPlus()) {
        validTracks.addAll(tracksDAO.getTracksFromPlaylist(playlistId))
    } else {
        val invalidTracks = ArrayList<Track>()
        val tracks = tracksDAO.getTracksFromPlaylist(playlistId)
        val showFilename = config.showFilename
        tracks.forEach {
            it.title = it.getProperTitle(showFilename)

            if (File(it.path).exists() || it.path.startsWith("content://")) {
                validTracks.add(it)
            } else {
                invalidTracks.add(it)
            }
        }

        getTracksDB().runInTransaction {
            invalidTracks.forEach {
                tracksDAO.removeSongPath(it.path)
            }
        }
    }

    return validTracks
}

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

fun Context.getArtists(callback: (artists: ArrayList<Artist>) -> Unit) {
    ensureBackgroundThread {
        val artists = getArtistsSync()
        callback(artists)
    }
}

fun Context.getArtistsSync(): ArrayList<Artist> {
    val artists = ArrayList<Artist>()
    val uri = Audio.Artists.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        Audio.Artists._ID,
        Audio.Artists.ARTIST
    )

    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getIntValue(Audio.Artists._ID)
                    val title = cursor.getStringValue(Audio.Artists.ARTIST)
                    var artist = Artist(id, title, 0, 0, 0)
                    artist = fillArtistExtras(this, artist)
                    if (artist.albumCnt > 0) {
                        artists.add(artist)
                    }
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    artists.sortWith { o1, o2 -> AlphanumericComparator().compare(o1.title.toLowerCase(), o2.title.toLowerCase()) }

    // move <unknown> at the bottom
    val unknown = artists.firstOrNull { it.title == MediaStore.UNKNOWN_STRING }
    if (unknown != null) {
        if (artists.remove(unknown)) {
            artists.add(unknown)
        }
    }

    return artists
}

private fun fillArtistExtras(context: Context, artist: Artist): Artist {
    val uri = Audio.Albums.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        Audio.Albums._ID)

    val selection = "${Audio.Albums.ARTIST_ID} = ?"
    val selectionArgs = arrayOf(artist.id.toString())

    artist.albumCnt = context.getAlbumsSync(artist).size

    try {
        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    val albumId = cursor.getLongValue(Audio.Albums._ID)
                    if (artist.albumArtId == 0L) {
                        artist.albumArtId = albumId
                    }

                    artist.trackCnt += context.getAlbumTracksSync(albumId.toInt()).size
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
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
        Audio.Albums.ALBUM)

    var selection = "${Audio.Albums.ARTIST} = ?"
    var selectionArgs = arrayOf(artist.title)

    if (isQPlus()) {
        selection = "${Audio.Albums.ARTIST_ID} = ?"
        selectionArgs = arrayOf(artist.id.toString())
    }

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getIntValue(Audio.Albums._ID)
                    val artistName = cursor.getStringValue(Audio.Albums.ARTIST)
                    val title = cursor.getStringValue(Audio.Albums.ALBUM)
                    val coverArt = ContentUris.withAppendedId(artworkUri, id.toLong()).toString()
                    val year = cursor.getIntValue(Audio.Albums.FIRST_YEAR)
                    val album = Album(id, artistName, title, coverArt, year)
                    albums.add(album)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    albums.sortWith { o1, o2 -> AlphanumericComparator().compare(o1.title.toLowerCase(), o2.title.toLowerCase()) }
    return albums
}

fun Context.getAlbumTracks(albumId: Int, callback: (tracks: ArrayList<Track>) -> Unit) {
    ensureBackgroundThread {
        val tracks = getAlbumTracksSync(albumId)
        callback(tracks)
    }
}

fun Context.getAlbumTracksSync(albumId: Int): ArrayList<Track> {
    val tracks = ArrayList<Track>()
    val uri = Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        Audio.Media._ID,
        Audio.Media.DURATION,
        Audio.Media.TITLE,
        Audio.Media.ARTIST,
        Audio.Media.ALBUM,
        Audio.Media.TRACK
    )

    val selection = "${Audio.Albums.ALBUM_ID} = ?"
    val selectionArgs = arrayOf(albumId.toString())
    val coverUri = ContentUris.withAppendedId(artworkUri, albumId.toLong())
    var coverArt = ""

    // check if the album art file exists at all
    try {
        val cursor = contentResolver.query(coverUri, null, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                coverArt = coverUri.toString()
            }
        }
    } catch (e: Exception) {
    }

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getLongValue(Audio.Media._ID)
                    val title = cursor.getStringValue(Audio.Media.TITLE)
                    val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
                    val trackId = cursor.getIntValue(Audio.Media.TRACK) % 1000
                    val path = ""
                    val artist = cursor.getStringValue(Audio.Media.ARTIST)
                    val album = cursor.getStringValue(Audio.Media.ALBUM)
                    val track = Track(id, title, artist, path, duration, album, coverArt, 0, trackId)
                    tracks.add(track)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return tracks
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
            val queueItem = QueueItem(it.id, order++, false, 0)
            itemsToInsert.add(queueItem)
        }

        tracksDAO.insertAll(newTracks)
        queueDAO.insertAll(itemsToInsert)
        callback()
    }
}

fun Context.removeQueueItems(tracks: List<Track>, callback: () -> Unit) {
    ensureBackgroundThread {
        tracks.forEach {
            queueDAO.removeQueueItem(it.id)
            MusicService.mTracks.remove(it)
            callback()
        }
    }
}
