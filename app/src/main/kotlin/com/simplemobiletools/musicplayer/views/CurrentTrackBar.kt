package com.simplemobiletools.musicplayer.views

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.ensureActivityNotDestroyed
import com.simplemobiletools.musicplayer.extensions.getTrackCoverArt
import com.simplemobiletools.musicplayer.extensions.toTrack
import com.simplemobiletools.musicplayer.extensions.updatePlayPauseIcon
import kotlinx.android.synthetic.main.view_current_track_bar.view.current_track_label
import kotlinx.android.synthetic.main.view_current_track_bar.view.current_track_play_pause

class CurrentTrackBar(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {

    fun initialize(togglePlayback: () -> Unit) {
        background = ColorDrawable(context.getProperBackgroundColor())
        current_track_label.setTextColor(context.getProperTextColor())
        current_track_play_pause.setOnClickListener {
            togglePlayback()
        }
    }

    fun updateCurrentTrack(mediaItem: MediaItem?) {
        val track = mediaItem?.toTrack()
        if (track == null) {
            fadeOut()
            return
        } else {
            fadeIn()
        }

        val artist = if (track.artist.trim().isNotEmpty() && track.artist != MediaStore.UNKNOWN_STRING) {
            " • ${track.artist}"
        } else {
            ""
        }

        @SuppressLint("SetTextI18n")
        current_track_label.text = "${track.title}$artist"
        val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()
        val currentTrackPlaceholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, context.getProperTextColor())
        val options = RequestOptions()
            .error(currentTrackPlaceholder)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))

        context.getTrackCoverArt(track) { coverArt ->
            (context as? Activity)?.ensureActivityNotDestroyed {
                Glide.with(this)
                    .load(coverArt)
                    .apply(options)
                    .into(findViewById(R.id.current_track_image))
            }
        }
    }

    fun updateTrackState(isPlaying: Boolean) {
        current_track_play_pause.updatePlayPauseIcon(isPlaying, context.getProperTextColor())
    }
}
