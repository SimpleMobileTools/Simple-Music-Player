package com.simplemobiletools.musicplayer.services.playback.player

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.os.postDelayed
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.currentMediaItems
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import org.greenrobot.eventbus.EventBus
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@UnstableApi
class PlayerListener(private val context: PlaybackService) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis = 800L

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
            val currentMediaItems = player.currentMediaItems
            if (currentMediaItem != null) {
                context.mediaItemProvider.saveRecentItemsWithStartPosition(
                    items = currentMediaItems,
                    current = currentMediaItem,
                    startPosition = player.currentPosition
                )
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            schedulePositionUpdate()
        } else {
            cancelPositionUpdate()
        }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (context.player.isPlaying) {
            schedulePositionUpdate()
        } else {
            cancelPositionUpdate()
        }
    }

    private fun schedulePositionUpdate() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(updateIntervalMillis) {
            updatePosition()
        }
    }

    private fun cancelPositionUpdate() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun updatePosition() {
        val currentPosition = context.player.currentPosition
        if (currentPosition >= 0) {
            val progress = currentPosition.seconds.toInt(DurationUnit.SECONDS)
            EventBus.getDefault().post(Events.ProgressUpdated(progress))
            schedulePositionUpdate()
        }
    }
}
