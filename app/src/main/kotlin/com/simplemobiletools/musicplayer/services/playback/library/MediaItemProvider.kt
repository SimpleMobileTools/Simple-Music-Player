package com.simplemobiletools.musicplayer.services.playback.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_PLAYLIST
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.buildMediaItem
import com.simplemobiletools.musicplayer.extensions.queueDAO
import com.simplemobiletools.musicplayer.extensions.toMediaItem
import com.simplemobiletools.musicplayer.models.QueueItem

@UnstableApi
internal class MediaItemProvider(private val context: Context) {

    inner class MediaItemNode(val item: MediaItem) {
        private val children: MutableList<MediaItem> = ArrayList()
        fun addChild(mediaId: String) = children.add(treeNodes[mediaId]!!.item)
        fun getChildren() = children.toList()
    }

    private val treeNodes = mutableMapOf<String, MediaItemNode>()
    private var titleMap: MutableMap<String, MediaItemNode> = mutableMapOf()
    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

    private var state: Int = STATE_CREATED
        set(value) {
            if (value == STATE_INITIALIZED || value == STATE_ERROR) {
                synchronized(onReadyListeners) {
                    field = value
                    Handler(Looper.getMainLooper()).post {
                        onReadyListeners.forEach { listener ->
                            listener(state == STATE_INITIALIZED)
                        }
                    }
                }
            } else {
                field = value
            }
        }

