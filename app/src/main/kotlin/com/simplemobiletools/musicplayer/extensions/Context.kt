package com.simplemobiletools.musicplayer.extensions

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import java.io.File

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

fun Context.playlistChanged(newID: Int) {
    config.currentPlaylist = newID
    sendIntent(PAUSE)
    sendIntent(REFRESH_LIST)
    sendIntent(SETUP)
}

fun Context.getAlbumImage(song: Song?): Bitmap {
    if (File(song?.path ?: "").exists()) {
        try {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(song!!.path)
            val rawArt = mediaMetadataRetriever.embeddedPicture
            if (rawArt != null) {
                val options = BitmapFactory.Options()
                val bitmap = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
                if (bitmap != null) {
                    return bitmap
                }
            }
        } catch (e: Exception) {
        }
    }
    return BitmapFactory.decodeResource(resources, R.drawable.ic_headset)
}
