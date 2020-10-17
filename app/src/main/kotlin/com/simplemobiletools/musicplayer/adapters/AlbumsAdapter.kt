package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.addTracksToPlaylist
import com.simplemobiletools.musicplayer.extensions.getTracksSync
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.AlbumSection
import com.simplemobiletools.musicplayer.models.ListItem
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_album.view.*
import kotlinx.android.synthetic.main.item_section.view.*
import kotlinx.android.synthetic.main.item_song.view.*
import java.util.*

// we show both albums and individual tracks here
class AlbumsAdapter(activity: SimpleActivity, val items: ArrayList<ListItem>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private val ITEM_SECTION = 0
    private val ITEM_ALBUM = 1
    private val ITEM_TRACK = 2

    private val placeholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset_padded, textColor)
    private val placeholderBig = resources.getColoredDrawableWithColor(R.drawable.ic_headset, textColor)

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_albums

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            ITEM_SECTION -> R.layout.item_section
            ITEM_ALBUM -> R.layout.item_album
            else -> R.layout.item_song
        }

        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        val allowClicks = item !is AlbumSection
        holder.bindView(item, allowClicks, allowClicks) { itemView, layoutPosition ->
            when (item) {
                is AlbumSection -> setupSection(itemView, item)
                is Album -> setupAlbum(itemView, item)
                else -> setupTrack(itemView, item as Track)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AlbumSection -> ITEM_SECTION
            is Album -> ITEM_ALBUM
            else -> ITEM_TRACK
        }
    }

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
        }
    }

    override fun getSelectableItemCount() = items.filter { it !is AlbumSection }.size

    override fun getIsItemSelectable(position: Int) = items[position] !is AlbumSection

    override fun getItemSelectionKey(position: Int) = (items.getOrNull(position))?.hashCode()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun addToPlaylist() {
        ensureBackgroundThread {
            val tracks = getSelectedTracks()
            getSelectedAlbums().forEach {
                tracks.addAll(activity.getTracksSync(it.id))
            }

            activity.runOnUiThread {
                activity.addTracksToPlaylist(tracks) {
                    finishActMode()
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun getSelectedAlbums(): List<Album> = items.filter { it is Album && selectedKeys.contains(it.hashCode()) }.toList() as List<Album>

    private fun getSelectedTracks(): ArrayList<Track> = items.filter { it is Track && selectedKeys.contains(it.hashCode()) }.toMutableList() as ArrayList<Track>

    private fun setupAlbum(view: View, album: Album) {
        view.apply {
            album_frame?.isSelected = selectedKeys.contains(album.hashCode())
            album_title.text = album.title
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

    private fun setupTrack(view: View, track: Track) {
        view.apply {
            song_frame?.isSelected = selectedKeys.contains(track.hashCode())
            song_title.text = track.title
            song_title.setTextColor(textColor)

            song_id.beGone()
            song_image.beVisible()
            song_duration.text = track.duration.getFormattedDuration()
            song_duration.setTextColor(textColor)

            if (track.coverArt.isEmpty()) {
                song_image.setImageDrawable(placeholder)
            } else {
                val options = RequestOptions()
                    .error(placeholder)
                    .transform(CenterCrop(), RoundedCorners(8))

                Glide.with(activity)
                    .load(track.coverArt)
                    .apply(options)
                    .into(findViewById(R.id.song_image))
            }
        }
    }

    private fun setupSection(view: View, section: AlbumSection) {
        view.apply {
            item_section.text = section.title
            item_section.setTextColor(textColor)
        }
    }
}
