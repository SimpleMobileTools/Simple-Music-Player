package com.simplemobiletools.musicplayer.views

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.PLAYPAUSE
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.view_current_track_bar.view.*

class CurrentTrackBar(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    fun updateColors() {
        background = ColorDrawable(context.config.backgroundColor)
        current_track_label.setTextColor(context.config.textColor)
        current_track_play_pause.setOnClickListener {
            context.sendIntent(PLAYPAUSE)
        }
    }

    fun updateCurrentTrack(track: Track?) {
        if (track == null) {
            beGone()
            return
        } else {
            beVisible()
        }

        val artist = if (track.artist.trim().isNotEmpty() && track.artist != MediaStore.UNKNOWN_STRING) {
            " • ${track.artist}"
        } else {
            ""
        }

        current_track_label.text = "${track.title}$artist"
        val currentTrackPlaceholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, context.config.textColor)
        val options = RequestOptions()
            .error(currentTrackPlaceholder)
            .transform(CenterCrop(), RoundedCorners(8))

        Glide.with(this)
            .load(track.coverArt)
            .apply(options)
            .into(findViewById(R.id.current_track_image))
    }

    fun updateTrackState(isPlaying: Boolean) {
        val drawableId = if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
        val drawable = context.resources.getColoredDrawableWithColor(drawableId, context.config.textColor)
        current_track_play_pause.setImageDrawable(drawable)
    }
}
