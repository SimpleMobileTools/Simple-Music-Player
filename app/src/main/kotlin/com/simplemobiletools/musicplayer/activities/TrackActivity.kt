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
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.activity_track.*
import kotlinx.android.synthetic.main.item_navigation.view.*
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
        setupTrackInfo(track)
        setupButtons()
        sendIntent(INIT)
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

    private fun setupTrackInfo(track: Song) {
        setupTopArt(track.coverArt)
        activity_track_title.text = track.title
        activity_track_artist.text = track.artist

        activity_track_progressbar.max = track.duration
        activity_track_progress_max.text = track.duration.getFormattedDuration()
    }

    private fun setupButtons() {
        activity_track_previous.setOnClickListener { sendIntent(PREVIOUS) }
        activity_track_play_pause.setOnClickListener { sendIntent(PLAYPAUSE) }
        activity_track_next.setOnClickListener { sendIntent(NEXT) }
    }

    private fun setupTopArt(coverArt: String) {
        val drawable = resources.getDrawable(R.drawable.ic_headset)
        val wantedHeight = resources.getDimension(R.dimen.top_art_height).toInt()
        val placeholder = getResizedDrawable(drawable, wantedHeight)
        placeholder.applyColorFilter(config.textColor)

        val wantedWidth = realScreenSize.x

        val options = RequestOptions()
            .error(placeholder)
            .centerCrop()

        Glide.with(this)
            .load(coverArt)
            .apply(options)
            .override(wantedWidth, wantedHeight)
            .into(findViewById(R.id.activity_track_image))
    }

    private fun getResizedDrawable(drawable: Drawable, wantedHeight: Int): Drawable {
        val bitmap = (drawable as BitmapDrawable).bitmap
        val bitmapResized = Bitmap.createScaledBitmap(bitmap, wantedHeight, wantedHeight, false)
        return BitmapDrawable(resources, bitmapResized)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun progressUpdated(event: Events.ProgressUpdated) {
        activity_track_progress_current.text = event.progress.getFormattedDuration()
        activity_track_progressbar.progress = event.progress
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songStateChanged(event: Events.SongStateChanged) {
        val drawableId = if (event.isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
        activity_track_play_pause.setImageDrawable(resources.getDrawable(drawableId))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun songChangedEvent(event: Events.SongChanged) {
        val song = event.song
        if (song != null) {
            setupTrackInfo(event.song)
        }
    }
}
