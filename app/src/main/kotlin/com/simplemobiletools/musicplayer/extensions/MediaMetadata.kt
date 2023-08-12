package com.simplemobiletools.musicplayer.extensions

import androidx.media3.common.MediaMetadata
import com.simplemobiletools.musicplayer.helpers.MEDIA_ITEM_DURATION

val MediaMetadata.durationInSeconds: Int
    get() = extras?.getInt(MEDIA_ITEM_DURATION) ?: 0
