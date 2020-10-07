package com.simplemobiletools.musicplayer.activities

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.TRACK
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.activity_track.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class TrackActivity : SimpleActivity() {
    private var bus: EventBus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track)
        bus = EventBus.getDefault()
        bus!!.register(this)

        (activity_track_appbar.layoutParams as ConstraintLayout.LayoutParams).topMargin = statusBarHeight
        activity_track_holder.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        activity_track_toolbar.setNavigationOnClickListener {
            finish()
        }

        val trackType = object : TypeToken<Song>() {}.type
        val track = Gson().fromJson<Song>(intent.getStringExtra(TRACK), trackType)
        setupTopArt(track.coverArt)

        activity_track_title.text = track.title
        activity_track_artist.text = track.artist

        activity_track_progressbar.max = track.duration
        activity_track_progress_max.text = track.duration.getFormattedDuration()
    }

    override fun onResume() {
        super.onResume()
        window.statusBarColor = Color.TRANSPARENT
        activity_track_holder.setBackgroundColor(config.backgroundColor)
        updateTextColors(activity_track_holder)
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupTopArt(coverArt: String) {
        val drawable = resources.getDrawable(R.drawable.ic_headset)
        val placeholder = getResizedDrawable(drawable)
        placeholder.applyColorFilter(config.textColor)

        val wantedWidth = realScreenSize.x
        val wantedHeight = if (portrait) realScreenSize.x else resources.getDimension(R.dimen.top_art_height_landscape).toInt()

        val options = RequestOptions()
            .error(placeholder)
            .centerCrop()

        Glide.with(this)
            .load(coverArt)
            .apply(options)
            .override(wantedWidth, wantedHeight)
            .into(findViewById(R.id.activity_track_image))
    }

    private fun getResizedDrawable(drawable: Drawable): Drawable {
        val dimension = if (portrait) R.dimen.top_art_height else R.dimen.top_art_height_landscape
        val size = resources.getDimension(dimension).toInt()
        val bitmap = (drawable as BitmapDrawable).bitmap
        val bitmapResized = Bitmap.createScaledBitmap(bitmap, size, size, false)
        return BitmapDrawable(resources, bitmapResized)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun progressUpdated(event: Events.ProgressUpdated) {
        activity_track_progress_current.text = event.progress.getFormattedDuration()
        activity_track_progressbar.progress = event.progress
    }
}
