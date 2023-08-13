package com.simplemobiletools.musicplayer.activities

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.simplemobiletools.musicplayer.extensions.runOnPlayerThread
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors

/**
 * Base class for activities that want to control the [Player].
 */
abstract class SimpleControllerActivity : SimpleActivity(), Player.Listener {
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4))
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

    private fun getController(): MediaController? {
        if (controllerFuture.isDone) {
            val activeController = controllerFuture.get()
            if (!activeController.isConnected) {
                activeController.runOnPlayerThread {
                    release()
                }

                newControllerAsync()
            }
        }

        return try {
            controllerFuture.get()
        } catch (e: CancellationException) {
            null
        }
    }

    private fun newControllerAsync() {
        controllerFuture = MediaController
            .Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java)))
            .buildAsync()

        controllerFuture.addListener({
            getController()?.addListener(this)
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

    private fun callOnPlayerThread(callback: MediaController.() -> Unit) {
        getController()?.runOnPlayerThread {
            callback(this)
        }
    }

    /**
     * The [callback] is executed on a background player thread. When performing UI operations, callers should use [runOnUiThread].
     */
    fun withPlayer(callback: MediaController.() -> Unit) {
        if (controllerFuture.isDone && controllerFuture.get().isConnected) {
            callOnPlayerThread(callback)
        } else {
            executorService.execute {
                callOnPlayerThread(callback)
            }
        }
    }

    fun playMediaItems(mediaItems: List<MediaItem>, startIndex: Int = 0, startPosition: Long = 0) = withPlayer {
        setMediaItems(mediaItems, startIndex, startPosition)
        prepare()
        play()
    }
}
