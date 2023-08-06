package com.simplemobiletools.musicplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

val Player.currentMediaItems: List<MediaItem>
    get() = (0 until (mediaItemCount)).map { getMediaItemAt(it) }
