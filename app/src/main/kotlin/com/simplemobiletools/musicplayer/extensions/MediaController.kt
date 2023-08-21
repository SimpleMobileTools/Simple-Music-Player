package com.simplemobiletools.musicplayer.extensions

import android.content.Context
import android.os.Bundle
import androidx.media3.session.MediaController
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.models.toMediaItems
import com.simplemobiletools.musicplayer.playback.CustomCommands
import kotlin.time.Duration.Companion.seconds

fun MediaController.sendCommand(command: CustomCommands) = sendCustomCommand(command.sessionCommand, Bundle.EMPTY)

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

fun MediaController.prepareUsingTracks(tracks: List<Track>, startIndex: Int = 0, startPosition: Long = 0, callback: (success: Boolean) -> Unit) {
    if (tracks.isEmpty()) {
        callback(false)
        return
    }

    val mediaItems = tracks.toMediaItems()
    runOnPlayerThread {
        setMediaItems(mediaItems, startIndex, startPosition)
        prepare()
        callback(true)
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
