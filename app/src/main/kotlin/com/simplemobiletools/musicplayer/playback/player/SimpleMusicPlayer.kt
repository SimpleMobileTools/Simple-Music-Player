package com.simplemobiletools.musicplayer.playback.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@UnstableApi
class SimpleMusicPlayer(private val exoPlayer: ExoPlayer) : ForwardingPlayer(exoPlayer) {

    /**
     * The default implementation only advertises the seek to next and previous item in the case
     * that it's not the first or last track. We manually advertise that these
     * are available to ensure next/previous buttons are always visible.
     */
    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            .build()
    }

    override fun play() {
        playWhenReady = true
    }

    fun getAudioSessionId(): Int {
        return exoPlayer.audioSessionId
    }

    fun setSkipSilence(skipSilence: Boolean) {
        exoPlayer.skipSilenceEnabled = skipSilence
    }

    fun setPauseAtEndOfMediaItems(pauseAtEnd: Boolean) {
        exoPlayer.pauseAtEndOfMediaItems = pauseAtEnd
    }
}
