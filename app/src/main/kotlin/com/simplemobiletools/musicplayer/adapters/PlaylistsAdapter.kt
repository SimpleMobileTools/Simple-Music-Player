package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getPlaylistSongs
import com.simplemobiletools.musicplayer.extensions.playlistChanged
import com.simplemobiletools.musicplayer.extensions.playlistDAO
import com.simplemobiletools.musicplayer.helpers.ALL_SONGS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.interfaces.RefreshPlaylistsListener
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.item_playlist.view.*
import java.util.*

class PlaylistsAdapter(activity: SimpleActivity, val playlists: ArrayList<Playlist>, val listener: RefreshPlaylistsListener?, recyclerView: MyRecyclerView,
                       itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_playlists

    override fun prepareItemSelection(view: View) {}

    override fun markItemSelection(select: Boolean, view: View?) {
        view?.playlist_frame?.isSelected = select
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_playlist, parent)

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
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
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
        }
    }

    private fun deletePlaylistSongs(ids: ArrayList<Int>, callback: () -> Unit) {
        var cnt = ids.size
        ids.map {
            val paths = activity.getPlaylistSongs(it).map { it.path }
            val fileDirItems = paths.map { FileDirItem(it, it.getFilenameFromPath()) } as ArrayList<FileDirItem>
            activity.deleteFiles(fileDirItems) {
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
            val playlist = playlists[pos]
            if (playlist.id == ALL_SONGS_PLAYLIST_ID) {
                activity.toast(R.string.all_songs_cannot_be_deleted)
                selectedPositions.remove(pos)
                toggleItemSelection(false, pos)
                break
            } else if (playlist.id == activity.config.currentPlaylist) {
                activity.playlistChanged(ALL_SONGS_PLAYLIST_ID)
            }
        }

        selectedPositions.sortedDescending().forEach {
            val playlist = playlists[it]
            playlistsToDelete.add(playlist)
        }
        playlists.removeAll(playlistsToDelete)

        Thread {
            activity.playlistDAO.deletePlaylists(playlistsToDelete)
            activity.runOnUiThread {
                if (isDeletingCurrentPlaylist) {
                    reloadList()
                } else {
                    removeSelectedItems()
                }
            }
        }.start()
    }

    private fun showRenameDialog() {
        NewPlaylistDialog(activity, playlists[selectedPositions.first()]) {
            activity.runOnUiThread {
                reloadList()
            }
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
