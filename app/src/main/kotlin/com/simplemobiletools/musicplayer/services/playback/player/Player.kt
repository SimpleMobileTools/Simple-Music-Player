@file:UnstableApi

package com.simplemobiletools.musicplayer.services.playback.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.setRepeatMode
import com.simplemobiletools.musicplayer.helpers.SEEK_INTERVAL_MS
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import com.simplemobiletools.musicplayer.services.playback.getCustomLayout
import com.simplemobiletools.musicplayer.services.playback.getMediaSessionCallback

internal fun PlaybackService.initializeSessionAndPlayer(handleAudioFocus: Boolean, handleAudioBecomingNoisy: Boolean, skipSilence: Boolean) {
    player = initializePlayer(handleAudioFocus, handleAudioBecomingNoisy, skipSilence)
    listener = PlayerListener(context = this)
    player.addListener(listener!!)

    mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, player, getMediaSessionCallback())
        .setSessionActivity(getSessionActivityIntent())
        .build()

    mediaSession.setCustomLayout(getCustomLayout())
}

private fun Context.initializePlayer(handleAudioFocus: Boolean, handleAudioBecomingNoisy: Boolean, skipSilence: Boolean): SimpleMusicPlayer {
    val renderersFactory = AudioOnlyRenderersFactory(context = this)
    val player = SimpleMusicPlayer(
        ExoPlayer.Builder(this, renderersFactory)
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
            .setSeekBackIncrementMs(SEEK_INTERVAL_MS)
            .setSeekForwardIncrementMs(SEEK_INTERVAL_MS)
            .build()
    )

    player.setRepeatMode(config.playbackSetting)
    player.setPlaybackSpeed(config.playbackSpeed)
    player.shuffleModeEnabled = config.isShuffleEnabled
    return player
}

private fun Context.getSessionActivityIntent(): PendingIntent {
    return PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
