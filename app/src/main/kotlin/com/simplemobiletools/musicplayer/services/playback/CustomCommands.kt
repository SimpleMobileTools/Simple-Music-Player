package com.simplemobiletools.musicplayer.services.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.simplemobiletools.musicplayer.R

/**
 * Enum class representing custom commands that are used within the app and by media controller clients (e.g. system media controls).
 */
enum class CustomCommands(val customAction: String) {
    CLOSE_PLAYER(customAction = "com.simplemobiletools.musicplayer.CLOSE_PLAYER"),
    RELOAD_CONTENT(customAction = "com.simplemobiletools.musicplayer.RELOAD_CONTENT"),
    TOGGLE_SLEEP_TIMER(customAction = "com.simplemobiletools.musicplayer.TOGGLE_SLEEP_TIMER");

    val sessionCommand = SessionCommand(customAction, Bundle.EMPTY)

    companion object {
        fun fromSessionCommand(sessionCommand: SessionCommand): CustomCommands? {
            return values().find { it.customAction == sessionCommand.customAction }
        }
    }
}

internal val customCommands = CustomCommands.values().map { it.sessionCommand }

internal fun Context.getCustomLayout(): List<CommandButton> {
    return listOf(
        CommandButton.Builder()
            .setDisplayName(getString(R.string.close))
            .setSessionCommand(CustomCommands.CLOSE_PLAYER.sessionCommand)
            .setIconResId(R.drawable.ic_cross_vector)
            .build()
    )
}
