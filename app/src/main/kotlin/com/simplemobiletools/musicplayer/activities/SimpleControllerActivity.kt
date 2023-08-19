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
import com.simplemobiletools.musicplayer.services.playback.CustomCommands
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

    fun addTracksToQueue(tracks: List<Track>, callback: () -> Unit) {
        withPlayer {
            val currentMediaItemsIds = currentMediaItems.map { it.mediaId }
            val mediaItems = tracks.toMediaItems().filter { it.mediaId !in currentMediaItemsIds }
            addMediaItems(mediaItems)

            runOnUiThread {
                callback()
            }
        }
    }

    fun removeQueueItems(tracks: List<Track>, callback: (() -> Unit)? = null) {
        withPlayer {
            var currentItemChanged = false
            tracks.forEach {
                val index = currentMediaItems.indexOfTrackOrNull(it)
                if (index != null) {
                    currentItemChanged = index == currentMediaItemIndex
                    removeMediaItem(index)
                }
            }

            if (currentItemChanged) {
                updatePlaybackInfo(this)
            }

            callback?.invoke()
        }
    }

    fun playNextInQueue(track: Track, callback: () -> Unit) {
        withPlayer {
            val mediaItem = track.toMediaItem()
            val indexInQueue = currentMediaItems.indexOfTrack(track)
            if (indexInQueue != -1) {
                moveMediaItem(indexInQueue, currentMediaItemIndex + 1)
            } else {
                addMediaItem(currentMediaItemIndex + 1, mediaItem)
            }

            sendCommand(CustomCommands.SAVE_QUEUE)
            callback()
        }
    }

    fun deleteTracks(tracks: List<Track>, callback: () -> Unit) {
        try {
            audioHelper.deleteTracks(tracks)
            audioHelper.removeInvalidAlbumsArtists()
        } catch (ignored: Exception) {
        }

        val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        maybeRescanTrackPaths(tracks) { tracksToDelete ->
            if (tracksToDelete.isNotEmpty()) {
                if (isRPlus()) {
                    val uris = tracksToDelete.map { ContentUris.withAppendedId(contentUri, it.mediaStoreId) }
                    deleteSDK30Uris(uris) { success ->
                        if (success) {
                            removeQueueItems(tracksToDelete)
                            EventBus.getDefault().post(Events.RefreshFragments())
                            callback()
                        } else {
                            toast(R.string.unknown_error_occurred)
                        }
                    }
                } else {
                    tracksToDelete.forEach { track ->
                        try {
                            val where = "${MediaStore.Audio.Media._ID} = ?"
                            val args = arrayOf(track.mediaStoreId.toString())
                            contentResolver.delete(contentUri, where, args)
                            File(track.path).delete()
                        } catch (ignored: Exception) {
                        }
                    }

                    removeQueueItems(tracksToDelete)
                    EventBus.getDefault().post(Events.RefreshFragments())
                    callback()
                }
            }
        }
    }

    fun refreshAfterEdit(track: Track) {
        withPlayer {
            val currentIndex = currentMediaItemIndex
            val currentPosition = currentPosition
            val currentMediaItems = currentMediaItems
            ensureBackgroundThread {
                val queuedTracks = audioHelper.getAllQueuedTracks()
                val queuedMediaItems = queuedTracks.toMediaItems()
                if (track.mediaStoreId.toString() == currentMediaItem?.mediaId) {
                    // in media3, it's not yet directly possible to update metadata without interrupting the playback
                    val startIndex = maxOf(queuedMediaItems.indexOfTrack(track), 0)
                    playMediaItems(queuedMediaItems, startIndex, currentPosition, startActivity = false)
                } else if (currentMediaItems.indexOfTrack(track) != -1) {
                    removeMediaItems(0, currentMediaItemIndex - 1)
                    removeMediaItems(currentMediaItemIndex + 1, currentMediaItems.lastIndex)
                    addMediaItems(queuedMediaItems.subList(0, currentIndex))
                    addMediaItems(queuedMediaItems.subList(currentIndex + 1, queuedMediaItems.lastIndex))
                }
            }
        }

        EventBus.getDefault().post(Events.RefreshTracks())
    }
}