    init {
        state = STATE_INITIALIZING

        val rootChildren = mutableListOf<MediaItem>()
        rootChildren += buildMediaItem(
            title = context.getString(R.string.playlists),
            artworkUri = buildDrawableUri(R.drawable.ic_playlist_vector),
            mediaId = SMP_PLAYLISTS_ROOT_ID,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        )

        rootChildren += buildMediaItem(
            title = context.getString(R.string.folders),
            artworkUri = buildDrawableUri(R.drawable.ic_folders_vector),
            mediaId = SMP_FOLDERS_ROOT_ID,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
        )

        rootChildren += buildMediaItem(
            title = context.getString(R.string.artists),
            artworkUri = buildDrawableUri(R.drawable.ic_person_vector),
            mediaId = SMP_ARTISTS_ROOT_ID,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
        )

        rootChildren += buildMediaItem(
            title = context.getString(R.string.albums),
            artworkUri = buildDrawableUri(R.drawable.ic_album_vector),
            mediaId = SMP_ALBUMS_ROOT_ID,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
        )

        rootChildren += buildMediaItem(
            title = context.getString(R.string.tracks),
            artworkUri = buildDrawableUri(R.drawable.ic_music_note_vector),
            mediaId = SMP_TRACKS_ROOT_ID,
            mediaType = MEDIA_TYPE_PLAYLIST
        )

        rootChildren += buildMediaItem(
            title = context.getString(R.string.genres),
            artworkUri = buildDrawableUri(R.drawable.ic_genre_vector),
            mediaId = SMP_GENRES_ROOT_ID,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES
        )

        addNodeAndChildren(
            item = buildMediaItem(title = context.getString(R.string.root), mediaId = SMP_ROOT_ID, mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            children = rootChildren
        )

        reload()
    }

    fun whenReady(performAction: (Boolean) -> Unit): Boolean {
        return when (state) {
            STATE_CREATED, STATE_INITIALIZING -> {
                onReadyListeners += performAction
                false
            }

            else -> {
                performAction(state != STATE_ERROR)
                true
            }
        }
    }

    operator fun get(mediaId: String) = getNode(mediaId)?.item

    fun getRootItem() = get(SMP_ROOT_ID)!!

    fun getChildren(id: String) = getNode(id)?.getChildren()

    fun getRandomItem(): MediaItem {
        var curRoot = getRootItem()
        while (curRoot.mediaMetadata.isBrowsable == true) {
            val children = getChildren(curRoot.mediaId)!!
            curRoot = children.random()
        }
        return curRoot
    }

    fun getItemFromSearch(searchQuery: String): MediaItem? {
        return if (searchQuery in titleMap) {
            titleMap[searchQuery]?.item
        } else {
            val partialMatches = titleMap.keys.filter { it.contains(searchQuery) }
            if (partialMatches.isNotEmpty()) {
                titleMap[partialMatches.first()]?.item
            } else {
                null
            }
        }
    }

    fun getRecentItemsWithStartPosition(random: Boolean = true): MediaItemsWithStartPosition {
        val recentItems = context.queueDAO.getAll().mapNotNull { getMediaItemFromQueueItem(it) }
        var startPosition = 0L
        val currentItem = context.queueDAO.getCurrent()?.let {
            val mediaItem = getMediaItemFromQueueItem(it) ?: return@let null
            val metadata = mediaItem.mediaMetadata.buildUpon().build()
            startPosition = it.lastPosition * 1000L
            mediaItem.buildUpon().setMediaMetadata(metadata).build()
        }

        if (recentItems.isEmpty() && random) {
            // return a random item if queue is empty
            val recent = getRandomItem().mediaId
            saveRecentItemsWithStartPosition(mediaId = recent, startPosition = 0, rootId = SMP_TRACKS_ROOT_ID)
            return getRecentItemsWithStartPosition(random = false)
        }

        val startIndex = maxOf(recentItems.indexOf(currentItem), 0)
        return MediaItemsWithStartPosition(recentItems, startIndex, startPosition)
    }

    fun saveRecentItemsWithStartPosition(mediaId: String, startPosition: Long, rootId: String) {
        val mediaItems = getChildren(rootId)?.filter { it.mediaMetadata.isPlayable == true } ?: return
        if (mediaItems.isEmpty()) {
            return
        }

        ensureBackgroundThread {
            val trackId = mediaId.toLong()
            val queueItems = mediaItems.mapIndexed { index, mediaItem ->
                QueueItem(trackId = mediaItem.mediaId.toLong(), trackOrder = index, isCurrent = false, lastPosition = 0)
            }

            context.audioHelper.updateQueue(queueItems, trackId, startPosition)
        }
    }

    fun reload() {
        ensureBackgroundThread {
            with(context.audioHelper) {
                getAllPlaylists().forEach { playlist ->
                    addNodeAndChildren(SMP_PLAYLISTS_ROOT_ID, playlist.toMediaItem(), getPlaylistTracks(playlist.id).map { it.toMediaItem() })
                }

                getAllFolders().forEach { folder ->
                    addNodeAndChildren(SMP_FOLDERS_ROOT_ID, folder.toMediaItem(), getFolderTracks(folder.title).map { it.toMediaItem() })
                }

                getAllArtists().forEach { artist ->
                    addNodeAndChildren(SMP_ARTISTS_ROOT_ID, artist.toMediaItem(), getArtistAlbums(artist.id).map { it.toMediaItem() })
                }

                getAllAlbums().forEach { album ->
                    addNodeAndChildren(SMP_ALBUMS_ROOT_ID, album.toMediaItem(), getAlbumTracks(album.id).map { it.toMediaItem() })
                }

                getAllTracks().forEach { track ->
                    val trackMediaItem = track.toMediaItem()
                    addNodeAndChildren(SMP_TRACKS_ROOT_ID, trackMediaItem)
                    titleMap[track.title.lowercase()] = MediaItemNode(trackMediaItem)
                }

                getAllGenres().forEach { genre ->
                    addNodeAndChildren(SMP_GENRES_ROOT_ID, genre.toMediaItem(), getGenreTracks(genre.id).map { it.toMediaItem() })
                }
            }

            state = STATE_INITIALIZED
        }
    }

    private fun getNode(id: String) = treeNodes[id]

    private fun addNodeAndChildren(parentId: String? = null, item: MediaItem, children: List<MediaItem>? = null) {
        val itemNode = MediaItemNode(item)
        treeNodes[item.mediaId] = itemNode
        children?.forEach { child ->
            treeNodes[child.mediaId] = MediaItemNode(child)
            itemNode.addChild(child.mediaId)
        }

        if (parentId != null) {
            getNode(parentId)!!.addChild(item.mediaId)
        }
    }

    private fun buildDrawableUri(drawableRes: Int): Uri? {
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.resources.getResourcePackageName(drawableRes))
            .appendPath(context.resources.getResourceTypeName(drawableRes))
            .appendPath(context.resources.getResourceEntryName(drawableRes))
            .build()
    }

    private fun getMediaItemFromQueueItem(queueItem: QueueItem) = getNode(queueItem.trackId.toString())?.item

    companion object {
        private const val SMP_ROOT_ID = "__ROOT__"
        private const val SMP_PLAYLISTS_ROOT_ID = "__PLAYLISTS__"
        private const val SMP_FOLDERS_ROOT_ID = "__FOLDERS__"
        private const val SMP_ARTISTS_ROOT_ID = "__ARTISTS__"
        private const val SMP_ALBUMS_ROOT_ID = "__ALBUMS__"
        private const val SMP_TRACKS_ROOT_ID = "__TRACKS__"
        private const val SMP_GENRES_ROOT_ID = "__GENRES__"

        private const val STATE_CREATED = 1
        private const val STATE_INITIALIZING = 2
        private const val STATE_INITIALIZED = 3
        private const val STATE_ERROR = 4
    }
}
