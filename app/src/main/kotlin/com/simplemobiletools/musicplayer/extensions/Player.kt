package com.simplemobiletools.musicplayer.extensions

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.helpers.PlaybackSetting
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.models.toMediaItemsFast

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

inline fun <T : Player> T.runOnPlayerThread(crossinline callback: T.() -> Unit) =
    applicationLooper.post {
        callback()
    }

fun Player.togglePlayback() {
    if (isReallyPlaying) {
        pause()
    } else {
        play()
    }
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

fun Player.prepareUsingTracks(
    tracks: List<Track>,
    startIndex: Int = 0,
    startPositionMs: Long = 0,
    play: Boolean = false,
    callback: ((success: Boolean) -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        runOnPlayerThread {
            callback?.invoke(false)
        }
        return
    }

    val mediaItems = tracks.toMediaItemsFast()
    runOnPlayerThread {
        setMediaItems(mediaItems, startIndex, startPositionMs)
        playWhenReady = play
        prepare()
        callback?.invoke(true)
    }
}

/**
 * This method prepares the player using queued tracks or in case the queue is empty, initializing
 * the queue using all tracks. To optimize this, the player is first prepared using the current track and then all queued
 * items are added using [addRemainingMediaItems]. This helps prevent delays, especially with large queues, and
 * avoids potential issues like [android.app.ForegroundServiceStartNotAllowedException] when starting from background.
 */
var prepareInProgress = false
inline fun Player.maybePreparePlayer(context: Context, crossinline callback: (success: Boolean) -> Unit) {
    if (!prepareInProgress && currentMediaItem == null) {
        prepareInProgress = true
        ensureBackgroundThread {
            var prepared = false
            context.audioHelper.getQueuedTracksLazily { tracks, startIndex, startPositionMs ->
                if (!prepared) {
                    prepareUsingTracks(tracks = tracks, startIndex = startIndex, startPositionMs = startPositionMs) {
                        callback(it)
                        prepared = it
                    }
                } else {
                    if (tracks.size == 1) {
                        return@getQueuedTracksLazily
                    }

                    addRemainingMediaItems(tracks.toMediaItemsFast(), startIndex)
                }
            }
        }
    } else {
        callback(false)
    }
}

/**
 * This method takes a list of media items and the current index in the playlist. It then
 * adds the media items that come before and after the current index to the player's playlist.
 */
fun Player.addRemainingMediaItems(mediaItems: List<MediaItem>, currentIndex: Int) {
    val itemsAtStart = mediaItems.take(currentIndex)
    val itemsAtEnd = mediaItems.takeLast(mediaItems.lastIndex - currentIndex)
    runOnPlayerThread {
        addMediaItems(0, itemsAtStart)
        addMediaItems(currentIndex + 1, itemsAtEnd)
    }
}
