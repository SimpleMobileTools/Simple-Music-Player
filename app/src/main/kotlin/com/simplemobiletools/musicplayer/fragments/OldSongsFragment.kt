package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.SeekBar
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getAdjustedPrimaryColor
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.adapters.OldSongAdapter
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getActionBarHeight
import com.simplemobiletools.musicplayer.helpers.LIST_HEADERS_COUNT
import com.simplemobiletools.musicplayer.helpers.PROGRESS
import com.simplemobiletools.musicplayer.helpers.SET_PROGRESS
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.fragment_old_songs.view.*
import kotlinx.android.synthetic.main.item_navigation.view.*
import java.util.*

class OldSongsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var songs = ArrayList<Track>()
    private var artView: ViewGroup? = null
    private var actionbarSize = 0
    private val config = context.config

    private var storedTextColor = 0

    private lateinit var activity: SimpleActivity

    fun onResume() {
        songs_playlist_empty_placeholder_2.setTextColor(activity.getAdjustedPrimaryColor())
        songs_playlist_empty_placeholder_2.paintFlags = songs_playlist_empty_placeholder_2.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        songs_fastscroller.updateBubbleColors()

        arrayListOf(art_holder, song_list_background, top_navigation).forEach {
            it.background = ColorDrawable(config.backgroundColor)
        }
    }

    fun onPause() {
        storeStateVariables()
    }

    override fun finishActMode() {
        (songs_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    private fun storeStateVariables() {
        config.apply {
            storedTextColor = textColor
        }
    }

    override fun setupFragment(simpleActivity: SimpleActivity) {
        storeStateVariables()
        activity = simpleActivity
        actionbarSize = activity.getActionBarHeight()

        artView = activity.layoutInflater.inflate(R.layout.item_transparent, null) as ViewGroup
        songs_fastscroller.measureItemIndex = LIST_HEADERS_COUNT

        initSeekbarChangeListener()
        onResume()
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
    }

    fun searchQueryChanged(text: String) {
        val filtered = songs.filter { it.artist.contains(text, true) || it.title.contains(text, true) } as ArrayList
        filtered.sortBy { !(it.artist.startsWith(text, true) || it.title.startsWith(text, true)) }
        songs_playlist_placeholder.beVisibleIf(filtered.isEmpty())
        getSongsAdapter()?.updateSongs(filtered, text)
    }

    private fun getCurrentSongIndex(): Int {
        val newSong = MusicService.mCurrTrack
        val cnt = songs.size - 1
        return (0..cnt).firstOrNull { songs[it] == newSong } ?: -1
    }

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

    override fun onSearchQueryChanged(text: String) {
    }

    override fun onSearchOpened() {
    }

    override fun onSearchClosed() {
    }
}
