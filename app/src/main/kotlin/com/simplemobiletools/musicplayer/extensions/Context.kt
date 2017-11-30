package com.simplemobiletools.musicplayer.extensions

import android.content.Context
import android.content.Intent
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.services.MusicService

fun Context.sendIntent(action: String) {
    Intent(this, MusicService::class.java).apply {
        this.action = action
        startService(this)
    }
}

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.dbHelper: DBHelper get() = DBHelper.newInstance(applicationContext)

fun Context.playlistChanged(newID: Int) {
    config.currentPlaylist = newID
    sendIntent(PAUSE)
    sendIntent(REFRESH_LIST)
    sendIntent(SETUP)
}
