package com.simplemobiletools.musicplayer.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.MediaStore.Audio
import android.util.TypedValue
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.PlaylistsDao
import com.simplemobiletools.musicplayer.interfaces.SongsDao
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Song
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

val Context.playlistDAO: PlaylistsDao get() = getSongsDB().PlaylistsDao()

val Context.songsDAO: SongsDao get() = getSongsDB().SongsDao()

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

fun Context.getSongsDB() = SongsDatabase.getInstance(this)

fun Context.getPlaylistIdWithTitle(title: String) = playlistDAO.getPlaylistWithTitle(title)?.id ?: -1

fun Context.getPlaylistSongs(playlistId: Int): ArrayList<Song> {
    val validSongs = ArrayList<Song>()
    if (isQPlus()) {
        validSongs.addAll(songsDAO.getSongsFromPlaylist(playlistId))
    } else {
        val invalidSongs = ArrayList<Song>()
        val songs = songsDAO.getSongsFromPlaylist(playlistId)
        val showFilename = config.showFilename
        songs.forEach {
            it.title = it.getProperTitle(showFilename)

            if (File(it.path).exists() || it.path.startsWith("content://")) {
                validSongs.add(it)
            } else {
                invalidSongs.add(it)
            }
        }

        getSongsDB().runInTransaction {
            invalidSongs.forEach {
                songsDAO.removeSongPath(it.path)
            }
        }
    }

    return validSongs
}

fun Context.deletePlaylists(playlists: ArrayList<Playlist>) {
    playlistDAO.deletePlaylists(playlists)
    playlists.forEach {
        songsDAO.removePlaylistSongs(it.id)
    }
}

fun Context.broadcastUpdateWidgetSong(newSong: Song?) {
    Intent(this, MyWidgetProvider::class.java).apply {
        putExtra(NEW_SONG, newSong)
        action = SONG_CHANGED
        sendBroadcast(this)
    }
}

fun Context.broadcastUpdateWidgetSongState(isPlaying: Boolean) {
    Intent(this, MyWidgetProvider::class.java).apply {
        putExtra(IS_PLAYING, isPlaying)
        action = SONG_STATE_CHANGED
        sendBroadcast(this)
    }
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
                    val album = Album(id, artistName, title)
                    albums.add(album)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return albums
}

fun Context.getSongs(albumId: Int, callback: (songs: ArrayList<Song>) -> Unit) {
    ensureBackgroundThread {
        val songs = getSongsSync(albumId)
        callback(songs)
    }
}

fun Context.getSongsSync(albumId: Int): ArrayList<Song> {
    val songs = ArrayList<Song>()
    val uri = Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        Audio.Media._ID,
        Audio.Media.DURATION,
        Audio.Media.TITLE,
        Audio.Media.TRACK
    )

    val selection = "${Audio.Albums.ALBUM_ID} = ?"
    val selectionArgs = arrayOf(albumId.toString())

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
                    val artist = ""
                    val song = Song(id, title, artist, path, duration, "", 0, trackId)
                    songs.add(song)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return songs
}
