package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import androidx.annotation.CallSuper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.extensions.openNotificationSettings
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.isPlayingOrBuffering
import com.simplemobiletools.musicplayer.extensions.togglePlayback
import com.simplemobiletools.musicplayer.views.CurrentTrackBar

/**
 * Base class for activities that want to control the player and display a [CurrentTrackBar] at the bottom.
 */
abstract class SimpleMusicActivity : SimpleControllerActivity(), Player.Listener {
    private var trackBarView: CurrentTrackBar? = null

    override fun onResume() {
        super.onResume()
        updateCurrentTrackBar()
    }

    fun setupCurrentTrackBar(trackBar: CurrentTrackBar) {
        trackBarView = trackBar
        trackBarView?.setOnClickListener {
            hideKeyboard()
            handleNotificationPermission { granted ->
                if (granted) {
                    Intent(this, TrackActivity::class.java).apply {
                        startActivity(this)
                    }
                } else {
                    PermissionRequiredDialog(this, R.string.allow_notifications_music_player, { openNotificationSettings() })
                }
            }
        }
    }

    private fun updateCurrentTrackBar() {
        if (trackBarView != null) {
            withPlayer {
                val currentMediaItem = currentMediaItem
                val isPlaying = isPlayingOrBuffering

                runOnUiThread {
                    trackBarView?.initialize {
                        withPlayer { togglePlayback() }
                    }

                    trackBarView?.updateCurrentTrack(currentMediaItem)
                    trackBarView?.updateTrackState(isPlaying)
                }
            }
        }
    }

    @CallSuper
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        trackBarView?.updateCurrentTrack(mediaItem)
    }

    @CallSuper
    override fun onPlaybackStateChanged(playbackState: Int) = withPlayer {
        trackBarView?.updateTrackState(isPlayingOrBuffering)
    }

    @CallSuper
    override fun onIsPlayingChanged(isPlaying: Boolean) = withPlayer {
        trackBarView?.updateTrackState(isPlayingOrBuffering)
    }
}
