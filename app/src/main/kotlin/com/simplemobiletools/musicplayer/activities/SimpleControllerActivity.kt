package com.simplemobiletools.musicplayer.activities

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.SimpleMediaController
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.models.toMediaItems
import com.simplemobiletools.musicplayer.services.playback.PlaybackService.Companion.updatePlaybackInfo
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * Base class for activities that want to control the [Player].
 */
abstract class SimpleControllerActivity : SimpleActivity(), Player.Listener {
    private lateinit var controller: SimpleMediaController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = SimpleMediaController(applicationContext, this)
    }

    override fun onStart() {
        super.onStart()
        controller.acquireController()
    }

    override fun onStop() {
        super.onStop()
        controller.releaseController()
    }

    /**
     * The [callback] is executed on a background player thread. When performing UI operations, callers should use [runOnUiThread].
     */
    fun withPlayer(callback: MediaController.() -> Unit) = controller.withController(callback)

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
