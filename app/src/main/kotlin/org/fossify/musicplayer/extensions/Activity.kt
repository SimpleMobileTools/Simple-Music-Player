package org.fossify.musicplayer.extensions

import android.app.Activity
import android.content.ContentUris
import android.provider.MediaStore
import org.fossify.commons.dialogs.PropertiesDialog
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.sharePathsIntent
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.BuildConfig
import org.fossify.musicplayer.dialogs.SelectPlaylistDialog
import org.fossify.musicplayer.helpers.FLAG_MANUAL_CACHE
import org.fossify.musicplayer.helpers.RoomHelper
import org.fossify.musicplayer.models.Track

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

fun Activity.maybeRescanTrackPaths(tracks: List<Track>, callback: (tracks: List<Track>) -> Unit) {
    val tracksWithoutId = tracks.filter { it.mediaStoreId == 0L || (it.flags and FLAG_MANUAL_CACHE != 0) }
    if (tracksWithoutId.isNotEmpty()) {
        val pathsToRescan = tracksWithoutId.map { it.path }
        rescanPaths(pathsToRescan) {
            for (track in tracks) {
                if (track.mediaStoreId == 0L || (track.flags and FLAG_MANUAL_CACHE != 0)) {
                    track.mediaStoreId = getMediaStoreIdFromPath(track.path)
                }
            }

            callback(tracks)
        }
    } else {
        callback(tracks)
    }
}

fun Activity.showTrackProperties(selectedTracks: List<Track>) {
    val selectedPaths = selectedTracks.map { track ->
        track.path.ifEmpty {
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

fun Activity.shareFiles(tracks: List<Track>) {
    val paths = tracks.map { it.path }
    sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
}
