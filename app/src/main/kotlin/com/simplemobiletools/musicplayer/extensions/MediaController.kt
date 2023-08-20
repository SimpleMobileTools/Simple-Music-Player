package com.simplemobiletools.musicplayer.extensions

import android.os.Bundle
import androidx.media3.session.MediaController
import com.simplemobiletools.musicplayer.services.playback.CustomCommands

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
