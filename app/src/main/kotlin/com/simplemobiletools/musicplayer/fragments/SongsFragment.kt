package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Environment
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.SeekBar
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.interfaces.RecyclerScrollCallback
import com.simplemobiletools.commons.views.MyLinearLayoutManager
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.adapters.OldSongAdapter
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getActionBarHeight
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.interfaces.MainActivityInterface
import com.simplemobiletools.musicplayer.interfaces.SongListListener
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_songs.view.*
import kotlinx.android.synthetic.main.item_navigation.view.*
import java.util.*

class SongsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), SongListListener {
    private var songs = ArrayList<Track>()
    private var artView: ViewGroup? = null
    private var actionbarSize = 0
    private var topArtHeight = 0
    private val config = context.config

    private var storedTextColor = 0
    private var storedShowAlbumCover = true

    private lateinit var activity: SimpleActivity
    private lateinit var activityInterface: MainActivityInterface

    fun onResume() {
        setupIconColors()
        setupIconDescriptions()
        markCurrentSong()

        getSongsAdapter()?.updateColors()
        songs_playlist_empty_placeholder_2.setTextColor(activity.getAdjustedPrimaryColor())
        songs_playlist_empty_placeholder_2.paintFlags = songs_playlist_empty_placeholder_2.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        songs_fastscroller.updateBubbleColors()

        arrayListOf(art_holder, song_list_background, top_navigation).forEach {
            it.background = ColorDrawable(config.backgroundColor)
        }

        if (storedTextColor != config.textColor) {
            updateAlbumCover()
        }

        if (storedShowAlbumCover != config.showAlbumCover) {
            setTopArtHeight()
            songs_list.adapter?.notifyDataSetChanged()
            if (config.showAlbumCover) {
                updateAlbumCover()
            } else {
                try {
                    art_image.setImageDrawable(null)
                } catch (ignored: Exception) {
                }
            }
        }
    }

    fun onPause() {
        storeStateVariables()
    }

    private fun storeStateVariables() {
        config.apply {
            storedTextColor = textColor
            storedShowAlbumCover = showAlbumCover
        }
    }

    override fun setupFragment(simpleActivity: SimpleActivity) {
        storeStateVariables()
        activity = simpleActivity
        activityInterface = simpleActivity as MainActivityInterface
        actionbarSize = activity.getActionBarHeight()

        artView = activity.layoutInflater.inflate(R.layout.item_transparent, null) as ViewGroup
        songs_fastscroller.measureItemIndex = LIST_HEADERS_COUNT

        shuffle_btn.setOnClickListener { toggleShuffle() }
        previous_btn.setOnClickListener { activity.sendIntent(PREVIOUS) }
        play_pause_btn.setOnClickListener { activity.sendIntent(PLAYPAUSE) }
        next_btn.setOnClickListener { activity.sendIntent(NEXT) }
        repeat_btn.setOnClickListener { toggleSongRepetition() }
        song_progress_current.setOnClickListener { activity.sendIntent(SKIP_BACKWARD) }
        song_progress_max.setOnClickListener { activity.sendIntent(SKIP_FORWARD) }

        songs_playlist_empty_placeholder_2.setOnClickListener { activityInterface.addFolderToPlaylist() }

        initSeekbarChangeListener()
        setTopArtHeight()

        songs_list.recyclerScrollCallback = object : RecyclerScrollCallback {
            override fun onScrolled(scrollY: Int) {
                top_navigation.beVisibleIf(scrollY > topArtHeight && !activityInterface.getIsSearchOpen())
                val minOverlayTransitionY = actionbarSize - topArtHeight
                art_holder.translationY = Math.min(0, Math.max(minOverlayTransitionY, -scrollY / 2)).toFloat()
                song_list_background.translationY = Math.max(0, -scrollY + topArtHeight).toFloat()
            }
        }

        onResume()
    }

    private fun setTopArtHeight() {
        topArtHeight = if (config.showAlbumCover) resources.getDimensionPixelSize(R.dimen.top_art_height) else 0
        artView!!.setPadding(0, topArtHeight, 0, 0)
    }

