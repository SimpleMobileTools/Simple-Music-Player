package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TrackActivity
import com.simplemobiletools.musicplayer.adapters.TracksAdapter
import com.simplemobiletools.musicplayer.extensions.getAlbumTracksSync
import com.simplemobiletools.musicplayer.extensions.getAlbumsSync
import com.simplemobiletools.musicplayer.extensions.getArtistsSync
import com.simplemobiletools.musicplayer.helpers.RESTART_PLAYER
import com.simplemobiletools.musicplayer.helpers.TRACK
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.fragment_tracks.view.*

// Artists -> Albums -> Tracks
class TracksFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun setupFragment(activity: SimpleActivity) {
        ensureBackgroundThread {
            val albums = ArrayList<Album>()
            val artists = activity.getArtistsSync()
            artists.forEach { artist ->
                albums.addAll(activity.getAlbumsSync(artist))
            }

            val tracks = ArrayList<Track>()
            albums.forEach {
                tracks.addAll(activity.getAlbumTracksSync(it.id))
            }
            tracks.sortWith { o1, o2 -> AlphanumericComparator().compare(o1.title.toLowerCase(), o2.title.toLowerCase()) }

            activity.runOnUiThread {
                val adapter = TracksAdapter(activity, tracks, tracks_list, tracks_fastscroller) {
                    Intent(activity, TrackActivity::class.java).apply {
                        putExtra(TRACK, Gson().toJson(it))
                        putExtra(RESTART_PLAYER, true)
                        activity.startActivity(this)
                    }
                }.apply {
                    tracks_list.adapter = this
                }

                tracks_fastscroller.setViews(tracks_list) {
                    val track = adapter.tracks.getOrNull(it)
                    tracks_fastscroller.updateBubbleText(track?.title ?: "")
                }
            }
        }

        tracks_fastscroller.updatePrimaryColor()
        tracks_fastscroller.updateBubbleColors()
    }

    override fun finishActMode() {
        (tracks_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }
}
