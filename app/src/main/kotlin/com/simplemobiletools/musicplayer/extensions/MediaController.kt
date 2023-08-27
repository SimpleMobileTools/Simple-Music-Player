package com.simplemobiletools.musicplayer.extensions

import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaController
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.models.toMediaItems
import com.simplemobiletools.musicplayer.playback.CustomCommands
import com.simplemobiletools.musicplayer.playback.PlaybackService.Companion.updatePlaybackInfo

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
        runOnPlayerThread {
            callback?.invoke(false)
        }
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
 * all queued items using [MediaController.addRemainingMediaItems]. This helps prevent delays, especially with
 * large queues, and avoids the [android.app.ForegroundServiceStartNotAllowedException] when starting from background.
 */
fun MediaController.maybePreparePlayer(context: Context, callback: (success: Boolean) -> Unit) {
    if (currentMediaItem == null) {
        ensureBackgroundThread {
            var prepared = false
            context.audioHelper.getAllQueuedTracksLazily { tracks, startIndex, startPositionMs ->
                if (!prepared) {
                    prepareUsingTracks(tracks = tracks, startIndex = startIndex, startPosition = startPositionMs) {
                        callback(it)
                        prepared = it
                    }
                } else {
                    if (tracks.size == 1) {
                        return@getAllQueuedTracksLazily
                    }

                    addRemainingMediaItems(tracks.toMediaItems(), startIndex)
                }
            }
        }
    } else {
        callback(false)
    }
}
