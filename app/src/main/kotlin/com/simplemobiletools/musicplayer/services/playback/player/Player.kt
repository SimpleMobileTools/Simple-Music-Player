@file:UnstableApi

package com.simplemobiletools.musicplayer.services.playback.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import com.simplemobiletools.musicplayer.services.playback.getCustomLayout
import com.simplemobiletools.musicplayer.services.playback.getMediaSessionCallback

private const val SEEK_INCREMENT_MS = 10000L

private var playerListener: Player.Listener? = null

internal fun PlaybackService.getPlayerListener(): Player.Listener {
    if (playerListener == null) {
        playerListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_TRACKS_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED
                    )
                ) {
                    val currentMediaId = player.currentMediaItem?.mediaId
                    if (currentMediaId != null && currentRoot.isNotEmpty()) {
                        mediaItemProvider.saveRecentItemsWithStartPosition(
                            mediaId = currentMediaId,
                            startPosition = player.currentPosition,
                            rootId = currentRoot
                        )
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) = toast(R.string.unknown_error_occurred, Toast.LENGTH_LONG)
        }
    }

    return playerListener!!
}

internal fun PlaybackService.initializeSessionAndPlayer(handleAudioFocus: Boolean, handleAudioBecomingNoisy: Boolean, skipSilence: Boolean) {
    player = SimplePlayer(
        ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(handleAudioBecomingNoisy)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                handleAudioFocus
            )
            .setSkipSilenceEnabled(skipSilence)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
    )

    player.addListener(getPlayerListener())

    mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, player, getMediaSessionCallback())
        .setSessionActivity(getSessionActivityIntent())
        .build()

    mediaSession.setCustomLayout(getCustomLayout())
}

private fun Context.getSessionActivityIntent(): PendingIntent {
    return PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
