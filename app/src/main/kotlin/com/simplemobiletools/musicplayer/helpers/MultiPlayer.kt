package com.simplemobiletools.musicplayer.helpers

import android.app.Application
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.receivers.HeadsetPlugReceiver

class MultiPlayer(private val app: Application, private val callbacks: PlaybackCallbacks) : MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    private var mCurrentMediaPlayer = MediaPlayer()
    private var mNextMediaPlayer: MediaPlayer? = null

    var isInitialized: Boolean = false

    private var becomingNoisyReceiverRegistered = false
    private val becomingNoisyReceiver = HeadsetPlugReceiver()
    private val becomingNoisyReceiverIntentFilter = IntentFilter(AudioManager.ACTION_HEADSET_PLUG).apply {
        addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    }

    private val audioManager: AudioManager? = app.getSystemService()
    private var isPausedByTransientLossOfFocus = false
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!isPlaying() && isPausedByTransientLossOfFocus) {
                    start()
                    callbacks.onPlayStateChanged()
                    isPausedByTransientLossOfFocus = false
                }
                setVolume(Volume.NORMAL)
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
                callbacks.onPlayStateChanged()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val wasPlaying = isPlaying()
                pause()
                callbacks.onPlayStateChanged()
                isPausedByTransientLossOfFocus = wasPlaying
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                setVolume(Volume.DUCK)
            }
        }
    }

    private var audioFocusRequest: AudioFocusRequestCompat? = null

    init {
        mCurrentMediaPlayer.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
    }

    fun getAudioSessionId(): Int = mCurrentMediaPlayer.audioSessionId

    fun isPlaying(): Boolean = isInitialized && mCurrentMediaPlayer.isPlaying

    fun setDataSource(trackUri: Uri) {
        isInitialized = false
        setDataSourceImpl(mCurrentMediaPlayer, trackUri) { result ->
            if (result.isFailure) {
                throw result.exceptionOrNull()!!
            }
            isInitialized = true
            setNextDataSource(null)
            callbacks.onPrepared()
        }
    }

    fun setNextDataSource(trackUri: Uri?, onPrepared: (() -> Unit)? = null) {
        try {
            mCurrentMediaPlayer.setNextMediaPlayer(null)
        } catch (e: IllegalArgumentException) {
            // Next media player is current one, continuing
        } catch (e: IllegalStateException) {
            return
        }
        releaseNextPlayer()
        if (trackUri == null) {
            return
        }
        if (app.config.gaplessPlayback) {
            mNextMediaPlayer = MediaPlayer()
            mNextMediaPlayer!!.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            mNextMediaPlayer!!.audioSessionId = mCurrentMediaPlayer.audioSessionId
            setDataSourceImpl(mNextMediaPlayer!!, trackUri) { result ->
                if (result.isSuccess) {
                    try {
                        mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer)
                        onPrepared?.invoke()
                    } catch (e: IllegalArgumentException) {
                        releaseNextPlayer()
                    } catch (e: IllegalStateException) {
                        releaseNextPlayer()
                    }
                } else {
                    releaseNextPlayer()
                    throw result.exceptionOrNull()!!
                }
            }
        }
    }

    fun start(): Boolean {
        requestFocus()
        registerBecomingNoisyReceiver()
        return try {
            mCurrentMediaPlayer.start()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    fun stop() {
        abandonFocus()
        unregisterBecomingNoisyReceiver()
        mCurrentMediaPlayer.reset()
        isInitialized = false
    }

    fun release() {
        stop()
        mCurrentMediaPlayer.release()
        mNextMediaPlayer?.release()
    }

    fun pause(): Boolean {
        unregisterBecomingNoisyReceiver()
        return try {
            mCurrentMediaPlayer.pause()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    fun duration(): Int {
        return if (!this.isInitialized) {
            -1
        } else try {
            mCurrentMediaPlayer.duration
        } catch (e: IllegalStateException) {
            -1
        }
    }

    fun position(): Int {
        return if (!this.isInitialized) {
            -1
        } else try {
            mCurrentMediaPlayer.currentPosition
        } catch (e: IllegalStateException) {
            -1
        }
    }

    fun seek(whereto: Int): Int {
        return try {
            mCurrentMediaPlayer.seekTo(whereto)
            whereto
        } catch (e: IllegalStateException) {
            -1
        }
    }

    fun setVolume(vol: Float): Boolean {
        return try {
            mCurrentMediaPlayer.setVolume(vol, vol)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        isInitialized = false
        mCurrentMediaPlayer.reset()
        return false
    }

    override fun onCompletion(mp: MediaPlayer) {
        if (app.config.gaplessPlayback && mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
            isInitialized = false
            mCurrentMediaPlayer.reset()
            mCurrentMediaPlayer.release()
            mCurrentMediaPlayer = mNextMediaPlayer!!
            isInitialized = true
            mNextMediaPlayer = null
            callbacks.onTrackWentToNext()
        } else {
            callbacks.onTrackEnded()
        }
    }

    private fun setDataSourceImpl(player: MediaPlayer, trackUri: Uri, onPrepared: (success: Result<Boolean>) -> Unit) {
        player.reset()
        try {
            player.setDataSource(app, trackUri)
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            try {
                player.playbackParams = player.playbackParams.setSpeed(app.config.playbackSpeed)
            } catch (ignored: Exception) {
            }

            player.setOnPreparedListener {
                player.setOnPreparedListener(null)
                onPrepared(Result.success(true))
            }
            player.prepareAsync()
        } catch (e: Exception) {
            onPrepared(Result.failure(e))
            e.printStackTrace()
        }
        player.setOnCompletionListener(this)
        player.setOnErrorListener(this)
    }

    private fun releaseNextPlayer() {
        mNextMediaPlayer?.release()
        mNextMediaPlayer = null
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (becomingNoisyReceiverRegistered) {
            app.unregisterReceiver(becomingNoisyReceiver)
            becomingNoisyReceiverRegistered = false
        }
    }

    private fun registerBecomingNoisyReceiver() {
        if (!becomingNoisyReceiverRegistered) {
            app.registerReceiver(
                becomingNoisyReceiver, becomingNoisyReceiverIntentFilter
            )
            becomingNoisyReceiverRegistered = true
        }
    }

    private fun getAudioFocusRequest(): AudioFocusRequestCompat {
        if (audioFocusRequest == null) {
            val audioAttributes = AudioAttributesCompat.Builder()
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setAudioAttributes(audioAttributes)
                .build()
        }
        return audioFocusRequest!!
    }

    private fun requestFocus(): Boolean {
        return AudioManagerCompat.requestAudioFocus(audioManager!!, getAudioFocusRequest()) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, getAudioFocusRequest())
    }

    fun setPlaybackSpeed(speed: Float) {
        mCurrentMediaPlayer.playbackParams = mCurrentMediaPlayer.playbackParams.setSpeed(speed)
    }

    interface PlaybackCallbacks {
        fun onPrepared()

        fun onTrackEnded()

        fun onTrackWentToNext()

        fun onPlayStateChanged()
    }

    object Volume {
        const val DUCK = 0.2f
        const val NORMAL = 1.0f
    }
}
