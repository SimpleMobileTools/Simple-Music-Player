package com.simplemobiletools.musicplayer.extensions

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isRPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.dialogs.SelectPlaylistDialog
import com.simplemobiletools.musicplayer.helpers.EDIT
import com.simplemobiletools.musicplayer.helpers.EDITED_TRACK
import com.simplemobiletools.musicplayer.helpers.REFRESH_LIST
import com.simplemobiletools.musicplayer.helpers.RoomHelper
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import org.greenrobot.eventbus.EventBus

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

fun BaseSimpleActivity.deleteTracks(tracks: List<Track>, callback: () -> Unit) {
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    if (isRPlus()) {
        val uris = arrayListOf<Uri>()
        tracks.forEach { track ->
            val newUri = ContentUris.withAppendedId(uri, track.mediaStoreId)
            uris.add(newUri)
        }

        deleteSDK30Uris(uris) { success ->
            if (success) {
                removeQueueItems(tracks) {}
                EventBus.getDefault().post(Events.TrackDeleted())
                callback()
            } else {
                toast(R.string.unknown_error_occurred)
            }
        }
        return
    }

    tracks.forEach { track ->
        try {
            val where = "${MediaStore.Audio.Media._ID} = ?"
            val args = arrayOf(track.mediaStoreId.toString())
            contentResolver.delete(uri, where, args)
            tracksDAO.removeTrack(track.mediaStoreId)
        } catch (ignored: Exception) {
        }
    }

    removeQueueItems(tracks) {}
    EventBus.getDefault().post(Events.TrackDeleted())
    callback()
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
