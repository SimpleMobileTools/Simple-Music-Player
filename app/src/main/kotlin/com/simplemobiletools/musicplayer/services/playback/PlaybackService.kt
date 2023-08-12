package com.simplemobiletools.musicplayer.services.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.os.postDelayed
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.NotificationHelper
import com.simplemobiletools.musicplayer.helpers.getPermissionToRequest
import com.simplemobiletools.musicplayer.services.playback.library.MediaItemProvider
import com.simplemobiletools.musicplayer.services.playback.player.PlayerListener
import com.simplemobiletools.musicplayer.services.playback.player.SimpleMusicPlayer
import com.simplemobiletools.musicplayer.services.playback.player.initializeSessionAndPlayer

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    internal lateinit var player: SimpleMusicPlayer

    internal lateinit var mediaSession: MediaLibrarySession

    internal lateinit var mediaItemProvider: MediaItemProvider

    internal var listener: PlayerListener? = null

    internal var currentRoot = ""

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer(handleAudioFocus = true, handleAudioBecomingNoisy = true, skipSilence = config.gaplessPlayback)
        mediaItemProvider = MediaItemProvider(this)

        // we may or may not have storage permission at this time
        if (hasPermission(getPermissionToRequest())) {
            mediaItemProvider.reload()
        } else {
            showNoPermissionNotification()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    private fun releaseMediaSession() {
        mediaSession.release()
        player.removeListener(listener!!)
        player.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaSession()
        clearListener()
        stopSleepTimer()
    }

    fun stopService() {
        player.pause()
        player.stop()
        stopSelf()
    }

    private fun showNoPermissionNotification() {
        Handler(Looper.getMainLooper()).postDelayed(delayInMillis = 100L) {
            try {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    NotificationHelper.createInstance(this).createNoPermissionNotification()
                )
            } catch (ignored: Exception) {
            }
        }
    }
}

