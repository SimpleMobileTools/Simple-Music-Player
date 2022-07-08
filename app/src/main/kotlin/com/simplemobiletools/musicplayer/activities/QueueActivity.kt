package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.QueueAdapter
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.helpers.PLAY_TRACK
import com.simplemobiletools.musicplayer.helpers.RoomHelper
import com.simplemobiletools.musicplayer.helpers.TRACK_ID
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_queue.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class QueueActivity : SimpleActivity() {
    private var bus: EventBus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)
        setupOptionsMenu()

        bus = EventBus.getDefault()
        bus!!.register(this)
        setupAdapter()
        queue_fastscroller.updateColors(getProperPrimaryColor())
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(queue_toolbar, NavigationIcon.Arrow)
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun setupOptionsMenu() {
        queue_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.create_playlist_from_queue -> createPlaylistFromQueue()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupAdapter() {
        val adapter = queue_list.adapter
        if (adapter == null) {
            QueueAdapter(this, MusicService.mTracks, queue_list) {
                Intent(this, MusicService::class.java).apply {
                    action = PLAY_TRACK
                    putExtra(TRACK_ID, (it as Track).mediaStoreId)
                    startService(this)
                }
            }.apply {
                queue_list.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                queue_list.scheduleLayoutAnimation()
            }

            val currentTrackPosition = MusicService.mTracks.indexOfFirstOrNull { it == MusicService.mCurrTrack } ?: -1
            if (currentTrackPosition > 0) {
                queue_list.smoothScrollToPosition(currentTrackPosition)
            }

        } else {
            adapter.notifyDataSetChanged()
        }
    }

    private fun createPlaylistFromQueue() {
        NewPlaylistDialog(this) { newPlaylistId ->
            val tracks = ArrayList<Track>()
            (queue_list.adapter as? QueueAdapter)?.items?.forEach {
                it.playListId = newPlaylistId
                tracks.add(it)
            }

            ensureBackgroundThread {
                RoomHelper(this).insertTracksWithPlaylist(tracks)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun trackChangedEvent(event: Events.TrackChanged) {
        setupAdapter()
    }
}
