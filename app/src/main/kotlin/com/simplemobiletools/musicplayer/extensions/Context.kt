package com.simplemobiletools.musicplayer.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import android.util.Size
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.*
import com.simplemobiletools.musicplayer.models.*
import com.simplemobiletools.musicplayer.services.MusicService
import java.io.File

@SuppressLint("NewApi")
fun Context.sendIntent(action: String) {
    Intent(this, MusicService::class.java).apply {
        this.action = action
        try {
            if (isOreoPlus()) {
                startForegroundService(this)
            } else {
                startService(this)
            }
        } catch (ignored: Exception) {
        }
    }
}

val Context.config: Config get() = Config.newInstance(applicationContext)

val Context.playlistDAO: PlaylistsDao get() = getTracksDB().PlaylistsDao()

val Context.tracksDAO: SongsDao get() = getTracksDB().SongsDao()

val Context.queueDAO: QueueItemsDao get() = getTracksDB().QueueItemsDao()

val Context.artistDAO: ArtistsDao get() = getTracksDB().ArtistsDao()

val Context.albumsDAO: AlbumsDao get() = getTracksDB().AlbumsDao()

val Context.genresDAO: GenresDao get() = getTracksDB().GenresDao()

val Context.audioHelper: AudioHelper get() = AudioHelper(this)

val Context.mediaScanner: SimpleMediaScanner get() = SimpleMediaScanner.getInstance(applicationContext as Application)

fun Context.getTracksDB() = SongsDatabase.getInstance(this)

fun Context.getPlaylistIdWithTitle(title: String) = playlistDAO.getPlaylistWithTitle(title)?.id ?: -1

fun Context.broadcastUpdateWidgetState() {
    Intent(this, MyWidgetProvider::class.java).apply {
        action = TRACK_STATE_CHANGED
        sendBroadcast(this)
    }
}

fun Context.resetQueueItems(newTracks: List<Track>, callback: () -> Unit) {
    ensureBackgroundThread {
        queueDAO.deleteAllItems()
        addQueueItems(newTracks, callback)
    }
}

fun Context.addQueueItems(newTracks: List<Track>, callback: () -> Unit) {
    ensureBackgroundThread {
        val itemsToInsert = ArrayList<QueueItem>()
        var order = 0
        newTracks.forEach {
            val queueItem = QueueItem(it.mediaStoreId, order++, false, 0)
            itemsToInsert.add(queueItem)
        }

        audioHelper.insertTracks(newTracks)
        queueDAO.insertAll(itemsToInsert)
        sendIntent(UPDATE_QUEUE_SIZE)
        callback()
    }
}

fun Context.addNextQueueItem(nextTrack: Track, callback: () -> Unit) {
    ensureBackgroundThread {
        val tracksInQueue = queueDAO.getAll().toMutableList()
        var order = 0
        for (index in 0..tracksInQueue.size) {
            val track = tracksInQueue[index]
            track.trackOrder = order++
            if (track.trackId == MusicService.mCurrTrack!!.mediaStoreId) {
                val currentTrackPosition = tracksInQueue.indexOf(track)
                tracksInQueue.add(currentTrackPosition + 1, QueueItem(nextTrack.mediaStoreId, order + 1, false, 0))
            }
        }

        queueDAO.deleteAllItems()
        queueDAO.insertAll(tracksInQueue)
        sendIntent(UPDATE_QUEUE_SIZE)
        callback()
    }
}

fun Context.removeQueueItems(tracks: List<Track>, callback: (() -> Unit)? = null) {
    ensureBackgroundThread {
        tracks.forEach {
            queueDAO.removeQueueItem(it.mediaStoreId)
            MusicService.mTracks.remove(it)
        }
        callback?.invoke()
    }
}

fun Context.getMediaStoreIdFromPath(path: String): Long {
    var id = 0L
    val projection = arrayOf(
        Audio.Media._ID
    )

    val uri = getFileUri(path)
    val selection = "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs = arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                id = cursor.getLongValue(Audio.Media._ID)
            }
        }
    } catch (ignored: Exception) {
    }

    return id
}

fun Context.getFolderTracks(path: String, rescanWrongPaths: Boolean, callback: (tracks: ArrayList<Track>) -> Unit) {
    val folderTracks = getFolderTrackPaths(File(path))
    val allTracks = audioHelper.getAllTracks()
    val wantedTracks = ArrayList<Track>()
    val wrongPaths = ArrayList<String>()    // rescan paths that are not present in the MediaStore

    folderTracks.forEach { trackPath ->
        var trackAdded = false
        val mediaStoreId = getMediaStoreIdFromPath(trackPath)
        if (mediaStoreId != 0L) {
            allTracks.firstOrNull { it.mediaStoreId == mediaStoreId }?.apply {
                id = 0
                wantedTracks.add(this)
                trackAdded = true
            }
        }

        if (!trackAdded) {
            val track = RoomHelper(this).getTrackFromPath(trackPath)
            if (track != null && track.mediaStoreId != 0L) {
                wantedTracks.add(track)
            } else {
                wrongPaths.add(trackPath)
            }
        }
    }

    if (wrongPaths.isEmpty() || !rescanWrongPaths) {
        callback(wantedTracks)
    } else {
        rescanPaths(wrongPaths) {
            getFolderTracks(path, false) { tracks ->
                callback(tracks)
            }
        }
    }
}

