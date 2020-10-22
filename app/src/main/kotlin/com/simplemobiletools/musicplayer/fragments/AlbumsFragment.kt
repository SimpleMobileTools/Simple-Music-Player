package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TracksActivity
import com.simplemobiletools.musicplayer.adapters.AlbumsAdapter
import com.simplemobiletools.musicplayer.extensions.getAlbumsSync
import com.simplemobiletools.musicplayer.extensions.getArtistsSync
import com.simplemobiletools.musicplayer.helpers.ALBUM
import com.simplemobiletools.musicplayer.models.Album
import kotlinx.android.synthetic.main.fragment_albums.view.*

// Artists -> Albums -> Tracks
class AlbumsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    var albumsIgnoringSearch = ArrayList<Album>()

    override fun setupFragment(activity: SimpleActivity) {
        ensureBackgroundThread {
            val albums = ArrayList<Album>()

            val artists = activity.getArtistsSync()
            artists.forEach { artist ->
                albums.addAll(activity.getAlbumsSync(artist))
            }

            albums.sortWith { o1, o2 -> AlphanumericComparator().compare(o1.title.toLowerCase(), o2.title.toLowerCase()) }

            activity.runOnUiThread {
                val adapter = AlbumsAdapter(activity, albums, albums_list, albums_fastscroller) {
                    Intent(activity, TracksActivity::class.java).apply {
                        putExtra(ALBUM, Gson().toJson(it))
                        activity.startActivity(this)
                    }
                }.apply {
                    albums_list.adapter = this
                }

                albums_fastscroller.setViews(albums_list) {
                    val album = adapter.albums.getOrNull(it)
                    albums_fastscroller.updateBubbleText(album?.title ?: "")
                }
            }
        }

        albums_fastscroller.updatePrimaryColor()
        albums_fastscroller.updateBubbleColors()
    }

    override fun finishActMode() {
        (albums_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = albumsIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Album>
        (albums_list.adapter as? AlbumsAdapter)?.updateItems(filtered, text)
        albums_placeholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchOpened() {
        albumsIgnoringSearch = (albums_list?.adapter as? AlbumsAdapter)?.albums ?: ArrayList()
    }

    override fun onSearchClosed() {
        (albums_list.adapter as? AlbumsAdapter)?.updateItems(albumsIgnoringSearch)
        albums_placeholder.beGone()
    }
}
