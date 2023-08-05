package com.simplemobiletools.musicplayer.services.playback

import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.services.playback.library.MediaItemProvider

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    internal lateinit var player: ExoPlayer

    internal lateinit var mediaSession: MediaLibrarySession

    internal lateinit var mediaItemProvider: MediaItemProvider

    internal var currentRoot = ""

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer(config.gaplessPlayback)
        mediaItemProvider = MediaItemProvider(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaSession()
        clearListener()
        stopSleepTimer()
    }

    fun stopService() {
        player.stop()
        releaseMediaSession()
        stopSelf()
    }

    private fun releaseMediaSession() {
        mediaSession.release()
        player.removeListener(getPlayerListener())
        player.release()
    }
}

