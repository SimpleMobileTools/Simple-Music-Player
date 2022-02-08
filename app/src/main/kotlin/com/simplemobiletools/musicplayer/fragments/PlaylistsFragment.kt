package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TracksActivity
import com.simplemobiletools.musicplayer.adapters.PlaylistsAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.playlistDAO
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.helpers.PLAYLIST
import com.simplemobiletools.musicplayer.helpers.TAB_PLAYLISTS
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.fragment_playlists.view.*
import org.greenrobot.eventbus.EventBus

class PlaylistsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var playlistsIgnoringSearch = ArrayList<Playlist>()

    override fun setupFragment(activity: SimpleActivity) {
        playlists_placeholder_2.underlineText()
        playlists_placeholder_2.setOnClickListener {
            NewPlaylistDialog(activity) {
                EventBus.getDefault().post(Events.PlaylistsUpdated())
            }
        }

        ensureBackgroundThread {
            val playlists = context.playlistDAO.getAll() as ArrayList<Playlist>
            playlists.forEach {
                it.trackCount = context.tracksDAO.getTracksCountFromPlaylist(it.id)
            }

            Playlist.sorting = context.config.playlistSorting
            playlists.sort()

            activity.runOnUiThread {
                playlists_placeholder.text = context.getString(R.string.no_items_found)
                playlists_placeholder.beVisibleIf(playlists.isEmpty())
                playlists_placeholder_2.beVisibleIf(playlists.isEmpty())
                val adapter = playlists_list.adapter
                if (adapter == null) {
                    PlaylistsAdapter(activity, playlists, playlists_list) {
                        activity.hideKeyboard()
                        Intent(activity, TracksActivity::class.java).apply {
                            putExtra(PLAYLIST, Gson().toJson(it))
                            activity.startActivity(this)
                        }
                    }.apply {
                        playlists_list.adapter = this
                    }

                    if (context.areSystemAnimationsEnabled) {
                        playlists_list.scheduleLayoutAnimation()
                    }
                } else {
                    (adapter as PlaylistsAdapter).updateItems(playlists)
                }
            }
        }
    }

    override fun finishActMode() {
        (playlists_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = playlistsIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Playlist>
        (playlists_list.adapter as? PlaylistsAdapter)?.updateItems(filtered, text)
        playlists_placeholder.beVisibleIf(filtered.isEmpty())
        playlists_placeholder_2.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchOpened() {
        playlistsIgnoringSearch = (playlists_list?.adapter as? PlaylistsAdapter)?.playlists ?: ArrayList()
    }

    override fun onSearchClosed() {
        (playlists_list.adapter as? PlaylistsAdapter)?.updateItems(playlistsIgnoringSearch)
        playlists_placeholder.beGoneIf(playlistsIgnoringSearch.isNotEmpty())
        playlists_placeholder_2.beGoneIf(playlistsIgnoringSearch.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_PLAYLISTS) {
            val adapter = playlists_list.adapter as? PlaylistsAdapter ?: return@ChangeSortingDialog
            val playlists = adapter.playlists
            Playlist.sorting = activity.config.playlistSorting
            playlists.sort()
            adapter.updateItems(playlists, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        playlists_placeholder.setTextColor(textColor)
        playlists_placeholder_2.setTextColor(adjustedPrimaryColor)
        playlists_fastscroller.updateColors(adjustedPrimaryColor)
    }
}
