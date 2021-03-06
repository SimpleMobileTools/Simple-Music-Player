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
            playback_speed_label.text = "1.00x"

            playback_speed_seekbar.onSeekBarChangeListener {
                listener?.updatePlaybackSpeed(it.toFloat())
            }
        }

        return view
    }

    fun setListener(playbackSpeedListener: PlaybackSpeedListener) {
        listener = playbackSpeedListener
    }
}
