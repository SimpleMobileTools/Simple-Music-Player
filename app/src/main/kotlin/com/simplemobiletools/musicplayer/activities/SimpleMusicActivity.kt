package com.simplemobiletools.musicplayer.activities

import android.content.ComponentName
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import java.util.concurrent.Executors

abstract class SimpleMusicActivity : SimpleActivity(), Player.Listener {

    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4))
    }

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController
        get() {
            if (controllerFuture.isDone) {
                val completedController = controllerFuture.get()
                if (!completedController.isConnected) {
                    runOnUiThread {
                        completedController.release()
                    }

                    newControllerAsync()
                }
            }

            return controllerFuture.get()
        }


    private fun newControllerAsync() {
        controllerFuture = MediaController
            .Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java)))
            .buildAsync()

        controllerFuture.addListener({
            controller.addListener(this)
        }, MoreExecutors.directExecutor())
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> acquireController()
            Lifecycle.Event.ON_STOP -> releaseController()
            else -> {}
        }
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(lifecycleObserver)
        newControllerAsync()
    }

    private fun acquireController() {
        if (controllerFuture.isDone && !controllerFuture.get().isConnected) {
            newControllerAsync()
        }
    }

    private fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }

    fun withController(callback: MediaController.() -> Unit) {
        if (controllerFuture.isDone && controllerFuture.get().isConnected) {
            callback(controller)
        } else {
            executorService.execute {
                val controller = controller
                runOnUiThread {
                    callback(controller)
                }
            }
        }
    }
}
