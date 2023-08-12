package com.simplemobiletools.musicplayer.extensions

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.simplemobiletools.musicplayer.helpers.PlaybackSetting
import com.simplemobiletools.musicplayer.services.playback.player.PlayerListener

val Player.isPlayingOrBuffering: Boolean
    get() = when (playbackState) {
        Player.STATE_BUFFERING -> true
        Player.STATE_ENDED, Player.STATE_IDLE -> false
        else -> isPlaying || playWhenReady
    }

val Player.currentMediaItems: List<MediaItem>
    get() = (0 until (mediaItemCount)).map { getMediaItemAt(it) }

val Player.nextMediaItem: MediaItem?
    get() = if (hasNextMediaItem()) {
        getMediaItemAt(nextMediaItemIndex)
    } else {
        null
    }

/**
 * [PlaybackSetting.STOP_AFTER_CURRENT_TRACK] is handled manually as it isn't supported by media3 player. See [PlayerListener.onMediaItemTransition].
 */
fun Player.setRepeatMode(playbackSetting: PlaybackSetting) {
    repeatMode = when (playbackSetting) {
        PlaybackSetting.REPEAT_OFF -> Player.REPEAT_MODE_OFF
        PlaybackSetting.REPEAT_PLAYLIST -> Player.REPEAT_MODE_ALL
        PlaybackSetting.REPEAT_TRACK -> Player.REPEAT_MODE_ONE
        PlaybackSetting.STOP_AFTER_CURRENT_TRACK -> Player.REPEAT_MODE_ONE
    }
}
