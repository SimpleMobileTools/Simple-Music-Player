package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
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

class AlbumsAdapter(activity: BaseSimpleActivity, var albums: ArrayList<Album>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textToHighlight = ""
    private val placeholderBig = resources.getBiggerPlaceholder(textColor)
    private val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_albums

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_album, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albums.getOrNull(position) ?: return
        holder.bindView(album, true, true) { itemView, layoutPosition ->
            setupView(itemView, album)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = albums.size

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = albums.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = albums.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = albums.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun addToPlaylist() {
        ensureBackgroundThread {
            val allSelectedTracks = getAllSelectedTracks()
            activity.runOnUiThread {
                activity.addTracksToPlaylist(allSelectedTracks) {
                    finishActMode()
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun addToQueue() {
        ensureBackgroundThread {
            activity.addTracksToQueue(getAllSelectedTracks()) {
                finishActMode()
            }
        }
    }

    private fun getAllSelectedTracks(): ArrayList<Track> {
        return activity.audioHelper.getAlbumTracks(getSelectedAlbums())
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            ensureBackgroundThread {
                val selectedAlbums = getSelectedAlbums()
                val positions = selectedAlbums.mapNotNull { album -> albums.indexOfFirstOrNull { it.id == album.id } }.filter { it != -1 } as ArrayList<Int>
                val tracks = activity.audioHelper.getAlbumTracks(selectedAlbums)
                activity.audioHelper.deleteAlbums(selectedAlbums)

                activity.deleteTracks(tracks) {
                    activity.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            if (albums.size > it) {
                                albums.removeAt(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getSelectedAlbums(): List<Album> = albums.filter { selectedKeys.contains(it.hashCode()) }.toList()

    fun updateItems(newItems: ArrayList<Album>, highlightText: String = "", forceUpdate: Boolean = false) {
        if (forceUpdate || newItems.hashCode() != albums.hashCode()) {
            albums = newItems.clone() as ArrayList<Album>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, album: Album) {
        view.apply {
            setupViewBackground(activity)
            album_frame?.isSelected = selectedKeys.contains(album.hashCode())
            album_title.text = if (textToHighlight.isEmpty()) album.title else album.title.highlightTextPart(textToHighlight, properPrimaryColor)
            album_title.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, album.trackCnt, album.trackCnt)
            album_tracks.text = tracks
            album_tracks.setTextColor(textColor)

            activity.getAlbumCoverArt(album) { coverArt ->
                val options = RequestOptions()
                    .error(placeholderBig)
                    .transform(CenterCrop(), RoundedCorners(cornerRadius))

                activity.ensureActivityNotDestroyed {
                    Glide.with(activity)
                        .load(coverArt)
                        .apply(options)
                        .into(findViewById(R.id.album_image))
                }
            }
        }
    }

    override fun onChange(position: Int) = albums.getOrNull(position)?.getBubbleText() ?: ""
}
