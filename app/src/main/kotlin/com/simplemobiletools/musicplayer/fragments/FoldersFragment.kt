package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TracksActivity
import com.simplemobiletools.musicplayer.adapters.FoldersAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.FOLDER
import com.simplemobiletools.musicplayer.helpers.TAB_FOLDERS
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Folder
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.fragment_folders.view.*

class FoldersFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var foldersIgnoringSearch = ArrayList<Folder>()

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

            val foldersMap = tracks.groupBy { it.folderName }
            val folders = ArrayList<Folder>()
            for ((title, folderTracks) in foldersMap) {
                val folder = Folder(title, folderTracks.size)
                folders.add(folder)

                if (!context.config.wereTrackFoldersAdded) {
                    folderTracks.forEach {
                        context.tracksDAO.updateFolderName(title, it.mediaStoreId)
                    }
                }
            }

            context.config.wereTrackFoldersAdded = true

            activity.runOnUiThread {
                folders_placeholder.text = context.getString(R.string.no_items_found)
                folders_placeholder.beVisibleIf(folders.isEmpty())

                Folder.sorting = activity.config.folderSorting
                folders.sort()

                val adapter = folders_list.adapter
                if (adapter == null) {
                    FoldersAdapter(activity, folders, folders_list) {
                        activity.hideKeyboard()
                        Intent(activity, TracksActivity::class.java).apply {
                            putExtra(FOLDER, (it as Folder).title)
                            activity.startActivity(this)
                        }
                    }.apply {
                        folders_list.adapter = this
                    }

                    if (context.areSystemAnimationsEnabled) {
                        folders_list.scheduleLayoutAnimation()
                    }
                } else {
                    (adapter as FoldersAdapter).updateItems(folders)
                }
            }
        }
    }

    override fun finishActMode() {
        (folders_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = foldersIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Folder>
        (folders_list.adapter as? FoldersAdapter)?.updateItems(filtered, text)
        folders_placeholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchOpened() {
        foldersIgnoringSearch = (folders_list?.adapter as? FoldersAdapter)?.folders ?: ArrayList()
    }

    override fun onSearchClosed() {
        (folders_list.adapter as? FoldersAdapter)?.updateItems(foldersIgnoringSearch)
        folders_placeholder.beGoneIf(foldersIgnoringSearch.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_FOLDERS) {
            val adapter = folders_list.adapter as? FoldersAdapter ?: return@ChangeSortingDialog
            val folders = adapter.folders
            Folder.sorting = activity.config.folderSorting
            folders.sort()
            adapter.updateItems(folders, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        folders_placeholder.setTextColor(textColor)
        folders_fastscroller.updateColors(adjustedPrimaryColor)
    }
}
