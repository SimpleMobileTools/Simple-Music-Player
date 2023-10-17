package com.simplemobiletools.musicplayer.playback.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import kotlinx.coroutines.*

private const val DEFAULT_SHUFFLE_ORDER_SEED = 42L

@UnstableApi
class SimpleMusicPlayer(private val exoPlayer: ExoPlayer) : ForwardingPlayer(exoPlayer) {

    private var seekToNextCount = 0
    private var seekToPreviousCount = 0

    private val scope = CoroutineScope(Dispatchers.Default)
    private var seekJob: Job? = null

    /**
     * The default implementation only advertises the seek to next and previous item in the case
     * that it's not the first or last track. We manually advertise that these
     * are available to ensure next/previous buttons are always visible.
     */
    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands()
            .buildUpon()
            .addAll(
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            )
            .build()
    }

    override fun play() {
        playWhenReady = true
    }

    override fun seekTo(positionMs: Long) {
        play()
        super.seekTo(positionMs)
    }

    override fun seekBack() {
        play()
        super.seekBack()
    }

    override fun seekForward() {
        play()
        super.seekForward()
    }

    override fun seekToNext() {
        play()
        if (!maybeForceNext()) {
            seekToNextCount += 1
            seekWithDelay()
        }
    }

    override fun seekToPrevious() {
        play()
        if (!maybeForcePrevious()) {
            seekToPreviousCount += 1
            seekWithDelay()
        }
    }

    override fun seekToNextMediaItem() {
        play()
        if (!maybeForceNext()) {
            seekToNextCount += 1
            seekWithDelay()
        }
    }

    override fun seekToPreviousMediaItem() {
        play()
        if (!maybeForcePrevious()) {
            seekToPreviousCount += 1
            seekWithDelay()
        }
    }

    fun getAudioSessionId(): Int {
        return exoPlayer.audioSessionId
    }

    fun setSkipSilence(skipSilence: Boolean) {
        exoPlayer.skipSilenceEnabled = skipSilence
    }

    /**
     * This is done here only because the player interface doesn't yet support the shuffle order concept: https://github.com/androidx/media/issues/325
     * To ensure the correct item is played, we manually alter the shuffle order here.
     */
    @Deprecated("Should be rewritten when https://github.com/androidx/media/issues/325 is implemented.")
    fun setShuffleIndices(indices: IntArray) {
        val shuffleOrder = DefaultShuffleOrder(indices, DEFAULT_SHUFFLE_ORDER_SEED)
        exoPlayer.setShuffleOrder(shuffleOrder)
    }

    @Deprecated("Should be rewritten when https://github.com/androidx/media/issues/325 is implemented.")
    fun setNextMediaItem(mediaItem: MediaItem) {
        val currentIndex = currentMediaItems.indexOfFirstOrNull { it.mediaId == mediaItem.mediaId }
        if (currentIndex != null) {
            if (shuffleModeEnabled) {
                ensureItemPlaysNext(currentIndex)
            } else {
                moveMediaItem(currentIndex, nextMediaItemIndex)
            }
        } else {
            val newIndex = currentMediaItemIndex + 1
            addMediaItem(newIndex, mediaItem)
            ensureItemPlaysNext(newIndex)
        }
    }

    private fun ensureItemPlaysNext(itemIndex: Int) {
        val nextMediaItemIndex = nextMediaItemIndex
        if (itemIndex != nextMediaItemIndex && shuffleModeEnabled) {
            val shuffledIndices = shuffledMediaItemsIndices.toMutableList()
            val shuffledCurrentIndex = shuffledIndices.indexOf(itemIndex)
            val shuffledNewIndex = shuffledIndices.indexOf(nextMediaItemIndex)
            shuffledIndices.move(currentIndex = shuffledCurrentIndex, newIndex = shuffledNewIndex)
            exoPlayer.setShuffleOrder(DefaultShuffleOrder(shuffledIndices.toIntArray(), DEFAULT_SHUFFLE_ORDER_SEED))
        }
    }

    /**
     * This is here so the player can quickly seek next/previous without doing too much work.
     * It probably won't be needed once https://github.com/androidx/media/issues/81 is resolved.
     */
    private fun seekWithDelay() {
        seekJob?.cancel()
        seekJob = scope.launch {
            delay(timeMillis = 300)
            val seekCount = seekToNextCount - seekToPreviousCount
            if (seekCount != 0) {
                seekByCount(seekCount)
            }
        }
    }

    private fun seekByCount(seekCount: Int) {
        runOnPlayerThread {
            if (currentMediaItem == null) {
                return@runOnPlayerThread
            }

            val currentIndex = currentMediaItemIndex
            val seekIndex = if (shuffleModeEnabled) {
                val shuffledIndex = shuffledMediaItemsIndices.indexOf(currentIndex)
                val seekIndex = rotateIndex(shuffledIndex + seekCount)
                shuffledMediaItemsIndices.getOrNull(seekIndex) ?: return@runOnPlayerThread
            } else {
                rotateIndex(currentIndex + seekCount)
            }

            seekTo(seekIndex, 0)
            seekToNextCount = 0
            seekToPreviousCount = 0
        }
    }

    private fun rotateIndex(index: Int): Int {
        val count = mediaItemCount
        return (index % count + count) % count
    }
}
