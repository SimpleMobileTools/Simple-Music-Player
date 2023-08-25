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

val Player.currentMediaItemsShuffled: List<MediaItem>
    get() = if (shuffleModeEnabled) {
        shuffledMediaItemsIndices.map { getMediaItemAt(it) }
    } else {
        currentMediaItems
    }

val Player.shuffledMediaItemsIndices: List<Int>
    get() {
        val indices = mutableListOf<Int>()
        var index = currentTimeline.getFirstWindowIndex(shuffleModeEnabled)
        if (index == -1) {
            return emptyList()
        }

        repeat(currentTimeline.windowCount) {
            indices += index
            index = currentTimeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
        }

        return indices
    }
