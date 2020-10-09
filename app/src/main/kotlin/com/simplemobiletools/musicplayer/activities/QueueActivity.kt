package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.QueueAdapter
import com.simplemobiletools.musicplayer.adapters.SongsAdapter
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_queue.*
import kotlinx.android.synthetic.main.activity_songs.*
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

        QueueAdapter(this, MusicService.mTracks, queue_list) {

        }.apply {
            queue_list.adapter = this
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun queueUpdated(event: Events.QueueUpdated) {

    }
}
