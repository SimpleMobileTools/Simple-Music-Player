package org.fossify.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.areSystemAnimationsEnabled
import org.fossify.commons.extensions.beGoneIf
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.activities.TracksActivity
import org.fossify.musicplayer.adapters.AlbumsAdapter
import org.fossify.musicplayer.databinding.FragmentAlbumsBinding
import org.fossify.musicplayer.dialogs.ChangeSortingDialog
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.mediaScanner
import org.fossify.musicplayer.extensions.viewBinding
import org.fossify.musicplayer.helpers.ALBUM
import org.fossify.musicplayer.helpers.TAB_ALBUMS
import org.fossify.musicplayer.models.Album
import org.fossify.musicplayer.models.sortSafely

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
                context.getString(org.fossify.commons.R.string.no_items_found)
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