private fun getFolderTrackPaths(folder: File): ArrayList<String> {
    val trackFiles = ArrayList<String>()
    val files = folder.listFiles() ?: return trackFiles
    files.forEach {
        if (it.isDirectory) {
            trackFiles.addAll(getFolderTrackPaths(it))
        } else if (it.isAudioFast()) {
            trackFiles.add(it.absolutePath)
        }
    }
    return trackFiles
}

fun Context.getArtistCoverArt(artist: Artist, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (artist.albumArt.isEmpty()) {
            val track = audioHelper.getArtistTracks(artist.id).firstOrNull()
            getTrackCoverArt(track, callback)
        } else {
            Handler(Looper.getMainLooper()).post {
                callback(artist.albumArt)
            }
        }
    }
}

fun Context.getAlbumCoverArt(album: Album, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (album.coverArt.isEmpty()) {
            val track = audioHelper.getAlbumTracks(album.id).firstOrNull()
            getTrackCoverArt(track, callback)
        } else {
            Handler(Looper.getMainLooper()).post {
                callback(album.coverArt)
            }
        }
    }
}

fun Context.getGenreCoverArt(genre: Genre, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (genre.albumArt.isEmpty()) {
            val track = audioHelper.getGenreTracks(genre.id).firstOrNull()
            getTrackCoverArt(track, callback)
        } else {
            Handler(Looper.getMainLooper()).post {
                callback(genre.albumArt)
            }
        }
    }
}

fun Context.getTrackCoverArt(track: Track?, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (track == null) {
            Handler(Looper.getMainLooper()).post {
                callback(null)
            }
            return@ensureBackgroundThread
        }

        val coverArt = track.coverArt.ifEmpty {
            loadTrackCoverArt(track)
        }

        Handler(Looper.getMainLooper()).post {
            callback(coverArt)
        }
    }
}

fun Context.getMediaItemCoverArt(mediaItem: MediaItem?, callback: (coverArt: Any?) -> Unit) {
    ensureBackgroundThread {
        if (mediaItem == null) {
            Handler(Looper.getMainLooper()).post {
                callback(null)
            }
            return@ensureBackgroundThread
        }

        val artworkUri = mediaItem.mediaMetadata.artworkUri.toString()
        val coverArt = artworkUri.ifEmpty {
            // TODO: load bitmap using path
        }

        Handler(Looper.getMainLooper()).post {
            callback(coverArt)
        }
    }
}

fun Context.loadTrackCoverArt(track: Track?): Bitmap? {
    if (track == null) {
        return null
    }

    val artworkUri = track.coverArt
    if (artworkUri.startsWith("content://")) {
        try {
            return MediaStore.Images.Media.getBitmap(contentResolver, artworkUri.toUri())
        } catch (ignored: Exception) {
        }
    }

    if (isQPlus()) {
        val coverArtHeight = resources.getCoverArtHeight()
        val size = Size(coverArtHeight, coverArtHeight)
        if (artworkUri.startsWith("content://")) {
            try {
                return contentResolver.loadThumbnail(artworkUri.toUri(), size, null)
            } catch (ignored: Exception) {
            }
        }

        val path = track.path
        if (path.isNotEmpty() && File(path).exists()) {
            try {
                return ThumbnailUtils.createAudioThumbnail(File(track.path), size, null)
            } catch (ignored: OutOfMemoryError) {
            } catch (ignored: Exception) {
            }
        }
    }

    return null
}

fun Context.loadGlideResource(
    model: Any?,
    options: RequestOptions,
    size: Size,
    onLoadFailed: (e: Exception?) -> Unit,
    onResourceReady: (resource: Drawable) -> Unit,
) {
    ensureBackgroundThread {
        try {
            Glide.with(this)
                .load(model)
                .apply(options)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                        onLoadFailed(e)
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        onResourceReady(resource)
                        return false
                    }
                })
                .submit(size.width, size.height)
                .get()
        } catch (e: Exception) {
            onLoadFailed(e)
        }
    }
}

fun Context.getMediaItemFromUri(uri: Uri?, callback: (mediaItem: MediaItem) -> Unit) {
    if (uri == null) {
        return
    }

    ensureBackgroundThread {
        val path = getRealPathFromURI(uri) ?: return@ensureBackgroundThread
        val allTracks = audioHelper.getAllTracks()
        val track = allTracks.find { it.path == path } ?: RoomHelper(this).getTrackFromPath(path) ?: return@ensureBackgroundThread
        callback(track.toMediaItem())
    }
}

fun Context.isTabVisible(flag: Int) = config.showTabs and flag != 0
