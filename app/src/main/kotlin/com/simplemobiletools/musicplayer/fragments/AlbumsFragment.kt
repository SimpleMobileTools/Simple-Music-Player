package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TracksActivity
import com.simplemobiletools.musicplayer.adapters.AlbumsAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.mediaScanner
import com.simplemobiletools.musicplayer.helpers.ALBUM
import com.simplemobiletools.musicplayer.helpers.TAB_ALBUMS
import com.simplemobiletools.musicplayer.models.Album
import kotlinx.android.synthetic.main.fragment_albums.view.albums_fastscroller
import kotlinx.android.synthetic.main.fragment_albums.view.albums_list
import kotlinx.android.synthetic.main.fragment_albums.view.albums_placeholder

// Artists -> Albums -> Tracks
class AlbumsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var albums = ArrayList<Album>()

    override fun setupFragment(activity: BaseSimpleActivity) {
        ensureBackgroundThread {
            val cachedAlbums = activity.audioHelper.getAllAlbums()
            activity.runOnUiThread {
                gotAlbums(activity, cachedAlbums)
            }
        }
    }

    private fun gotAlbums(activity: BaseSimpleActivity, cachedAlbums: ArrayList<Album>) {
        albums = cachedAlbums

        activity.runOnUiThread {
            val scanning = activity.mediaScanner.isScanning()
            albums_placeholder.text = if (scanning) {
                context.getString(R.string.loading_files)
            } else {
                context.getString(R.string.no_items_found)
            }
            albums_placeholder.beVisibleIf(albums.isEmpty())

            val adapter = albums_list.adapter
            if (adapter == null) {
                AlbumsAdapter(activity, albums, albums_list) {
                    activity.hideKeyboard()
                    Intent(activity, TracksActivity::class.java).apply {
                        putExtra(ALBUM, Gson().toJson(it))
                        activity.startActivity(this)
                    }
                }.apply {
                    albums_list.adapter = this
                }

                if (context.areSystemAnimationsEnabled) {
                    albums_list.scheduleLayoutAnimation()
                }
            } else {
                val oldItems = (adapter as AlbumsAdapter).albums
                if (oldItems.sortedBy { it.id }.hashCode() != albums.sortedBy { it.id }.hashCode()) {
                    adapter.updateItems(albums)
                }
            }
        }
    }

    override fun finishActMode() {
        (albums_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = albums.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Album>
        (albums_list.adapter as? AlbumsAdapter)?.updateItems(filtered, text)
        albums_placeholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchClosed() {
        (albums_list.adapter as? AlbumsAdapter)?.updateItems(albums)
        albums_placeholder.beGoneIf(albums.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_ALBUMS) {
            val adapter = albums_list.adapter as? AlbumsAdapter ?: return@ChangeSortingDialog
            Album.sorting = activity.config.albumSorting
            albums.sort()
            adapter.updateItems(albums, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        albums_placeholder.setTextColor(textColor)
        albums_fastscroller.updateColors(adjustedPrimaryColor)
    }
}
