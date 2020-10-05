package com.simplemobiletools.musicplayer.adapters

import android.content.ContentUris
import android.net.Uri
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.ListItem
import kotlinx.android.synthetic.main.item_album.view.*
import java.util.*

// we show both albums and individual songs here
class AlbumsAdapter(activity: SimpleActivity, val items: ArrayList<ListItem>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_albums

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_album, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = items.getOrNull(position) ?: return
        holder.bindView(album, true, true) { itemView, layoutPosition ->
            setupView(itemView, album as Album)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = items.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = (items.getOrNull(position) as? Album)?.id

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { (it as Album).id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun getItemWithKey(key: Int): ListItem? = items.firstOrNull { (it as Album).id == key }

    private fun setupView(view: View, album: Album) {
        view.apply {
            album_frame?.isSelected = selectedKeys.contains(album.id)
            album_title.text = album.title
            album_title.setTextColor(textColor)

            val artworkUri = Uri.parse("content://media/external/audio/albumart")
            val albumArtUri = ContentUris.withAppendedId(artworkUri, album.id.toLong())

            val options = RequestOptions()
                .transform(CenterCrop(), RoundedCorners(16))

            Glide.with(activity)
                .load(albumArtUri)
                .apply(options)
                .into(findViewById(R.id.album_image))
        }
    }
}
