package com.simplemobiletools.musicplayer.playback

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.os.postDelayed
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.NotificationHelper
import com.simplemobiletools.musicplayer.helpers.getPermissionToRequest
import com.simplemobiletools.musicplayer.playback.library.MediaItemProvider
import com.simplemobiletools.musicplayer.playback.player.SimpleMusicPlayer
import com.simplemobiletools.musicplayer.playback.player.initializeSessionAndPlayer

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), MediaSessionService.Listener {
    internal lateinit var player: SimpleMusicPlayer
    internal lateinit var playerThread: HandlerThread
    internal lateinit var playerListener: Player.Listener
    internal lateinit var playerHandler: Handler
    internal lateinit var mediaSession: MediaLibrarySession
    internal lateinit var mediaItemProvider: MediaItemProvider

    internal var currentRoot = ""

    override fun onCreate() {
        super.onCreate()
        setListener(this)
        initializeSessionAndPlayer(handleAudioFocus = true, handleAudioBecomingNoisy = true, skipSilence = config.gaplessPlayback)
        initializeLibrary()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaSession()
        clearListener()
        stopSleepTimer()
        SimpleEqualizer.release()
    }

    fun stopService() {
        withPlayer {
            pause()
            stop()
        }

        stopSelf()
    }

    private fun initializeLibrary() {
        mediaItemProvider = MediaItemProvider(this)
        if (hasPermission(getPermissionToRequest())) {
            mediaItemProvider.reload()
        } else {
            showNoPermissionNotification()
        }
    }

    private fun releaseMediaSession() {
        mediaSession.release()
        withPlayer {
            removeListener(playerListener)
            release()
        }
    }

    internal fun withPlayer(callback: SimpleMusicPlayer.() -> Unit) = playerHandler.post { callback(player) }

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

    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the {@link MediaSessionService} is in the
     * background.
     */
    override fun onForegroundServiceStartNotAllowedException() {
        showErrorToast(getString(com.simplemobiletools.commons.R.string.unknown_error_occurred))
        // todo: show a notification instead.
    }

    companion object {
        // Initializing a media controller might take a noticeable amount of time thus we expose current playback info here to keep things as quick as possible.
        var isPlaying: Boolean = false
            private set
        var currentMediaItem: MediaItem? = null
            private set
        var nextMediaItem: MediaItem? = null
            private set

        fun updatePlaybackInfo(player: Player) {
            currentMediaItem = player.currentMediaItem
            nextMediaItem = player.nextMediaItem
            isPlaying = player.isReallyPlaying
        }
    }
}

