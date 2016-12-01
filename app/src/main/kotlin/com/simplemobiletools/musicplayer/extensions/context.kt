package com.simplemobiletools.musicplayer.extensions

import android.content.Context
import android.content.Intent
import com.simplemobiletools.musicplayer.Constants
import com.simplemobiletools.musicplayer.MusicService

fun Context.sendIntent(action: String) {
    Intent(this, MusicService::class.java).apply {
        this.action = action
        startService(this)
    }
}

fun Context.getSharedPrefs() = getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE)
