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
import com.simplemobiletools.musicplayer.activities.TrackActivity
import com.simplemobiletools.musicplayer.adapters.TracksAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.RESTART_PLAYER
import com.simplemobiletools.musicplayer.helpers.TAB_TRACKS
import com.simplemobiletools.musicplayer.helpers.TRACK
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.fragment_tracks.view.*

// Artists -> Albums -> Tracks
class TracksFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var tracksIgnoringSearch = ArrayList<Track>()

    override fun setupFragment(activity: SimpleActivity) {
        ensureBackgroundThread {
            val albums = ArrayList<Album>()
            val artists = context.artistDAO.getAll()
            artists.forEach { artist ->
                albums.addAll(context.albumsDAO.getArtistAlbums(artist.id))
            }

            var tracks = ArrayList<Track>()
            albums.forEach { album ->
                tracks.addAll(context.tracksDAO.getTracksFromAlbum(album.id))
            }

            tracks = tracks.distinctBy { "${it.path}/${it.mediaStoreId}" }.toMutableList() as ArrayList<Track>

            Track.sorting = context.config.trackSorting
            tracks.sort()

            activity.runOnUiThread {
                tracks_placeholder.text = context.getString(R.string.no_items_found)
                tracks_placeholder.beVisibleIf(tracks.isEmpty())
                val adapter = tracks_list.adapter
                if (adapter == null) {
                    TracksAdapter(activity, tracks, false, tracks_list) {
                        activity.hideKeyboard()
                        activity.resetQueueItems(tracks) {
                            Intent(activity, TrackActivity::class.java).apply {
                                putExtra(TRACK, Gson().toJson(it))
                                putExtra(RESTART_PLAYER, true)
                                activity.startActivity(this)
                            }
                        }
                    }.apply {
                        tracks_list.adapter = this
                    }

                    if (context.areSystemAnimationsEnabled) {
                        tracks_list.scheduleLayoutAnimation()
                    }
                } else {
                    (adapter as TracksAdapter).updateItems(tracks)
                }
            }
        }
    }

    override fun finishActMode() {
        (tracks_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = tracksIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Track>
        (tracks_list.adapter as? TracksAdapter)?.updateItems(filtered, text)
        tracks_placeholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchOpened() {
        tracksIgnoringSearch = (tracks_list?.adapter as? TracksAdapter)?.tracks ?: ArrayList()
    }

    override fun onSearchClosed() {
        (tracks_list.adapter as? TracksAdapter)?.updateItems(tracksIgnoringSearch)
        tracks_placeholder.beGoneIf(tracksIgnoringSearch.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_TRACKS) {
            val adapter = tracks_list.adapter as? TracksAdapter ?: return@ChangeSortingDialog
            val tracks = adapter.tracks
            Track.sorting = activity.config.trackSorting
            tracks.sort()
            adapter.updateItems(tracks, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        tracks_placeholder.setTextColor(textColor)
        tracks_fastscroller.updateColors(adjustedPrimaryColor)
    }
}
