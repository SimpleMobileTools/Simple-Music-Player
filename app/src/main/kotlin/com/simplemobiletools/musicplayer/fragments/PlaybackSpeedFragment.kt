package com.simplemobiletools.musicplayer.fragments

import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.onSeekBarChangeListener
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.interfaces.PlaybackSpeedListener
import kotlinx.android.synthetic.main.fragment_playback_speed.view.*

class PlaybackSpeedFragment : BottomSheetDialogFragment() {
    private val MIN_PLAYBACK_SPEED = 0.25f
    private val MAX_PLAYBACK_SPEED = 3f
    private val STEP = 0.05f

    private var listener: PlaybackSpeedListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val config = context!!.config
        val view = inflater.inflate(R.layout.fragment_playback_speed, container, false)
        val background = context!!.resources.getDrawable(R.drawable.bottom_sheet_bg)
        (background as LayerDrawable).findDrawableByLayerId(R.id.bottom_sheet_background).applyColorFilter(config.backgroundColor)

        view.apply {
            setBackgroundDrawable(background)
            context!!.updateTextColors(playback_speed_holder)
            playback_speed_slow.applyColorFilter(config.textColor)
            playback_speed_fast.applyColorFilter(config.textColor)

            val formattedValue = formatPlaybackSpeed(config.playbackSpeed)
            playback_speed_label.text = "${formattedValue}x"

            val maxProgress = (MAX_PLAYBACK_SPEED * 100 + MIN_PLAYBACK_SPEED * 100).toInt()
            val halfProgress = maxProgress / 2
            playback_speed_seekbar.max = maxProgress

            val playbackSpeedProgress = config.playbackSpeedProgress
            if (playbackSpeedProgress == -1) {
                config.playbackSpeedProgress = halfProgress
            }
            playback_speed_seekbar.progress = config.playbackSpeedProgress

            var lastUpdatedProgress = config.playbackSpeedProgress
            var lastUpdatedFormattedValue = formattedValue

            playback_speed_seekbar.onSeekBarChangeListener { progress ->
                val playbackSpeed = getPlaybackSpeed(progress, halfProgress)

                val stepMultiplier = 1 / STEP
                val rounded = Math.round(playbackSpeed * stepMultiplier) / stepMultiplier
                if (rounded.toString() != lastUpdatedFormattedValue) {
                    lastUpdatedProgress = progress
                    lastUpdatedFormattedValue = rounded.toString()
                    config.playbackSpeed = rounded
                    config.playbackSpeedProgress = progress

                    playback_speed_label.text = "${formatPlaybackSpeed(rounded)}x"
                    listener?.updatePlaybackSpeed(rounded)
                } else {
                    playback_speed_seekbar.progress = lastUpdatedProgress
                }
            }
        }

        return view
    }

    private fun getPlaybackSpeed(progress: Int, halfProgress: Int): Float {
        var playbackSpeed = when {
            progress < halfProgress -> {
                val lowerProgressPercent = progress / halfProgress.toFloat()
                val lowerProgress = (1 - MIN_PLAYBACK_SPEED) * lowerProgressPercent + MIN_PLAYBACK_SPEED
                lowerProgress
            }
            progress > halfProgress -> {
                val upperProgressPercent = progress / halfProgress.toFloat() - 1
                val upperDiff = MAX_PLAYBACK_SPEED - 1
                upperDiff * upperProgressPercent + 1
            }
            else -> 1f
        }
        playbackSpeed = Math.min(Math.max(playbackSpeed, MIN_PLAYBACK_SPEED), MAX_PLAYBACK_SPEED)
        return playbackSpeed
    }

    private fun formatPlaybackSpeed(value: Float) = String.format("%.2f", value)

    fun setListener(playbackSpeedListener: PlaybackSpeedListener) {
        listener = playbackSpeedListener
    }
}
