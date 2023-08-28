package com.simplemobiletools.musicplayer.extensions

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.simplemobiletools.musicplayer.helpers.PlaybackSetting

val Player.isReallyPlaying: Boolean
    get() = when (playbackState) {
        Player.STATE_ENDED, Player.STATE_IDLE -> false
        else -> isPlaying || playWhenReady
    }

val Player.currentMediaItems: List<MediaItem>
    get() = (0 until (mediaItemCount)).map { getMediaItemAt(it) }

val Player.nextMediaItem: MediaItem?
    get() = when {
        hasNextMediaItem() -> getMediaItemAt(nextMediaItemIndex)
        currentMediaItemIndex == lastMediaItemIndex -> getMediaItemAt(firstMediaItemIndex)
        else -> null
    }

val Player.firstMediaItemIndex: Int
    get() = if (shuffleModeEnabled) {
        shuffledMediaItemsIndices.firstOrNull() ?: -1
    } else {
        0
    }

val Player.lastMediaItemIndex: Int
    get() = if (shuffleModeEnabled) {
        shuffledMediaItemsIndices.lastOrNull() ?: -1
    } else {
        mediaItemCount - 1
    }

val Player.isAtStartOfPlaylist: Boolean
    get() = currentMediaItemIndex == firstMediaItemIndex

val Player.isAtEndOfPlaylist: Boolean
    get() {
        if (currentMediaItemIndex == C.INDEX_UNSET) {
            return false
        }

        return currentMediaItemIndex == lastMediaItemIndex
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

fun Player.setRepeatMode(playbackSetting: PlaybackSetting) {
    repeatMode = when (playbackSetting) {
        PlaybackSetting.REPEAT_TRACK -> Player.REPEAT_MODE_ONE
        PlaybackSetting.REPEAT_PLAYLIST -> Player.REPEAT_MODE_ALL
        PlaybackSetting.REPEAT_OFF -> Player.REPEAT_MODE_OFF
        else -> {
            // PlaybackSetting.STOP_AFTER_CURRENT_TRACK is handled manually.
            Player.REPEAT_MODE_ONE
        }
    }
}

fun Player.forceSeekToNext() {
    if (!maybeForceNext()) {
        seekToNext()
    }
}

fun Player.forceSeekToPrevious() {
    if (!maybeForcePrevious()) {
        seekToPrevious()
    }
}

/**
 * Force seek to the next media item regardless of the current [Player.RepeatMode]. Returns true on success.
 */
fun Player.maybeForceNext(): Boolean {
    return if (isAtEndOfPlaylist) {
        seekTo(firstMediaItemIndex, 0)
        true
    } else {
        false
    }
}

/**
 * Force seek to the previous media item regardless of the current [Player.RepeatMode]. Returns true on success.
 */
fun Player.maybeForcePrevious(): Boolean {
    return if (isAtStartOfPlaylist && currentMediaItem != null) {
        seekTo(lastMediaItemIndex, 0)
        true
    } else {
        false
    }
}

/**
 * This function takes a list of media items and the current index in the playlist. It then
 * adds the media items that come before and after the current index to the player's playlist.
 */
fun Player.addRemainingMediaItems(mediaItems: List<MediaItem>, currentIndex: Int) {
    val itemsAtStart = mediaItems.take(currentIndex)
    val itemsAtEnd = mediaItems.takeLast(mediaItems.lastIndex - currentIndex)
    applicationLooper.post {
        addMediaItems(0, itemsAtStart)
        addMediaItems(currentIndex + 1, itemsAtEnd)
    }
}
