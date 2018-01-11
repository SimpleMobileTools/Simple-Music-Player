package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.beInvisibleIf
import com.simplemobiletools.commons.extensions.deleteFiles
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.helpers.DBHelper
import com.simplemobiletools.musicplayer.interfaces.RefreshItemsListener
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.item_playlist.view.*
import java.io.File
import java.util.*

class PlaylistsAdapter(activity: SimpleActivity, val playlists: ArrayList<Playlist>, val listener: RefreshItemsListener?, recyclerView: MyRecyclerView,
                       itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    override fun getActionMenuId() = R.menu.cab_playlists

    override fun prepareItemSelection(view: View) {}

    override fun markItemSelection(select: Boolean, view: View?) {
        view?.playlist_frame?.isSelected = select
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = createViewHolder(R.layout.item_playlist, parent)

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        val playlist = playlists[position]
        val view = holder.bindView(playlist) { itemView, layoutPosition ->
            setupView(itemView, playlist)
        }
        bindViewHolder(holder, position, view)
    }

    override fun getItemCount() = playlists.size

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = selectedPositions.size == 1
        }
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_rename -> showRenameDialog()
        }
    }

    override fun getSelectableItemCount() = playlists.size

    private fun askConfirmDelete() {
        RemovePlaylistDialog(activity) {
            val ids = selectedPositions.map { playlists[it].id } as ArrayList<Int>
            if (it) {
                deletePlaylistSongs(ids) {
                    removePlaylists(ids)
                }
            } else {
                removePlaylists(ids)
            }
            finishActMode()
        }
    }

    private fun deletePlaylistSongs(ids: ArrayList<Int>, callback: () -> Unit) {
        var cnt = ids.size
        ids.map { activity.dbHelper.getPlaylistSongPaths(it).map(::File) as ArrayList<File> }
                .forEach {
                    activity.deleteFiles(it) {
                        if (--cnt <= 0) {
                            callback()
                        }
                    }
                }
    }

    private fun removePlaylists(ids: ArrayList<Int>) {
        val isDeletingCurrentPlaylist = ids.contains(activity.config.currentPlaylist)
        val playlistsToDelete = ArrayList<Playlist>(selectedPositions.size)

        for (pos in selectedPositions) {
            if (playlists[pos].id == DBHelper.ALL_SONGS_ID) {
                activity.toast(R.string.all_songs_cannot_be_deleted)
                selectedPositions.remove(pos)
                toggleItemSelection(false, pos)
                break
            }
        }

        selectedPositions.sortedDescending().forEach {
            val playlist = playlists[it]
            playlistsToDelete.add(playlist)
        }
        playlists.removeAll(playlistsToDelete)
        activity.dbHelper.removePlaylists(ids)

        if (isDeletingCurrentPlaylist) {
            reloadList()
        } else {
            removeSelectedItems()
        }
    }

    private fun showRenameDialog() {
        NewPlaylistDialog(activity, playlists[selectedPositions.first()]) {
            reloadList()
        }
    }

    private fun reloadList() {
        finishActMode()
        listener?.refreshItems()
    }

    private fun setupView(view: View, playlist: Playlist) {
        view.apply {
            playlist_title.text = playlist.title
            playlist_title.setTextColor(textColor)
            playlist_icon.applyColorFilter(textColor)
            playlist_icon.beInvisibleIf(playlist.id != context.config.currentPlaylist)
        }
    }
}
