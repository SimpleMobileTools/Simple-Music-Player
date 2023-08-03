package com.simplemobiletools.musicplayer.services.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.simplemobiletools.musicplayer.R

const val CUSTOM_COMMAND_CLOSE_PLAYER = "com.simplemobiletools.musicplayer.CLOSE_PLAYER"
const val CUSTOM_COMMAND_RELOAD_CONTENT = "com.simplemobiletools.musicplayer.RELOAD_CONTENT"

val customCommands = listOf(
    SessionCommand(CUSTOM_COMMAND_CLOSE_PLAYER, Bundle.EMPTY),
    SessionCommand(CUSTOM_COMMAND_RELOAD_CONTENT, Bundle.EMPTY)
)

internal lateinit var customLayout: List<CommandButton>

internal fun Context.getCloseCommandButton(sessionCommand: SessionCommand): CommandButton {
    return CommandButton.Builder()
        .setDisplayName(getString(R.string.close))
        .setSessionCommand(sessionCommand)
        .setIconResId(R.drawable.ic_cross_vector)
        .build()
}
