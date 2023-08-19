package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.postDelayed
import androidx.core.view.GestureDetectorCompat
import androidx.media3.common.MediaItem
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MEDIUM_ALPHA
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.fragments.PlaybackSpeedFragment
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.PlaybackSpeedListener
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.playback.CustomCommands
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import kotlinx.android.synthetic.main.activity_track.*
import java.text.DecimalFormat
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class TrackActivity : SimpleControllerActivity(), PlaybackSpeedListener {
    private val SWIPE_DOWN_THRESHOLD = 100

    private var isThirdPartyIntent = false
    private lateinit var nextTrackPlaceholder: Drawable

    private val handler = Handler(Looper.getMainLooper())
    private val updateIntervalMillis = 500L

    override fun onCreate(savedInstanceState: Bundle?) {
        showTransparentTop = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track)
        nextTrackPlaceholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, getProperTextColor())
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

        setupTrackInfo(PlaybackService.currentMediaItem)
        setupNextTrackInfo(PlaybackService.nextMediaItem)
        activity_track_play_pause.updatePlayPauseIcon(PlaybackService.isPlaying, getProperTextColor())
        updatePlayerState()

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
        updatePlayerState()
        updateTrackInfo()
    }

    override fun onPause() {
        super.onPause()
        cancelProgressUpdate()
    }

    override fun onStop() {
        super.onStop()
        cancelProgressUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelProgressUpdate()
        if (isThirdPartyIntent && !isChangingConfigurations) {
            withPlayer {
                if (!isPlayingOrBuffering) {
                    sendCommand(CustomCommands.CLOSE_PLAYER)
                }
            }
        }
    }

    private fun setupTrackInfo(item: MediaItem?) {
        val track = item?.toTrack() ?: return

        setupTopArt(track)
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
        getMediaItemFromUri(intent.data) {
            runOnUiThread {
                playMediaItems(listOf(it))
            }
        }
    }

    private fun setupButtons() {
        activity_track_toggle_shuffle.setOnClickListener { withPlayer { toggleShuffle() } }
        activity_track_previous.setOnClickListener { withPlayer { seekToPreviousMediaItem() } }
        activity_track_play_pause.setOnClickListener { withPlayer { togglePlayback() } }
        activity_track_next.setOnClickListener { withPlayer { seekToNextMediaItem() } }
        activity_track_progress_current.setOnClickListener { seekBack() }
        activity_track_progress_max.setOnClickListener { seekForward() }
        activity_track_playback_setting.setOnClickListener { togglePlaybackSetting() }
        activity_track_speed_click_area.setOnClickListener { showPlaybackSpeedPicker() }
        setupShuffleButton()
        setupPlaybackSettingButton()
        setupSeekbar()

        arrayOf(activity_track_previous, activity_track_play_pause, activity_track_next).forEach {
            it.applyColorFilter(getProperTextColor())
        }
    }

    private fun setupNextTrackInfo(item: MediaItem?) {
        val track = item?.toTrack()
        if (track == null) {
            next_track_holder.beGone()
            return
        }

        next_track_holder.beVisible()
        val artist = if (track.artist.trim().isNotEmpty() && track.artist != MediaStore.UNKNOWN_STRING) {
            " • ${track.artist}"
        } else {
            ""
        }

        @SuppressLint("SetTextI18n")
        next_track_label.text = "${getString(R.string.next_track)} ${track.title}$artist"

        getTrackCoverArt(track) { coverArt ->
            val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()
            val wantedSize = resources.getDimension(R.dimen.song_image_size).toInt()

            // change cover image manually only once loaded successfully to avoid blinking at fails and placeholders
            loadGlideResource(
                model = coverArt,
                options = RequestOptions().transform(CenterCrop(), RoundedCorners(cornerRadius)),
                size = Size(wantedSize, wantedSize),
                onLoadFailed = {
                    runOnUiThread {
                        next_track_image.setImageDrawable(nextTrackPlaceholder)
                    }
                },
                onResourceReady = {
                    runOnUiThread {
                        next_track_image.setImageDrawable(it)
                    }
                }
            )
        }
    }

    private fun setupTopArt(track: Track) {
        getTrackCoverArt(track) { coverArt ->
            var wantedHeight = resources.getCoverArtHeight()
            wantedHeight = min(wantedHeight, realScreenSize.y / 2)
            val wantedWidth = realScreenSize.x

            // change cover image manually only once loaded successfully to avoid blinking at fails and placeholders
            loadGlideResource(
                model = coverArt,
                options = RequestOptions().centerCrop(),
                size = Size(wantedWidth, wantedHeight),
                onLoadFailed = {
                    val drawable = resources.getDrawable(R.drawable.ic_headset)
                    val placeholder = getResizedDrawable(drawable, wantedHeight)
                    placeholder.applyColorFilter(getProperTextColor())

                    runOnUiThread {
                        activity_track_image.setImageDrawable(placeholder)
                    }
                },
                onResourceReady = {
                    val coverHeight = it.intrinsicHeight
                    if (coverHeight > 0 && activity_track_image.height != coverHeight) {
                        activity_track_image.layoutParams.height = coverHeight
                    }

                    runOnUiThread {
                        activity_track_image.setImageDrawable(it)
                    }
                }
            )
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
        withPlayer {
            shuffleModeEnabled = config.isShuffleEnabled
            runOnUiThread {
                setupNextTrackInfo(nextMediaItem)
            }
        }
    }

    private fun setupShuffleButton() {
        val isShuffleEnabled = config.isShuffleEnabled
        activity_track_toggle_shuffle.apply {
            applyColorFilter(if (isShuffleEnabled) getProperPrimaryColor() else getProperTextColor())
            alpha = if (isShuffleEnabled) 1f else MEDIUM_ALPHA
            contentDescription = getString(if (isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        }
    }

    private fun seekBack() {
        activity_track_progressbar.progress += -SEEK_INTERVAL_S
        withPlayer { seekBack() }
    }

    private fun seekForward() {
        activity_track_progressbar.progress += SEEK_INTERVAL_S
        withPlayer { seekForward() }
    }

    private fun togglePlaybackSetting() {
        val newPlaybackSetting = config.playbackSetting.nextPlaybackOption
        config.playbackSetting = newPlaybackSetting
        toast(newPlaybackSetting.descriptionStringRes)
        setupPlaybackSettingButton()
        withPlayer {
            setRepeatMode(config.playbackSetting)
            val nextMediaItem = nextMediaItem
            runOnUiThread {
                setupNextTrackInfo(nextMediaItem)
            }
        }
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
        activity_track_speed_icon.applyColorFilter(getProperTextColor())
        updatePlaybackSpeed(config.playbackSpeed)

        activity_track_progressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val formattedProgress = progress.getFormattedDuration()
                activity_track_progress_current.text = formattedProgress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) = withPlayer {
                seekTo(seekBar.progress * 1000L)
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

        @SuppressLint("SetTextI18n")
        activity_track_speed.text = "${DecimalFormat("#.##").format(speed)}x"
        withPlayer {
            setPlaybackSpeed(speed)
        }
    }

    private fun getResizedDrawable(drawable: Drawable, wantedHeight: Int): Drawable {
        val bitmap = (drawable as BitmapDrawable).bitmap
        val bitmapResized = Bitmap.createScaledBitmap(bitmap, wantedHeight, wantedHeight, false)
        return BitmapDrawable(resources, bitmapResized)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        super.onPlaybackStateChanged(playbackState)
        updatePlayerState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        updatePlayerState()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        if (mediaItem == null) {
            finish()
        } else {
            activity_track_progressbar.progress = 0
            updateTrackInfo()
        }
    }

    private fun updateTrackInfo() {
        withPlayer {
            val currentMediaItem = currentMediaItem
            val nextMediaItem = nextMediaItem
            runOnUiThread {
                setupTrackInfo(currentMediaItem)
                setupNextTrackInfo(nextMediaItem)
            }
        }
    }

    private fun updatePlayerState() {
        withPlayer {
            val isPlaying = isPlayingOrBuffering
            runOnUiThread {
                activity_track_play_pause.updatePlayPauseIcon(isPlaying, getProperTextColor())
                updateProgress(currentPosition)
                if (isPlaying) {
                    scheduleProgressUpdate()
                } else {
                    cancelProgressUpdate()
                }
            }
        }
    }

    private fun scheduleProgressUpdate() {
        cancelProgressUpdate()
        withPlayer {
            val delayInMillis = (updateIntervalMillis / config.playbackSpeed).toLong()
            handler.postDelayed(delayInMillis) {
                updateProgress(currentPosition)
                scheduleProgressUpdate()
            }
        }
    }

    private fun cancelProgressUpdate() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateProgress(currentPosition: Long) {
        activity_track_progressbar.progress = currentPosition.milliseconds.inWholeSeconds.toInt()
    }
}
