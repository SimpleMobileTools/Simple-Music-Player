package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.getAlbumsSync
import com.simplemobiletools.musicplayer.extensions.getArtistsSync
import com.simplemobiletools.musicplayer.models.Album
import kotlinx.android.synthetic.main.fragment_albums.view.*

// Artists -> Albums -> Tracks
class AlbumsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun setupFragment(activity: SimpleActivity) {
        ensureBackgroundThread {
            val albums = ArrayList<Album>()

            val artists = activity.getArtistsSync()
            artists.forEach { artist ->
                albums.addAll(activity.getAlbumsSync(artist))
            }
        }

        albums_fastscroller.updatePrimaryColor()
        albums_fastscroller.updateBubbleColors()
    }

    override fun finishActMode() {
        (albums_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }
}
