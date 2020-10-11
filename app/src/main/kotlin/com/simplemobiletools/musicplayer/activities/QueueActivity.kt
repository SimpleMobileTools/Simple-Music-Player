package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.QueueAdapter
import com.simplemobiletools.musicplayer.helpers.PLAY_TRACK
import com.simplemobiletools.musicplayer.helpers.TRACK_ID
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
        bus = EventBus.getDefault()
        bus!!.register(this)
        setupAdapter()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupAdapter() {
        val adapter = queue_list.adapter
        if (adapter == null) {
            QueueAdapter(this, MusicService.mTracks, queue_list) {
                Intent(this, MusicService::class.java).apply {
                    action = PLAY_TRACK
                    putExtra(TRACK_ID, (it as Track).id)
                    startService(this)
                }
            }.apply {
                queue_list.adapter = this
            }
        } else {
            adapter.notifyDataSetChanged()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun trackChangedEvent(event: Events.TrackChanged) {
        setupAdapter()
    }
}
