package org.fossify.musicplayer.extensions

import android.os.Bundle
import androidx.media3.session.MediaController
import org.fossify.musicplayer.playback.CustomCommands

fun MediaController.sendCommand(command: CustomCommands, extras: Bundle = Bundle.EMPTY) = sendCustomCommand(command.sessionCommand, extras)
