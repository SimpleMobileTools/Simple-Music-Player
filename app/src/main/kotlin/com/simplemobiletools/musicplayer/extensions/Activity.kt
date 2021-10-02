package com.simplemobiletools.musicplayer.extensions

import android.app.Activity
import android.provider.MediaStore
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.dialogs.SelectPlaylistDialog
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

fun Activity.deleteTracks(tracks: List<Track>, callback: () -> Unit) {
    tracks.forEach { track ->
        try {
            val where = "${MediaStore.Audio.Media._ID} = ?"
            val args = arrayOf(track.mediaStoreId.toString())
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            contentResolver.delete(uri, where, args)
            tracksDAO.removeTrack(track.mediaStoreId)
        } catch (ignored: Exception) {
        }
    }

    removeQueueItems(tracks) {}
    EventBus.getDefault().post(Events.TrackDeleted())
    callback()
}
