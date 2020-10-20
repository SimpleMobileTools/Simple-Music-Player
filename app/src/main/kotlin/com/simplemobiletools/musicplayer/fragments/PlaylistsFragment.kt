package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TracksActivity
import com.simplemobiletools.musicplayer.adapters.PlaylistsAdapter
import com.simplemobiletools.musicplayer.extensions.playlistDAO
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.helpers.PLAYLIST
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.fragment_playlists.view.*

class PlaylistsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    var playlistsIgnoringSearch = ArrayList<Playlist>()

    override fun setupFragment(activity: SimpleActivity) {
        ensureBackgroundThread {
            var playlists = activity.playlistDAO.getAll() as ArrayList<Playlist>
            playlists.forEach {
                it.trackCnt = activity.tracksDAO.getTracksCountFromPlaylist(it.id)
            }
            playlists = playlists.filter { it.trackCnt != 0 }.toMutableList() as ArrayList<Playlist>

            activity.runOnUiThread {
                val adapter = PlaylistsAdapter(activity, playlists, playlists_list, playlists_fastscroller) {
                    Intent(activity, TracksActivity::class.java).apply {
                        putExtra(PLAYLIST, Gson().toJson(it))
                        activity.startActivity(this)
                    }
                }.apply {
                    playlists_list.adapter = this
                }

                playlists_fastscroller.setViews(playlists_list) {
                    val playlist = adapter.playlists.getOrNull(it)
                    playlists_fastscroller.updateBubbleText(playlist?.title ?: "")
                }
            }
        }

        playlists_fastscroller.updatePrimaryColor()
        playlists_fastscroller.updateBubbleColors()
    }

    override fun finishActMode() {
        (playlists_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = playlistsIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Playlist>
        (playlists_list.adapter as? PlaylistsAdapter)?.updateItems(filtered, text)
    }

    override fun onSearchOpened() {
        playlistsIgnoringSearch = (playlists_list?.adapter as? PlaylistsAdapter)?.playlists ?: ArrayList()
    }

    override fun onSearchClosed() {
        (playlists_list.adapter as? PlaylistsAdapter)?.updateItems(playlistsIgnoringSearch)
    }
}
