package com.simplemobiletools.musicplayer.activities

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import com.simplemobiletools.commons.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.commons.extensions.beGoneIf
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
    private var searchMenuItem: MenuItem? = null
    private var isSearchOpen = false
    private var tracksIgnoringSearch = ArrayList<Track>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)
        setupOptionsMenu()
        updateMaterialActivityViews(queue_coordinator, queue_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(queue_nested_scrollview, queue_toolbar)

        bus = EventBus.getDefault()
        bus!!.register(this)
        setupAdapter()
        queue_fastscroller.updateColors(getProperPrimaryColor())
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(queue_toolbar, NavigationIcon.Arrow, searchMenuItem = searchMenuItem)
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressed() {
        if (isSearchOpen && searchMenuItem != null) {
            searchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupOptionsMenu() {
        setupSearch(queue_toolbar.menu)
        queue_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.create_playlist_from_queue -> createPlaylistFromQueue()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                onSearchOpened()
                isSearchOpen = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                onSearchClosed()
                isSearchOpen = false
                return true
            }
        })
    }

    private fun onSearchOpened() {
        val adapter = (queue_list.adapter as? QueueAdapter) ?: return
        tracksIgnoringSearch = adapter.items
        adapter.updateItems(tracksIgnoringSearch, forceUpdate = true)
    }

    private fun onSearchClosed() {
        val adapter = (queue_list.adapter as? QueueAdapter) ?: return
        adapter.updateItems(tracksIgnoringSearch, forceUpdate = true)
        queue_placeholder.beGoneIf(tracksIgnoringSearch.isNotEmpty())
    }

    private fun onSearchQueryChanged(text: String) {
        val filtered = tracksIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Track>
        (queue_list.adapter as? QueueAdapter)?.updateItems(filtered, text)
        queue_placeholder.beGoneIf(filtered.isNotEmpty())
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
