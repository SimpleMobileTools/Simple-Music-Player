package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.addTracksToPlaylist
import com.simplemobiletools.musicplayer.extensions.addTracksToQueue
import com.simplemobiletools.musicplayer.extensions.getAlbumTracksSync
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_album.view.*
import java.util.*

class AlbumsAdapter(activity: SimpleActivity, var albums: ArrayList<Album>, recyclerView: MyRecyclerView, fastScroller: FastScroller, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    private var textToHighlight = ""
    private val placeholderBig = resources.getColoredDrawableWithColor(R.drawable.ic_headset, textColor)

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
        }
    }

    override fun getSelectableItemCount() = albums.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = albums.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = albums.indexOfFirst { it.id == key }

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
        val tracks = ArrayList<Track>()
        getSelectedAlbums().forEach {
            tracks.addAll(activity.getAlbumTracksSync(it.id))
        }
        return tracks
    }

    private fun getSelectedAlbums(): List<Album> = albums.filter { selectedKeys.contains(it.id) }.toList()

    fun updateItems(newItems: ArrayList<Album>, highlightText: String = "") {
        if (newItems.hashCode() != albums.hashCode()) {
            albums = newItems.clone() as ArrayList<Album>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
        fastScroller?.measureRecyclerView()
    }

    private fun setupView(view: View, album: Album) {
        view.apply {
            album_frame?.isSelected = selectedKeys.contains(album.id)
            album_title.text = if (textToHighlight.isEmpty()) album.title else album.title.highlightTextPart(textToHighlight, adjustedPrimaryColor)
            album_title.setTextColor(textColor)

            val options = RequestOptions()
                .error(placeholderBig)
                .transform(CenterCrop(), RoundedCorners(16))

            Glide.with(activity)
                .load(album.coverArt)
                .apply(options)
                .into(findViewById(R.id.album_image))
        }
    }
}
