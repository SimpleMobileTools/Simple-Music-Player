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
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.models.AlbumHeader
import com.simplemobiletools.musicplayer.models.ListItem
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.item_album_header.view.*
import kotlinx.android.synthetic.main.item_song.view.*
import java.util.*

class SongsAdapter(activity: SimpleActivity, val items: ArrayList<ListItem>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private val ITEM_HEADER = 0
    private val ITEM_TRACK = 1

    private val placeholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, textColor)

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_songs

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            ITEM_HEADER -> R.layout.item_album_header
            else -> R.layout.item_song
        }

        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        val isTrack = item !is AlbumHeader
        holder.bindView(item, isTrack, isTrack) { itemView, layoutPosition ->
            when (item) {
                is AlbumHeader -> setupHeader(itemView, item)
                else -> setupSong(itemView, item as Song)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AlbumHeader -> ITEM_HEADER
            else -> ITEM_TRACK
        }
    }

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = items.size - 1

    override fun getIsItemSelectable(position: Int) = position != 0

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun getItemWithKey(key: Int) = items.firstOrNull { it.hashCode() == key }

    private fun setupSong(view: View, song: Song) {
        view.apply {
            song_frame?.isSelected = selectedKeys.contains(song.hashCode())
            song_title.text = song.title

            arrayOf(song_id, song_title, song_duration).forEach {
                it.setTextColor(textColor)
            }

            song_id.text = song.trackId.toString()
            song_duration.text = song.duration.getFormattedDuration()
        }
    }

    private fun setupHeader(view: View, header: AlbumHeader) {
        view.apply {
            album_title.text = header.title
            album_title.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks, header.songCnt, header.songCnt)
            var year = ""
            if (header.year != 0) {
                year = "${header.year} • "
            }

            album_meta.text = "$year$tracks • ${header.duration.getFormattedDuration(true)}"

            val options = RequestOptions()
                .error(placeholder)
                .transform(CenterCrop(), RoundedCorners(16))

            Glide.with(activity)
                .load(header.coverArt)
                .apply(options)
                .into(findViewById(R.id.album_image))
        }
    }
}
