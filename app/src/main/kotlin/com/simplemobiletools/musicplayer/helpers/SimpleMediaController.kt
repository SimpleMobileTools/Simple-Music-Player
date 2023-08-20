package com.simplemobiletools.musicplayer.helpers

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import androidx.media3.common.Player.Listener
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.simplemobiletools.musicplayer.extensions.getOrNull
import com.simplemobiletools.musicplayer.extensions.runOnPlayerThread
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import java.util.concurrent.Executors

class SimpleMediaController(val context: Context, val listener: Listener?) {
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }

    private lateinit var controllerFuture: ListenableFuture<MediaController>

    init {
        newControllerAsync()
    }

    private fun newControllerAsync() {
        controllerFuture = MediaController
            .Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()

        if (listener != null) {
            controllerFuture.addListener({
                controllerFuture.getOrNull()?.addListener(listener)
            }, MoreExecutors.directExecutor())
        }
    }

    private fun shouldCreateNewController(): Boolean {
        return controllerFuture.isCancelled || controllerFuture.isDone && controllerFuture.getOrNull()?.isConnected == false
    }

    fun acquireController(callback: (() -> Unit)? = null) {
        executorService.execute {
            if (shouldCreateNewController()) {
                newControllerAsync()
            }

            callback?.invoke()
        }
    }

    fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }

    fun withController(callback: MediaController.() -> Unit) {
        acquireController {
            controllerFuture.getOrNull()?.runOnPlayerThread {
                callback(this)
            }
        }
    }
}
