package com.simplemobiletools.musicplayer.extensions

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.extensions.rescanPaths
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.dialogs.SelectPlaylistDialog
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import org.greenrobot.eventbus.EventBus
import java.io.File

fun Activity.addTracksToPlaylist(tracks: List<Track>, callback: () -> Unit) {
    SelectPlaylistDialog(this) { playlistId ->
        val tracksToAdd = ArrayList<Track>()
        tracks.forEach {
            it.id = 0
            it.playListId = playlistId
            tracksToAdd.add(it)
        }

        ensureBackgroundThread {
            RoomHelper(this).insertTracksWithPlaylist(tracksToAdd)

            runOnUiThread {
                callback()
            }
        }
    }
}

fun Activity.addTracksToQueue(tracks: List<Track>, callback: () -> Unit) {
    addQueueItems(tracks) {
        tracks.forEach { track ->
            if (MusicService.mTracks.none { it.mediaStoreId == track.mediaStoreId }) {
                MusicService.mTracks.add(track)
            }
        }

        runOnUiThread {
            callback()
        }
    }
}

fun Activity.playNextInQueue(track: Track, callback: () -> Unit) {
    val isTrackQueued = MusicService.mTracks.any { it.mediaStoreId == track.mediaStoreId }
    if (isTrackQueued) {
        removeQueueItems(listOf(track))
    }

    addNextQueueItem(track) {
        val currentTrackPosition = MusicService.mTracks.indexOf(MusicService.mCurrTrack)
        MusicService.mTracks.add(currentTrackPosition + 1, track)

        runOnUiThread {
            callback()
        }
    }
}

fun BaseSimpleActivity.deleteTracks(tracks: List<Track>, callback: () -> Unit) {
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

private fun Activity.maybeRescanTrackPaths(tracks: List<Track>, callback: (tracks: List<Track>) -> Unit) {
    val tracksWithoutId = tracks.filter { it.mediaStoreId == 0L || (it.flags and FLAG_MANUAL_CACHE) != 0 }
    if (tracksWithoutId.isNotEmpty()) {
        val pathsToRescan = tracksWithoutId.map { it.path }
        rescanPaths(pathsToRescan) {
            for (track in tracks) {
                if (track.mediaStoreId == 0L || (track.flags and FLAG_MANUAL_CACHE) != 0) {
                    track.mediaStoreId = getMediaStoreIdFromPath(track.path)
                }
            }

            callback(tracks)
        }
    } else {
        callback(tracks)
    }
}

fun Activity.refreshAfterEdit(track: Track) {
    if (track.mediaStoreId == MusicService.mCurrTrack?.mediaStoreId) {
        Intent(this, MusicService::class.java).apply {
            putExtra(EDITED_TRACK, track)
            action = EDIT
            startService(this)
        }
    }

    sendIntent(REFRESH_LIST)
    EventBus.getDefault().post(Events.RefreshTracks())
}

fun Activity.showTrackProperties(selectedTracks: List<Track>) {
    val selectedPaths = selectedTracks.map { track ->
        if (track.path.isNotEmpty()) {
            track.path
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId).toString()
        }
    }

    if (selectedPaths.size <= 1) {
        PropertiesDialog(this, selectedPaths.first(), false)
    } else {
        PropertiesDialog(this, selectedPaths, false)
    }
}

fun Activity.ensureActivityNotDestroyed(callback: () -> Unit) {
    if (!isFinishing && !isDestroyed) {
        callback()
    }
}
