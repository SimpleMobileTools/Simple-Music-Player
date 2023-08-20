package com.simplemobiletools.musicplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

val Player.isReallyPlaying: Boolean
    get() = when (playbackState) {
        Player.STATE_ENDED, Player.STATE_IDLE -> false
        else -> isPlaying || playWhenReady
    }

val Player.currentMediaItems: List<MediaItem>
    get() = (0 until (mediaItemCount)).map { getMediaItemAt(it) }

val Player.nextMediaItem: MediaItem?
    get() = if (hasNextMediaItem()) {
        getMediaItemAt(nextMediaItemIndex)
    } else {
        null
    }
