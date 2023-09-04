package com.simplemobiletools.musicplayer.adapters

import android.view.View
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ItemAlbumBinding
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getAlbumCoverArt
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Track

class AlbumsAdapter(activity: BaseSimpleActivity, items: ArrayList<Album>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    BaseMusicAdapter<Album>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun getActionMenuId() = R.menu.cab_albums

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlbumBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = items.getOrNull(position) ?: return
        holder.bindView(album, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, album)
        }
        bindViewHolder(holder)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_share -> shareFiles()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectedTracks(): List<Track> {
        return context.audioHelper.getAlbumTracks(getSelectedItems())
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(context) {
            ensureBackgroundThread {
                val selectedAlbums = getSelectedItems()
                val positions = selectedAlbums.mapNotNull { album -> items.indexOfFirstOrNull { it.id == album.id } } as ArrayList<Int>
                val tracks = context.audioHelper.getAlbumTracks(selectedAlbums)
                context.audioHelper.deleteAlbums(selectedAlbums)

                context.deleteTracks(tracks) {
                    context.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            if (items.size > it) {
                                items.removeAt(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupView(view: View, album: Album) {
        ItemAlbumBinding.bind(view).apply {
            root.setupViewBackground(context)
            albumFrame.isSelected = selectedKeys.contains(album.hashCode())
            albumTitle.text = if (textToHighlight.isEmpty()) album.title else album.title.highlightTextPart(textToHighlight, properPrimaryColor)
            albumTitle.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, album.trackCnt, album.trackCnt)
            albumTracks.text = tracks
            albumTracks.setTextColor(textColor)

            context.getAlbumCoverArt(album) { coverArt ->
                loadImage(albumImage, coverArt, placeholderBig)
            }
        }
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(context.config.albumSorting) ?: ""
}
