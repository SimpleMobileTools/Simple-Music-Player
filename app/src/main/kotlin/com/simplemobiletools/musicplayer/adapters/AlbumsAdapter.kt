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
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_album.view.album_frame
import kotlinx.android.synthetic.main.item_album.view.album_title
import kotlinx.android.synthetic.main.item_album.view.album_tracks

class AlbumsAdapter(activity: BaseSimpleActivity, items: ArrayList<Album>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    BaseMusicAdapter<Album>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_albums

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_album, parent)

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
        return ctx.audioHelper.getAlbumTracks(getSelectedItems())
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(ctx) {
            ensureBackgroundThread {
                val selectedAlbums = getSelectedItems()
                val positions = selectedAlbums.mapNotNull { album -> items.indexOfFirstOrNull { it.id == album.id } } as ArrayList<Int>
                val tracks = ctx.audioHelper.getAlbumTracks(selectedAlbums)
                ctx.audioHelper.deleteAlbums(selectedAlbums)

                ctx.deleteTracks(tracks) {
                    ctx.runOnUiThread {
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
        view.apply {
            setupViewBackground(ctx)
            album_frame?.isSelected = selectedKeys.contains(album.hashCode())
            album_title.text = if (textToHighlight.isEmpty()) album.title else album.title.highlightTextPart(textToHighlight, properPrimaryColor)
            album_title.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, album.trackCnt, album.trackCnt)
            album_tracks.text = tracks
            album_tracks.setTextColor(textColor)

            ctx.getAlbumCoverArt(album) { coverArt ->
                loadImage(findViewById(R.id.album_image), coverArt, placeholderBig)
            }
        }
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(ctx.config.albumSorting) ?: ""
}
