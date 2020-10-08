package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.SeekBar
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
import com.simplemobiletools.musicplayer.services.MusicService
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
        activity_track_toggle_shuffle.setOnClickListener { toggleShuffle() }
        activity_track_previous.setOnClickListener { sendIntent(PREVIOUS) }
        activity_track_play_pause.setOnClickListener { sendIntent(PLAYPAUSE) }
        activity_track_next.setOnClickListener { sendIntent(NEXT) }
        activity_track_repeat.setOnClickListener { toggleSongRepetition() }
        setupShuffleButton()
        setupSongRepetitionButton()
        setupSeekbar()
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

    private fun toggleShuffle() {
        val isShuffleEnabled = !config.isShuffleEnabled
        config.isShuffleEnabled = isShuffleEnabled
        toast(if (isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
        setupShuffleButton()
    }

    private fun setupShuffleButton() {
        val isShuffleEnabled = config.isShuffleEnabled
        activity_track_toggle_shuffle.apply {
            applyColorFilter(if (isShuffleEnabled) getAdjustedPrimaryColor() else config.textColor)
            alpha = if (isShuffleEnabled) 1f else LOWER_ALPHA
            contentDescription = getString(if (isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        }
    }

    private fun toggleSongRepetition() {
        val repeatSong = !config.repeatSong
        config.repeatSong = repeatSong
        toast(if (repeatSong) R.string.song_repetition_enabled else R.string.song_repetition_disabled)
        setupSongRepetitionButton()
    }

    private fun setupSongRepetitionButton() {
        val repeatSong = config.repeatSong
        activity_track_repeat.apply {
            applyColorFilter(if (repeatSong) getAdjustedPrimaryColor() else config.textColor)
            alpha = if (repeatSong) 1f else LOWER_ALPHA
            contentDescription = getString(if (repeatSong) R.string.disable_song_repetition else R.string.enable_song_repetition)
        }
    }

    private fun setupSeekbar() {
        activity_track_progressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val formattedProgress = progress.getFormattedDuration()
                activity_track_progress_current.text = formattedProgress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Intent(this@TrackActivity, MusicService::class.java).apply {
                    putExtra(PROGRESS, seekBar.progress)
                    action = SET_PROGRESS
                    startService(this)
                }
            }
        })
    }

    private fun getResizedDrawable(drawable: Drawable, wantedHeight: Int): Drawable {
        val bitmap = (drawable as BitmapDrawable).bitmap
        val bitmapResized = Bitmap.createScaledBitmap(bitmap, wantedHeight, wantedHeight, false)
        return BitmapDrawable(resources, bitmapResized)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun progressUpdated(event: Events.ProgressUpdated) {
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
        if (song == null) {
            finish()
        } else {
            setupTrackInfo(event.song)
        }
    }
}
