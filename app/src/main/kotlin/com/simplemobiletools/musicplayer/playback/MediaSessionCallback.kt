package com.simplemobiletools.musicplayer.playback

import android.os.Bundle
import android.os.ConditionVariable
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.simplemobiletools.musicplayer.extensions.addRemainingMediaItems
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.EXTRA_NEXT_MEDIA_ID
import com.simplemobiletools.musicplayer.helpers.EXTRA_SHUFFLE_INDICES
import com.simplemobiletools.musicplayer.playback.player.updatePlaybackState
import java.util.concurrent.Executors

@UnstableApi
internal fun PlaybackService.getMediaSessionCallback() = object : MediaLibrarySession.Callback {
    private val browsers = mutableMapOf<MediaSession.ControllerInfo, String>()
    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4))
    }

    private fun <T> callWhenSourceReady(action: () -> T): ListenableFuture<T> {
        val conditionVariable = ConditionVariable()
        return if (mediaItemProvider.whenReady { conditionVariable.open() }) {
            executorService.submit<T> {
                action()
            }
        } else {
            executorService.submit<T> {
                conditionVariable.block()
                action()
            }
        }
    }

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
        for (command in customCommands) {
            availableSessionCommands.add(command)
        }

        return MediaSession.ConnectionResult.accept(
            availableSessionCommands.build(),
            connectionResult.availablePlayerCommands
        )
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        val customLayout = getCustomLayout()
        if (customLayout.isNotEmpty() && controller.controllerVersion != 0) {
            mediaSession.setCustomLayout(controller, customLayout)
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        val command = CustomCommands.fromSessionCommand(customCommand)
            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))

        when (command) {
            CustomCommands.CLOSE_PLAYER -> stopService()
            CustomCommands.RELOAD_CONTENT -> reloadContent()
            CustomCommands.TOGGLE_SLEEP_TIMER -> toggleSleepTimer()
            CustomCommands.TOGGLE_SKIP_SILENCE -> player.setSkipSilence(config.gaplessPlayback)
            CustomCommands.SET_SHUFFLE_ORDER -> setShuffleOrder(args)
            CustomCommands.SET_NEXT_ITEM -> setNextItem(args)
        }

        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        if (params != null && params.isRecent) {
            // The service currently does not support recent playback. Tell System UI by returning
            // an error of type 'RESULT_ERROR_NOT_SUPPORTED' for a `params.isRecent` request. See
            // https://github.com/androidx/media/issues/355
            return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        return Futures.immediateFuture(
            LibraryResult.ofItem(
                mediaItemProvider.getRootItem(),
                params
            )
        )
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ) = callWhenSourceReady {
        currentRoot = parentId
        val children = mediaItemProvider.getChildren(parentId)
            ?: return@callWhenSourceReady LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)

        LibraryResult.ofItemList(children, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ) = callWhenSourceReady {
        val item = mediaItemProvider[mediaId]
            ?: return@callWhenSourceReady LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)

        LibraryResult.ofItem(item, null)
    }

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: MediaLibraryService.LibraryParams?
    ) = callWhenSourceReady {
        val children = mediaItemProvider.getChildren(parentId)
            ?: return@callWhenSourceReady LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)

        browsers[browser] = parentId
        session.notifyChildrenChanged(browser, parentId, children.size, params)
        LibraryResult.ofVoid()
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val settableFuture = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        executorService.execute {
            var futureSet = false
            mediaItemProvider.getRecentItemsLazily {
                // resume playback as quickly as possible: https://github.com/androidx/media/issues/111
                if (!futureSet) {
                    settableFuture.set(it)
                    futureSet = true
                } else {
                    player.addRemainingMediaItems(it.mediaItems, it.startIndex)
                }
            }
        }

        return settableFuture
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        if (controller.packageName == packageName) {
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }

        // this is to avoid single items in the queue: https://github.com/androidx/media/issues/156
        var queueItems = mediaItems
        val startItemId = mediaItems[0].mediaId
        val currentItems = mediaItemProvider.getChildren(currentRoot).orEmpty()

        queueItems = if (currentItems.any { it.mediaId == startItemId }) {
            currentItems.toMutableList()
        } else {
            mediaItemProvider.getDefaultQueue()?.toMutableList() ?: queueItems
        }

        val startItemIndex = queueItems.indexOfFirst { it.mediaId == startItemId }
        return super.onSetMediaItems(mediaSession, controller, queueItems, startItemIndex, startPositionMs)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val items = mediaItems.map { mediaItem ->
            if (mediaItem.requestMetadata.searchQuery != null) {
                getMediaItemFromSearchQuery(mediaItem.requestMetadata.searchQuery!!)
            } else {
                mediaItemProvider[mediaItem.mediaId] ?: mediaItem
            }
        }

        return Futures.immediateFuture(items)
    }

    private fun getMediaItemFromSearchQuery(query: String): MediaItem {
        return mediaItemProvider.getItemFromSearch(query.lowercase()) ?: mediaItemProvider.getRandomItem()
    }

    private fun reloadContent() {
        mediaItemProvider.reload()
        mediaItemProvider.whenReady {
            val rootItem = mediaItemProvider.getRootItem()
            val rootItemCount = mediaItemProvider.getChildren(rootItem.mediaId)?.size ?: 0

            executorService.execute {
                browsers.forEach { (browser, parentId) ->
                    val itemCount = mediaItemProvider.getChildren(parentId)?.size ?: 0
                    mediaSession.notifyChildrenChanged(browser, parentId, itemCount, null)
                    mediaSession.notifyChildrenChanged(browser, rootItem.mediaId, rootItemCount, null)
                }
            }
        }
    }

    private fun setShuffleOrder(args: Bundle) {
        val indices = args.getIntArray(EXTRA_SHUFFLE_INDICES) ?: return
        withPlayer {
            setShuffleIndices(indices)
        }
    }

    private fun setNextItem(args: Bundle) {
        val mediaId = args.getString(EXTRA_NEXT_MEDIA_ID) ?: return
        callWhenSourceReady {
            val mediaItem = mediaItemProvider[mediaId] ?: return@callWhenSourceReady
            withPlayer {
                setNextMediaItem(mediaItem)
                updatePlaybackState()
            }
        }
    }
}
