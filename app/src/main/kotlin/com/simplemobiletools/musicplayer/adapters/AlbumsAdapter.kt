package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.models.Album
import kotlinx.android.synthetic.main.item_album.view.*
import java.util.*

class AlbumsAdapter(activity: SimpleActivity, val albums: ArrayList<Album>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

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

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = albums.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = albums.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = albums.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun getItemWithKey(key: Int): Album? = albums.firstOrNull { it.id == key }

    private fun setupView(view: View, album: Album) {
        view.apply {
            album_frame?.isSelected = selectedKeys.contains(album.id)
            album_title.text = album.title
            album_title.setTextColor(textColor)
        }
    }
}
