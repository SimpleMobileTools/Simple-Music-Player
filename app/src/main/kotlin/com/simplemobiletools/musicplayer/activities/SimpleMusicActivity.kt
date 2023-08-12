package com.simplemobiletools.musicplayer.activities

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.extensions.openNotificationSettings
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.isPlayingOrBuffering
import com.simplemobiletools.musicplayer.extensions.togglePlayback
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import com.simplemobiletools.musicplayer.views.CurrentTrackBar
import java.util.concurrent.Executors

abstract class SimpleMusicActivity : SimpleActivity(), Player.Listener {
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4))
    }

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController
        get() {
            if (controllerFuture.isDone) {
                val activeController = controllerFuture.get()
                if (!activeController.isConnected) {
                    runOnUiThread {
                        activeController.release()
                    }

                    newControllerAsync()
                }
            }

            return controllerFuture.get()
        }

    private var trackBarView: CurrentTrackBar? = null

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> acquireController()
                    Lifecycle.Event.ON_STOP -> releaseController()
                    else -> {}
                }
            }
        )

        newControllerAsync()
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        updateCurrentTrackBar()
    }

    private fun newControllerAsync() {
        controllerFuture = MediaController
            .Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java)))
            .buildAsync()

        controllerFuture.addListener({
            controller.addListener(this)
        }, MoreExecutors.directExecutor())
    }

    private fun acquireController() {
        if (controllerFuture.isDone && !controllerFuture.get().isConnected) {
            newControllerAsync()
        }
    }

    private fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }

    fun withPlayer(callback: MediaController.() -> Unit) {
        if (controllerFuture.isDone && controllerFuture.get().isConnected) {
            callback(controller)
        } else {
            executorService.execute {
                val activeController = controller
                runOnUiThread {
                    callback(activeController)
                }
            }
        }
    }

    fun playMediaItems(mediaItems: List<MediaItem>, startIndex: Int = 0, startPosition: Long = 0) = withPlayer {
        setMediaItems(mediaItems, startIndex, startPosition)
        prepare()
        play()
    }

    fun setupCurrentTrackBar(trackBar: CurrentTrackBar) {
        trackBarView = trackBar
        trackBarView?.setOnClickListener {
            hideKeyboard()
            handleNotificationPermission { granted ->
                if (granted) {
                    Intent(this, TrackActivity::class.java).apply {
                        startActivity(this)
                    }
                } else {
                    PermissionRequiredDialog(this, R.string.allow_notifications_music_player, { openNotificationSettings() })
                }
            }
        }
    }

    private fun updateCurrentTrackBar() = withPlayer {
        trackBarView?.initialize {
            withPlayer { togglePlayback() }
        }

        trackBarView?.updateCurrentTrack(currentMediaItem)
        trackBarView?.updateTrackState(isPlayingOrBuffering)
    }

    @CallSuper
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        trackBarView?.updateCurrentTrack(mediaItem)
    }

    @CallSuper
    override fun onPlaybackStateChanged(playbackState: Int) = withPlayer {
        trackBarView?.updateTrackState(isPlayingOrBuffering)
    }

    @CallSuper
    override fun onIsPlayingChanged(isPlaying: Boolean) = withPlayer {
        trackBarView?.updateTrackState(isPlayingOrBuffering)
    }
}
