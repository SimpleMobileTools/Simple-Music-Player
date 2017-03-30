package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.NEXT
import com.simplemobiletools.musicplayer.helpers.PAUSE
import com.simplemobiletools.musicplayer.helpers.REFRESH_LIST

open class SimpleActivity : BaseSimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun playlistChanged(newID: Int) {
        config.currentPlaylist = newID
        sendIntent(PAUSE)
        sendIntent(REFRESH_LIST)
        sendIntent(NEXT)
    }
}
