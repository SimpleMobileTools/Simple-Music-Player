package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GestureDetectorCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MEDIUM_ALPHA
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isMarshmallowPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.extensions.updatePlayPauseIcon
import com.simplemobiletools.musicplayer.fragments.PlaybackSpeedFragment
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.PlaybackSpeedListener
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_track.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DecimalFormat

class TrackActivity : SimpleActivity(), PlaybackSpeedListener {
    private val SWIPE_DOWN_THRESHOLD = 100

    private var isThirdPartyIntent = false
    private var bus: EventBus? = null
    private lateinit var nextTrackPlaceholder: Drawable

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track)
        nextTrackPlaceholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, getProperTextColor())
        bus = EventBus.getDefault()
        bus!!.register(this)
        setupButtons()
        setupFlingListener()

        (activity_track_appbar.layoutParams as ConstraintLayout.LayoutParams).topMargin = statusBarHeight
        activity_track_holder.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        activity_track_toolbar.setNavigationOnClickListener {
            finish()
        }

        isThirdPartyIntent = intent.action == Intent.ACTION_VIEW
        arrayOf(activity_track_toggle_shuffle, activity_track_previous, activity_track_next, activity_track_playback_setting).forEach {
            it.beInvisibleIf(isThirdPartyIntent)
        }

        if (isThirdPartyIntent) {
            initThirdPartyIntent()
            return
        }

        val trackType = object : TypeToken<Track>() {}.type
        val track = Gson().fromJson<Track>(intent.getStringExtra(TRACK), trackType) ?: MusicService.mCurrTrack
        if (track == null) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }

        setupTrackInfo(track)

        if (intent.getBooleanExtra(RESTART_PLAYER, false)) {
            intent.removeExtra(RESTART_PLAYER)
            Intent(this, MusicService::class.java).apply {
                putExtra(TRACK_ID, track.mediaStoreId)
                action = INIT
                try {
                    startService(this)
                    activity_track_play_pause.updatePlayPauseIcon(true, getProperTextColor())
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            sendIntent(BROADCAST_STATUS)
        }

        next_track_holder.background = ColorDrawable(getProperBackgroundColor())
        next_track_holder.setOnClickListener {
            startActivity(Intent(applicationContext, QueueActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(activity_track_holder)
        activity_track_title.setTextColor(getProperTextColor())
        activity_track_artist.setTextColor(getProperTextColor())
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)

        if (isThirdPartyIntent && !isChangingConfigurations) {
            sendIntent(FINISH_IF_NOT_PLAYING)
        }
    }

    private fun setupTrackInfo(track: Track) {
        setupTopArt(track.coverArt)
        activity_track_title.text = track.title
        activity_track_artist.text = track.artist

        activity_track_title.setOnLongClickListener {
            copyToClipboard(activity_track_title.value)
            true
        }

        activity_track_artist.setOnLongClickListener {
            copyToClipboard(activity_track_artist.value)
            true
        }

        activity_track_progressbar.max = track.duration
        activity_track_progress_max.text = track.duration.getFormattedDuration()
    }

    private fun initThirdPartyIntent() {
        next_track_holder.beGone()
        val fileUri = intent.data
        Intent(this, MusicService::class.java).apply {
            data = fileUri
            action = INIT_PATH

            try {
                startService(this)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun setupButtons() {
        activity_track_toggle_shuffle.setOnClickListener { toggleShuffle() }
        activity_track_previous.setOnClickListener { sendIntent(PREVIOUS) }
        activity_track_play_pause.setOnClickListener { sendIntent(PLAYPAUSE) }
        activity_track_next.setOnClickListener { sendIntent(NEXT) }
        activity_track_progress_current.setOnClickListener { sendIntent(SKIP_BACKWARD) }
        activity_track_progress_max.setOnClickListener { sendIntent(SKIP_FORWARD) }
        activity_track_playback_setting.setOnClickListener { togglePlaybackSetting() }
        activity_track_speed_click_area.setOnClickListener { showPlaybackSpeedPicker() }
        setupShuffleButton()
        setupPlaybackSettingButton()
        setupSeekbar()

        // constraintlayout with textview wrap_content is broken, so we need to use a more complicated way of drawing speed related things
        arrayOf(activity_track_speed_icon, activity_track_speed, activity_track_speed_click_area).forEach {
            it.beVisibleIf(isMarshmallowPlus())
        }

        arrayOf(activity_track_previous, activity_track_play_pause, activity_track_next).forEach {
            it.applyColorFilter(getProperTextColor())
        }
    }

    private fun setupNextTrackInfo(track: Track?) {
        val artist = if (track?.artist?.trim()?.isNotEmpty() == true && track.artist != MediaStore.UNKNOWN_STRING) {
            " • ${track.artist}"
        } else {
            ""
        }

        next_track_label.text = "${getString(R.string.next_track)} ${track?.title}$artist"

        ensureBackgroundThread {
            val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()
            val wantedSize = resources.getDimension(R.dimen.song_image_size).toInt()
            val options = RequestOptions()
                .transform(CenterCrop(), RoundedCorners(cornerRadius))

            try {
                // change cover image manually only once loaded successfully to avoid blinking at fails and placeholders
                Glide.with(this)
                    .load(track?.coverArt)
                    .apply(options)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            runOnUiThread {
                                next_track_image.setImageDrawable(nextTrackPlaceholder)
                            }
                            return true
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any?,
                            target: Target<Drawable>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            next_track_image.setImageDrawable(resource)
                            return false
                        }
                    })
                    .into(wantedSize, wantedSize)
                    .get()
            } catch (e: Exception) {
            }
        }
    }

    private fun setupTopArt(coverArt: String) {
        var wantedHeight = resources.getDimension(R.dimen.top_art_height).toInt()
        wantedHeight = Math.min(wantedHeight, realScreenSize.y / 2)

        ensureBackgroundThread {
            val wantedWidth = realScreenSize.x
            val options = RequestOptions().centerCrop()

            try {
                // change cover image manually only once loaded successfully to avoid blinking at fails and placeholders
                Glide.with(this)
                    .load(coverArt)
                    .apply(options)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            val drawable = resources.getDrawable(R.drawable.ic_headset)
                            val placeholder = getResizedDrawable(drawable, wantedHeight)
                            placeholder.applyColorFilter(getProperTextColor())

                            runOnUiThread {
                                activity_track_image.setImageDrawable(placeholder)
                            }

                            return true
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any?,
                            target: Target<Drawable>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            val coverHeight = resource.intrinsicHeight
                            if (coverHeight > 0 && activity_track_image.height != coverHeight) {
                                activity_track_image.layoutParams.height = coverHeight
                            }

                            activity_track_image.setImageDrawable(resource)
                            return false
                        }
                    })
                    .into(wantedWidth, wantedHeight)
                    .get()
            } catch (e: Exception) {
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFlingListener() {
        val flingListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (velocityY > 0 && velocityY > velocityX && e2.y - e1.y > SWIPE_DOWN_THRESHOLD) {
                    finish()
                    activity_track_top_shadow.animate().alpha(0f).start()
                    overridePendingTransition(0, R.anim.slide_down)
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }
        }

        val gestureDetector = GestureDetectorCompat(this, flingListener)
        activity_track_holder.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun toggleShuffle() {
        val isShuffleEnabled = !config.isShuffleEnabled
        config.isShuffleEnabled = isShuffleEnabled
        toast(if (isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
        setupShuffleButton()
        sendIntent(REFRESH_LIST)
    }

    private fun setupShuffleButton() {
        val isShuffleEnabled = config.isShuffleEnabled
        activity_track_toggle_shuffle.apply {
            applyColorFilter(if (isShuffleEnabled) getProperPrimaryColor() else getProperTextColor())
            alpha = if (isShuffleEnabled) 1f else MEDIUM_ALPHA
            contentDescription = getString(if (isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        }
    }

    private fun togglePlaybackSetting() {
        val newPlaybackSetting = config.playbackSetting.nextPlaybackOption
        config.playbackSetting = newPlaybackSetting

        toast(newPlaybackSetting.descriptionStringRes)

        setupPlaybackSettingButton()
    }

    private fun setupPlaybackSettingButton() {
        val playbackSetting = config.playbackSetting
        activity_track_playback_setting.apply {
            contentDescription = getString(playbackSetting.contentDescriptionStringRes)
            setImageResource(playbackSetting.iconRes)

            val isRepeatOff = playbackSetting == PlaybackSetting.REPEAT_OFF

            alpha = if (isRepeatOff) MEDIUM_ALPHA else 1f
            applyColorFilter(if (isRepeatOff) getProperTextColor() else getProperPrimaryColor())
        }
    }

    private fun setupSeekbar() {
        if (isMarshmallowPlus()) {
            activity_track_speed_icon.applyColorFilter(getProperTextColor())
            updatePlaybackSpeed(config.playbackSpeed)
        }

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

    private fun showPlaybackSpeedPicker() {
        val fragment = PlaybackSpeedFragment()
        fragment.show(supportFragmentManager, PlaybackSpeedFragment::class.java.simpleName)
        fragment.setListener(this)
    }

    override fun updatePlaybackSpeed(speed: Float) {
        val isSlow = speed < 1f
        if (isSlow != activity_track_speed.tag as? Boolean) {
            activity_track_speed.tag = isSlow

            val drawableId = if (isSlow) R.drawable.ic_playback_speed_slow_vector else R.drawable.ic_playback_speed_vector
            activity_track_speed_icon.setImageDrawable(resources.getDrawable(drawableId))
        }

        activity_track_speed.text = "${DecimalFormat("#.##").format(speed)}x"
        sendIntent(SET_PLAYBACK_SPEED)
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
    fun trackStateChanged(event: Events.TrackStateChanged) {
        activity_track_play_pause.updatePlayPauseIcon(event.isPlaying, getProperTextColor())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun trackChangedEvent(event: Events.TrackChanged) {
        val track = event.track
        if (track == null) {
            finish()
        } else {
            setupTrackInfo(event.track)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun nextTrackChangedEvent(event: Events.NextTrackChanged) {
        setupNextTrackInfo(event.track!!)
    }
}
