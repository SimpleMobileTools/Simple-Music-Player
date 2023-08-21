package com.simplemobiletools.musicplayer.activities

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.media3.common.MediaItem
import com.simplemobiletools.commons.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.QueueAdapter
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.RoomHelper
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.activity_queue.*

class QueueActivity : SimpleControllerActivity() {
    private var searchMenuItem: MenuItem? = null
    private var isSearchOpen = false
    private var tracksIgnoringSearch = ArrayList<Track>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)
        setupOptionsMenu()
        updateMaterialActivityViews(queue_coordinator, queue_list, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(queue_list, queue_toolbar)

        setupAdapter()
        queue_fastscroller.updateColors(getProperPrimaryColor())
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(queue_toolbar, NavigationIcon.Arrow, searchMenuItem = searchMenuItem)
    }

    override fun onBackPressed() {
        if (isSearchOpen && searchMenuItem != null) {
            searchMenuItem!!.collapseActionView()
        } else {
            super.onBackPressed()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateAdapter()

    override fun onIsPlayingChanged(isPlaying: Boolean) = updateAdapter()

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
        val adapter = getAdapter() ?: return
        tracksIgnoringSearch = adapter.items
        adapter.updateItems(tracksIgnoringSearch, forceUpdate = true)
    }

    private fun onSearchClosed() {
        val adapter = getAdapter() ?: return
        adapter.updateItems(tracksIgnoringSearch, forceUpdate = true)
        queue_placeholder.beGoneIf(tracksIgnoringSearch.isNotEmpty())
    }

    private fun onSearchQueryChanged(text: String) {
        val filtered = tracksIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Track>
        getAdapter()?.updateItems(filtered, text)
        queue_placeholder.beGoneIf(filtered.isNotEmpty())
    }

    private fun getAdapter(): QueueAdapter? {
        return queue_list.adapter as? QueueAdapter
    }

    private fun setupAdapter() {
        if (getAdapter() == null) {
            ensureBackgroundThread {
                val tracks = audioHelper.getAllQueuedTracks()
                runOnUiThread {
                    queue_list.adapter = QueueAdapter(activity = this, items = tracks, recyclerView = queue_list) {
                        withPlayer {
                            val startIndex = currentMediaItems.indexOfTrack(it as Track)
                            seekTo(startIndex, 0)
                            if (!isReallyPlaying) {
                                play()
                            }
                        }
                    }

                    if (areSystemAnimationsEnabled) {
                        queue_list.scheduleLayoutAnimation()
                    }

                    val currentPosition = tracks.indexOfFirst { it.isCurrent() }
                    if (currentPosition > 0) {
                        queue_list.smoothScrollToPosition(currentPosition)
                    }
                }
            }
        } else {
            updateAdapter()
        }
    }

    private fun updateAdapter() {
        val adapter = getAdapter() ?: return
        withPlayer {
            val currentTracks = currentMediaItems.toTracks() as ArrayList<Track>
            adapter.updateItems(currentTracks, forceUpdate = true)
        }
    }

    private fun createPlaylistFromQueue() {
        NewPlaylistDialog(this) { newPlaylistId ->
            val tracks = ArrayList<Track>()
            getAdapter()?.items?.forEach {
                it.playListId = newPlaylistId
                tracks.add(it)
            }

            ensureBackgroundThread {
                RoomHelper(this).insertTracksWithPlaylist(tracks)
            }
        }
    }
}
