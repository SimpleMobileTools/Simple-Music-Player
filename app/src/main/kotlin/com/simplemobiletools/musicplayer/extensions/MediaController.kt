package com.simplemobiletools.musicplayer.extensions

import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaController
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.models.toMediaItems
import com.simplemobiletools.musicplayer.playback.CustomCommands
import com.simplemobiletools.musicplayer.playback.PlaybackService.Companion.updatePlaybackInfo
import kotlin.time.Duration.Companion.seconds

fun MediaController.sendCommand(command: CustomCommands, extras: Bundle = Bundle.EMPTY) = sendCustomCommand(command.sessionCommand, extras)

fun MediaController.togglePlayback() {
    if (isReallyPlaying) {
        pause()
    } else {
        play()
    }
}

fun MediaController.runOnPlayerThread(callback: MediaController.() -> Unit) =
    applicationLooper.post {
        callback(this)
    }

fun MediaController.prepareUsingTracks(
    tracks: List<Track>,
    startIndex: Int = 0,
    startPosition: Long = 0,
    play: Boolean = false,
    callback: ((success: Boolean) -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        callback?.invoke(false)
        return
    }

    val mediaItems = tracks.toMediaItems()
    runOnPlayerThread {
        setMediaItems(mediaItems, startIndex, startPosition)
        playWhenReady = play
        prepare()
        updatePlaybackInfo(this)
        callback?.invoke(true)
    }
}

/**
 * This method optimizes player preparation by first starting with the current track and then adding
 * all queued items using [MediaController.addMediaItems]. This helps prevent delays, especially with
 * large queues, and avoids the [ForegroundServiceDidNotStartInTimeException] when starting from background.
 */
fun MediaController.maybePreparePlayer(context: Context, callback: (success: Boolean) -> Unit) {
    if (currentMediaItem == null) {
        ensureBackgroundThread {
            val currentQueueItem = context.queueDAO.getCurrent()
            if (currentQueueItem == null) {
                prepareUsingTracks(context.audioHelper.initQueue(), callback = callback)
            } else {
                val queueItems = context.queueDAO.getAll()
                val currentTrack = context.audioHelper.getTrack(currentQueueItem.trackId) ?: return@ensureBackgroundThread
                val startPosition = currentQueueItem.lastPosition.seconds.inWholeMilliseconds
                prepareUsingTracks(
                    tracks = listOf(currentTrack),
                    startPosition = startPosition,
                ) { success ->
                    callback(success)
                    if (success) {
                        ensureBackgroundThread {
                            val queuedTracks = context.audioHelper.getAllQueuedTracks(queueItems)
                            if (queuedTracks.size == 1) {
                                return@ensureBackgroundThread
                            }

                            val mediaItems = queuedTracks.toMediaItems()
                            val currentIndex = mediaItems.indexOfTrack(currentTrack)
                            runOnPlayerThread {
                                addMediaItems(0, mediaItems.take(currentIndex))
                                addMediaItems(currentIndex + 1, mediaItems.takeLast(mediaItems.lastIndex - currentIndex))
                            }
                        }
                    }
                }
            }
        }
    } else {
        callback(false)
    }
}
