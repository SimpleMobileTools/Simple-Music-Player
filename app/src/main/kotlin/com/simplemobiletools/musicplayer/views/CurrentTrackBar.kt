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
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.activity_main.view.*

class CurrentTrackBar(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    fun updateColors() {
        background = ColorDrawable(context.config.backgroundColor)
        current_track_label.setTextColor(context.config.textColor)
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
}
