package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.deleteFiles
import com.simplemobiletools.commons.extensions.getFilenameFromPath
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ItemPlaylistBinding
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import org.greenrobot.eventbus.EventBus

class PlaylistsAdapter(
    activity: BaseSimpleActivity, items: ArrayList<Playlist>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit
) : BaseMusicAdapter<Playlist>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun getActionMenuId() = R.menu.cab_playlists

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaylistBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = items.getOrNull(position) ?: return
        holder.bindView(playlist, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, playlist)
        }
        bindViewHolder(holder)
    }

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

    private fun askConfirmDelete() {
        RemovePlaylistDialog(context) { delete ->
            val ids = getSelectedItems().map { it.id } as ArrayList<Int>
            if (delete) {
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
        ids.map { id ->
            val paths = context.audioHelper.getPlaylistTracks(id).map { it.path }
            val fileDirItems = paths.map { FileDirItem(it, it.getFilenameFromPath()) } as ArrayList<FileDirItem>
            context.deleteFiles(fileDirItems) {
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
            val position = items.indexOfFirst { it.id == key }
            if (position != -1) {
                positions.add(position + positionOffset)
            }
            playlistsToDelete.add(playlist)
        }

        items.removeAll(playlistsToDelete.toSet())

        ensureBackgroundThread {
            context.audioHelper.deletePlaylists(playlistsToDelete)
            context.runOnUiThread {
                removeSelectedItems(positions)
            }

            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }

    private fun getItemWithKey(key: Int): Playlist? = items.firstOrNull { it.id == key }

    private fun showRenameDialog() {
        NewPlaylistDialog(context, items[getItemKeyPosition(selectedKeys.first())]) {
            context.runOnUiThread {
                finishActMode()
            }
        }
    }

    private fun setupView(view: View, playlist: Playlist) {
        ItemPlaylistBinding.bind(view).apply {
            root.setupViewBackground(context)
            playlistFrame.isSelected = selectedKeys.contains(playlist.id)
            playlistTitle.text = if (textToHighlight.isEmpty()) playlist.title else playlist.title.highlightTextPart(textToHighlight, properPrimaryColor)
            playlistTitle.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, playlist.trackCount, playlist.trackCount)
            playlistTracks.text = tracks
            playlistTracks.setTextColor(textColor)
        }
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(context.config.playlistSorting) ?: ""
}
