package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.deleteFiles
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.extensions.deletePlaylists
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.item_playlist.view.*
import org.greenrobot.eventbus.EventBus
import java.util.*

class PlaylistsAdapter(
    activity: SimpleActivity, var playlists: ArrayList<Playlist>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textToHighlight = ""

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_playlists

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_playlist, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = playlists.getOrNull(position) ?: return
        holder.bindView(playlist, true, true) { itemView, layoutPosition ->
            setupView(itemView, playlist)
        }
        bindViewHolder(holder)
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
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = playlists.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = playlists.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = playlists.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun askConfirmDelete() {
        RemovePlaylistDialog(activity) {
            val ids = selectedKeys.map { it } as ArrayList<Int>
            if (it) {
                ensureBackgroundThread {
                    deletePlaylistSongs(ids) {
                        removePlaylists()
                    }
                }
            } else {
                removePlaylists()
            }
        }
    }

    private fun deletePlaylistSongs(ids: ArrayList<Int>, callback: () -> Unit) {
        var cnt = ids.size
        ids.map {
            val paths = activity.tracksDAO.getTracksFromPlaylist(it).map { it.path }
            val fileDirItems = paths.map { FileDirItem(it, it.getFilenameFromPath()) } as ArrayList<FileDirItem>
            activity.deleteFiles(fileDirItems) {
                if (--cnt <= 0) {
                    callback()
                }
            }
        }
    }

    private fun removePlaylists() {
        val playlistsToDelete = ArrayList<Playlist>(selectedKeys.size)
        val positions = ArrayList<Int>()
        for (key in selectedKeys) {
            val playlist = getItemWithKey(key) ?: continue
            val position = playlists.indexOfFirst { it.id == key }
            if (position != -1) {
                positions.add(position + positionOffset)
            }
            playlistsToDelete.add(playlist)
        }

        playlists.removeAll(playlistsToDelete)

        ensureBackgroundThread {
            activity.deletePlaylists(playlistsToDelete)
            activity.runOnUiThread {
                removeSelectedItems(positions)
            }

            if (playlists.isEmpty()) {
                EventBus.getDefault().post(Events.PlaylistsUpdated())
            }
        }
    }

    private fun getItemWithKey(key: Int): Playlist? = playlists.firstOrNull { it.id == key }

    fun updateItems(newItems: ArrayList<Playlist>, highlightText: String = "", forceUpdate: Boolean = false) {
        if (forceUpdate || newItems.hashCode() != playlists.hashCode()) {
            playlists = newItems.clone() as ArrayList<Playlist>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun showRenameDialog() {
        NewPlaylistDialog(activity, playlists[getItemKeyPosition(selectedKeys.first())]) {
            activity.runOnUiThread {
                finishActMode()
            }
        }
    }

    private fun setupView(view: View, playlist: Playlist) {
        view.apply {
            playlist_frame?.isSelected = selectedKeys.contains(playlist.id)
            playlist_title.text = if (textToHighlight.isEmpty()) playlist.title else playlist.title.highlightTextPart(textToHighlight, properPrimaryColor)
            playlist_title.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, playlist.trackCount, playlist.trackCount)
            playlist_tracks.text = tracks
            playlist_tracks.setTextColor(textColor)
        }
    }

    override fun onChange(position: Int) = playlists.getOrNull(position)?.getBubbleText() ?: ""
}
