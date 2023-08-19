package com.simplemobiletools.musicplayer.activities

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.simplemobiletools.musicplayer.extensions.getOrNull
import com.simplemobiletools.musicplayer.extensions.runOnPlayerThread
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import com.simplemobiletools.musicplayer.services.playback.PlaybackService.Companion.updatePlaybackInfo
import java.util.concurrent.Executors

/**
 * Base class for activities that want to control the [Player].
 */
abstract class SimpleControllerActivity : SimpleActivity(), Player.Listener {
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }

    private lateinit var controllerFuture: ListenableFuture<MediaController>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        newControllerAsync()
    }

    override fun onStart() {
        super.onStart()
        acquireController()
    }

    override fun onStop() {
        super.onStop()
        releaseController()
    }

    private fun newControllerAsync() {
        controllerFuture = MediaController
            .Builder(applicationContext, SessionToken(this, ComponentName(this, PlaybackService::class.java)))
            .buildAsync()

        controllerFuture.addListener({
            controllerFuture.getOrNull()?.addListener(this)
        }, MoreExecutors.directExecutor())
    }

    private fun shouldCreateNewController(): Boolean {
        return controllerFuture.isCancelled || controllerFuture.isDone && controllerFuture.getOrNull()?.isConnected == false
    }

    private fun acquireController(callback: (() -> Unit)? = null) {
        executorService.execute {
            if (shouldCreateNewController()) {
                newControllerAsync()
            }

            callback?.invoke()
        }
    }

    private fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }

    /**
     * The [callback] is executed on a background player thread. When performing UI operations, callers should use [runOnUiThread].
     */
    fun withPlayer(callback: MediaController.() -> Unit) {
        acquireController {
            controllerFuture.getOrNull()?.runOnPlayerThread {
                callback(this)
            }
        }
    }

    fun playMediaItems(mediaItems: List<MediaItem>, startIndex: Int = 0, startPosition: Long = 0, startActivity: Boolean = true) {
        withPlayer {
            if (startActivity) {
                startActivity(
                    Intent(this@SimpleControllerActivity, TrackActivity::class.java)
                )
            }

            setMediaItems(mediaItems, startIndex, startPosition)
            prepare()
            play()
            updatePlaybackInfo(this)
        }
    }
}