    fun searchOpened() {
        songs_playlist_placeholder.text = activity.getString(R.string.no_items_found)
        songs_playlist_empty_placeholder_2.beGone()
        art_holder.beGone()
        getSongsAdapter()?.searchOpened()
        top_navigation.beGone()
    }

    fun searchClosed() {
        songs_playlist_placeholder.text = activity.getString(R.string.playlist_empty)
        songs_playlist_empty_placeholder_2.beVisibleIf(songs.isEmpty())
        art_holder.beVisibleIf(songs.isNotEmpty())
        if (activityInterface.getIsSearchOpen()) {
            searchQueryChanged("")
            getSongsAdapter()?.searchClosed()
            markCurrentSong()
            (songs_list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).scrollToPositionWithOffset(0, 0)
            songs_fastscroller?.setScrollToY(0)
        }
    }

    fun searchQueryChanged(text: String) {
        val filtered = songs.filter { it.artist.contains(text, true) || it.title.contains(text, true) } as ArrayList
        filtered.sortBy { !(it.artist.startsWith(text, true) || it.title.startsWith(text, true)) }
        songs_playlist_placeholder.beVisibleIf(filtered.isEmpty())
        getSongsAdapter()?.updateSongs(filtered, text)
    }

    override fun toggleShuffle() {
        val isShuffleEnabled = !config.isShuffleEnabled
        config.isShuffleEnabled = isShuffleEnabled
        shuffle_btn.applyColorFilter(if (isShuffleEnabled) activity.getAdjustedPrimaryColor() else config.textColor)
        shuffle_btn.alpha = if (isShuffleEnabled) 1f else LOWER_ALPHA
        shuffle_btn.contentDescription = activity.getString(if (isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        getSongsAdapter()?.updateShuffle(isShuffleEnabled)
        activity.toast(if (isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
    }

    override fun toggleSongRepetition() {
        val repeatSong = !config.repeatTrack
        config.repeatTrack = repeatSong
        repeat_btn.applyColorFilter(if (repeatSong) activity.getAdjustedPrimaryColor() else config.textColor)
        repeat_btn.alpha = if (repeatSong) 1f else LOWER_ALPHA
        repeat_btn.contentDescription = activity.getString(if (repeatSong) R.string.disable_song_repetition else R.string.enable_song_repetition)
        getSongsAdapter()?.updateRepeatSong(repeatSong)
        activity.toast(if (repeatSong) R.string.song_repetition_enabled else R.string.song_repetition_disabled)
    }

    override fun refreshItems() {
        if (!activityInterface.getIsSearchOpen()) {
            activity.sendIntent(REFRESH_LIST)
        }
    }

    private fun setupIconColors() {
        val textColor = config.textColor
        previous_btn.applyColorFilter(textColor)
        play_pause_btn.applyColorFilter(textColor)
        next_btn.applyColorFilter(textColor)

        shuffle_btn.applyColorFilter(if (config.isShuffleEnabled) activity.getAdjustedPrimaryColor() else config.textColor)
        shuffle_btn.alpha = if (config.isShuffleEnabled) 1f else LOWER_ALPHA

        repeat_btn.applyColorFilter(if (config.repeatTrack) activity.getAdjustedPrimaryColor() else config.textColor)
        repeat_btn.alpha = if (config.repeatTrack) 1f else LOWER_ALPHA

        getSongsAdapter()?.updateTextColor(textColor)
        songs_fastscroller.updatePrimaryColor()
    }

    private fun setupIconDescriptions() {
        shuffle_btn.contentDescription = activity.getString(if (config.isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        repeat_btn.contentDescription = activity.getString(if (config.repeatTrack) R.string.disable_song_repetition else R.string.enable_song_repetition)
    }

    private fun songPicked(pos: Int) {
        setupIconColors()
        setupIconDescriptions()
        Intent(activity, MusicService::class.java).apply {
            putExtra(TRACK_POS, pos)
            //action = PLAYPOS
            activity.startService(this)
        }
    }

    fun songChangedEvent(song: Track?) {
        updateSongInfo(song)
        markCurrentSong()
        updateAlbumCover()
    }

    fun songStateChanged(isPlaying: Boolean) {
        play_pause_btn.setImageDrawable(resources.getDrawable(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector))
        getSongsAdapter()?.updateSongState(isPlaying)
    }

    fun songProgressUpdated(seconds: Int) {
        song_progressbar.progress = seconds
        getSongsAdapter()?.updateSongProgress(seconds)
    }

    private fun updateSongInfo(song: Track?) {
        top_navigation.song_info_title.text = song?.title ?: ""
        top_navigation.song_info_artist.text = song?.artist ?: ""
        song_progressbar.max = song?.duration ?: 0
        song_progressbar.progress = 0

        getSongsAdapter()?.updateSong(song)
        if (songs.isEmpty() && !activityInterface.getIsThirdPartyIntent()) {
            activity.toast(R.string.empty_playlist)
        }
    }

    fun fillSongsListView(songs: ArrayList<Track>) {
        this.songs = songs
        val currAdapter = songs_list.adapter
        songs_fastscroller.setViews(songs_list) {
            val item = getSongsAdapter()?.songs?.getOrNull(it)
            songs_fastscroller.updateBubbleText(item?.getBubbleText() ?: "")
        }

        if (currAdapter == null) {
            OldSongAdapter(activity, songs, this, artView!!, songs_list, songs_fastscroller) {
                songPicked(getSongIndex(it as Track))
            }.apply {
                isThirdPartyIntent = activityInterface.getIsThirdPartyIntent()
                songs_list.adapter = this
            }
        } else {
            val state = (songs_list.layoutManager as MyLinearLayoutManager).onSaveInstanceState()
            (currAdapter as OldSongAdapter).apply {
                isThirdPartyIntent = activityInterface.getIsThirdPartyIntent()
                updateSongs(songs)
            }
            (songs_list.layoutManager as MyLinearLayoutManager).onRestoreInstanceState(state)
        }

        markCurrentSong()
        songs_list.beVisibleIf(songs.isNotEmpty())
        art_holder.beVisibleIf(songs_list.isVisible())
        songs_playlist_placeholder.beVisibleIf(songs.isEmpty())
        songs_playlist_empty_placeholder_2.beVisibleIf(songs.isEmpty())
    }

    private fun markCurrentSong() {
        getSongsAdapter()?.updateCurrentSongIndex(getCurrentSongIndex())
    }

    private fun getCurrentSongIndex(): Int {
        val newSong = MusicService.mCurrTrack
        val cnt = songs.size - 1
        return (0..cnt).firstOrNull { songs[it] == newSong } ?: -1
    }

    fun getDefaultFilePickerPath(): String {
        val path = if (songs.isEmpty()) {
            Environment.getExternalStorageDirectory().toString()
        } else {
            songs[0].path
        }
        return path
    }

    private fun updateAlbumCover() {
        if (!config.showAlbumCover) {
            return
        }

        val coverToUse = if (MusicService.mCurrTrackCover?.isRecycled == true) {
            resources.getColoredBitmap(R.drawable.ic_headset, config.textColor)
        } else {
            MusicService.mCurrTrackCover
        }

        // Picasso cannot load bitmaps/drawables directly, so as a workaround load images as placeholders
        val bitmapDrawable = BitmapDrawable(resources, coverToUse)
        val uri: Uri? = null
        Picasso.get()
                .load(uri)
                .fit()
                .placeholder(bitmapDrawable)
                .into(art_image)
    }

    private fun getSongIndex(song: Track) = songs.indexOfFirstOrNull { it == song } ?: 0

    fun getSongsAdapter() = songs_list.adapter as? OldSongAdapter

    private fun initSeekbarChangeListener() {
        song_progressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val duration = song_progressbar.max.getFormattedDuration()
                val formattedProgress = progress.getFormattedDuration()
                song_progress_current.text = formattedProgress
                song_progress_max.text = duration
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Intent(activity, MusicService::class.java).apply {
                    putExtra(PROGRESS, seekBar.progress)
                    action = SET_PROGRESS
                    activity.startService(this)
                }
            }
        })
    }
}
