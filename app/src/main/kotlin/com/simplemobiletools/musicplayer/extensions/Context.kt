package com.simplemobiletools.musicplayer.extensions

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.PlaylistsDao
import com.simplemobiletools.musicplayer.interfaces.SongsDao
import com.simplemobiletools.musicplayer.services.MusicService

fun Context.sendIntent(action: String) {
    Intent(this, MusicService::class.java).apply {
        this.action = action
        try {
            startService(this)
        } catch (ignored: Exception) {
        }
    }
}

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.dbHelper: DBHelper get() = DBHelper.newInstance(applicationContext)

val Context.songsDB: SongsDatabase get() = SongsDatabase.getInstance(applicationContext)

val Context.playlistDAO: PlaylistsDao get() = songsDB.PlaylistsDao()

val Context.songsDAO: SongsDao get() = songsDB.SongsDao()

fun Context.playlistChanged(newID: Int, callSetup: Boolean = true) {
    config.currentPlaylist = newID
    sendIntent(PAUSE)
    sendIntent(REFRESH_LIST)
    if (callSetup) {
        sendIntent(SETUP)
    }
}

fun Context.getActionBarHeight(): Int {
    val textSizeAttr = intArrayOf(R.attr.actionBarSize)
    val attrs = obtainStyledAttributes(TypedValue().data, textSizeAttr)
    val actionBarSize = attrs.getDimensionPixelSize(0, -1)
    attrs.recycle()
    return actionBarSize
}

fun Context.getPlaylistIdWithTitle(title: String) = playlistDAO.getPlaylistWithTitle(title)?.id ?: -1
