package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.musicplayer.activities.AlbumsActivity
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.adapters.ArtistsAdapter
import com.simplemobiletools.musicplayer.extensions.getArtists
import com.simplemobiletools.musicplayer.helpers.ARTIST
import com.simplemobiletools.musicplayer.models.Artist
import kotlinx.android.synthetic.main.fragment_artists.view.*

// Artists -> Albums -> Tracks
class ArtistsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun setupFragment(activity: SimpleActivity) {
        activity.getArtists { artists ->
            activity.runOnUiThread {
                ArtistsAdapter(activity, artists, artists_list) {
                    Intent(activity, AlbumsActivity::class.java).apply {
                        putExtra(ARTIST, Gson().toJson(it as Artist))
                        activity.startActivity(this)
                    }
                }.apply {
                    artists_list.adapter = this
                }
            }
        }
    }

    override fun finishActMode() {
        (artists_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }
}
