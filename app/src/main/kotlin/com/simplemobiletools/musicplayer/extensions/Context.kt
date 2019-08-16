package com.simplemobiletools.musicplayer.extensions

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.helpers.CALL_SETUP_AFTER
import com.simplemobiletools.musicplayer.helpers.Config
import com.simplemobiletools.musicplayer.helpers.PAUSE
import com.simplemobiletools.musicplayer.helpers.REFRESH_LIST
import com.simplemobiletools.musicplayer.interfaces.PlaylistsDao
import com.simplemobiletools.musicplayer.interfaces.SongsDao
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
    val invalidSongs = ArrayList<Song>()
    val songs = songsDAO.getSongsFromPlaylist(playlistId)
    val showFilename = config.showFilename

    songs.forEach {
        it.title = it.getProperTitle(showFilename)

        if (File(it.path).exists()) {
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

    return validSongs
}

fun Context.deletePlaylists(playlists: ArrayList<Playlist>) {
    playlistDAO.deletePlaylists(playlists)
    playlists.forEach {
        songsDAO.removePlaylistSongs(it.id)
    }
}
