package com.simplemobiletools.musicplayer.helpers

import android.app.Application
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
import com.simplemobiletools.musicplayer.playback.PlaybackService
import java.util.concurrent.Executors

class SimpleMediaController(val context: Application) {
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null

    @Synchronized
    fun createControllerAsync() {
        controllerFuture = MediaController
            .Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .setApplicationLooper(Looper.getMainLooper())
            .buildAsync()

        controllerFuture.addListener({
            controller = getControllerSync()
        }, MoreExecutors.directExecutor())
    }

    private fun getControllerSync() = controllerFuture.getOrNull()

    private fun shouldCreateNewController(): Boolean {
        return if (!::controllerFuture.isInitialized) {
            return true
        } else {
            controllerFuture.isCancelled || controllerFuture.isDone && getControllerSync()?.isConnected == false
        }
    }

    private fun acquireController(callback: (() -> Unit)? = null) {
        executorService.execute {
            if (shouldCreateNewController()) {
                createControllerAsync()
            } else {
                controller = getControllerSync()
            }

            callback?.invoke()
        }
    }

    fun releaseController() {
        MediaController.releaseFuture(controllerFuture)
    }

    fun withController(callback: MediaController.() -> Unit) {
        val controller = controller
        if (controller != null && controller.isConnected) {
            controller.runOnPlayerThread(callback)
        } else {
            acquireController {
                getControllerSync()?.runOnPlayerThread(callback)
            }
        }
    }

    fun addListener(listener: Listener) {
        withController {
            addListener(listener)
        }
    }

    fun removeListener(listener: Listener) {
        withController {
            removeListener(listener)
        }
    }

    companion object {
        private var instance: SimpleMediaController? = null

        fun getInstance(context: Context): SimpleMediaController {
            if (instance == null) {
                instance = SimpleMediaController(context.applicationContext as Application)
            }

            return instance!!
        }

        fun destroyInstance() {
            instance?.releaseController()
            instance = null
        }
    }
}
