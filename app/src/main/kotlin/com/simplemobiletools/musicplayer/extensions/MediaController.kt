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

fun MediaController.maybePreparePlayer(context: Context, callback: (success: Boolean) -> Unit) {
    if (currentMediaItem == null) {
        ensureBackgroundThread {
            if (context.queueDAO.getAll().isEmpty()) {
                prepareUsingTracks(context.audioHelper.initQueue(), callback = callback)
            } else {
                val queuedTracks = context.audioHelper.getAllQueuedTracks()
                prepareUsingTracks(
                    tracks = queuedTracks,
                    startIndex = maxOf(queuedTracks.indexOfFirst { it.isCurrent() }, 0),
                    startPosition = context.queueDAO.getCurrent()?.lastPosition?.seconds?.inWholeMilliseconds ?: 0,
                    callback = callback
                )
            }
        }
    } else {
        callback(false)
    }
}
