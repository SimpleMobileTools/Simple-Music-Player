package com.simplemobiletools.musicplayer.services.playback.player

import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.currentMediaItems
import com.simplemobiletools.musicplayer.helpers.PlaybackSetting
import com.simplemobiletools.musicplayer.services.playback.PlaybackService

@UnstableApi
class PlayerListener(private val context: PlaybackService) : Player.Listener {

    override fun onPlayerError(error: PlaybackException) = context.toast(R.string.unknown_error_occurred, Toast.LENGTH_LONG)

    override fun onEvents(player: Player, events: Player.Events) {
        if (
            events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_TRACKS_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED
            )
        ) {
            val currentMediaItem = player.currentMediaItem
            if (currentMediaItem != null) {
                context.mediaItemProvider.saveRecentItemsWithStartPosition(
                    mediaItems = player.currentMediaItems,
                    current = currentMediaItem,
                    startPosition = player.currentPosition
                )
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // We handle this manually because this mode isn't supported in the media3 player.
        // It's possible using Exoplayer.setPauseAtEndOfMediaItems() but that would require rebuilding the player.
        val isReasonRepeat = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (isReasonRepeat && context.config.playbackSetting == PlaybackSetting.STOP_AFTER_CURRENT_TRACK) {
            context.player.pause()
        }
    }
}
