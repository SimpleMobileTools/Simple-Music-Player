package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TracksActivity
import com.simplemobiletools.musicplayer.adapters.AlbumsAdapter
import com.simplemobiletools.musicplayer.databinding.FragmentAlbumsBinding
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.mediaScanner
import com.simplemobiletools.musicplayer.extensions.viewBinding
import com.simplemobiletools.musicplayer.helpers.ALBUM
import com.simplemobiletools.musicplayer.helpers.TAB_ALBUMS
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.sortSafely

// Artists -> Albums -> Tracks
class AlbumsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var albums = ArrayList<Album>()
    private val binding by viewBinding(FragmentAlbumsBinding::bind)

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
            binding.albumsPlaceholder.text = if (scanning) {
                context.getString(R.string.loading_files)
            } else {
                context.getString(com.simplemobiletools.commons.R.string.no_items_found)
            }
            binding.albumsPlaceholder.beVisibleIf(albums.isEmpty())

            val adapter = binding.albumsList.adapter
            if (adapter == null) {
                AlbumsAdapter(activity, albums, binding.albumsList) {
                    activity.hideKeyboard()
                    Intent(activity, TracksActivity::class.java).apply {
                        putExtra(ALBUM, Gson().toJson(it))
                        activity.startActivity(this)
                    }
                }.apply {
                    binding.albumsList.adapter = this
                }

                if (context.areSystemAnimationsEnabled) {
                    binding.albumsList.scheduleLayoutAnimation()
                }
            } else {
                val oldItems = (adapter as AlbumsAdapter).items
                if (oldItems.sortedBy { it.id }.hashCode() != albums.sortedBy { it.id }.hashCode()) {
                    adapter.updateItems(albums)
                }
            }
        }
    }

    override fun finishActMode() {
        getAdapter()?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = albums.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Album>
        getAdapter()?.updateItems(filtered, text)
        binding.albumsPlaceholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchClosed() {
        getAdapter()?.updateItems(albums)
        binding.albumsPlaceholder.beGoneIf(albums.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_ALBUMS) {
            val adapter = getAdapter() ?: return@ChangeSortingDialog
            albums.sortSafely(activity.config.albumSorting)
            adapter.updateItems(albums, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        binding.albumsPlaceholder.setTextColor(textColor)
        binding.albumsFastscroller.updateColors(adjustedPrimaryColor)
        getAdapter()?.updateColors(textColor)
    }

    private fun getAdapter() = binding.albumsList.adapter as? AlbumsAdapter
}
