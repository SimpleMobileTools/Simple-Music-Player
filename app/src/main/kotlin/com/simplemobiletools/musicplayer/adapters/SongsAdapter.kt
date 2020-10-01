package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.item_song.view.*
import java.util.*

class SongsAdapter(activity: SimpleActivity, val songs: ArrayList<Song>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_songs

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_song, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = songs.getOrNull(position) ?: return
        holder.bindView(song, true, true) { itemView, layoutPosition ->
            setupView(itemView, song)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = songs.size

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = songs.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = songs.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = songs.indexOfFirst { it.id.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun getItemWithKey(key: Int): Song? = songs.firstOrNull { it.id.toInt() == key }

    private fun setupView(view: View, song: Song) {
        view.apply {
            song_frame?.isSelected = selectedKeys.contains(song.id)
            song_title.text = song.title
            song_title.setTextColor(textColor)
        }
    }
}
