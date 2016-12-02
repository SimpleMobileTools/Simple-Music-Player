package com.simplemobiletools.musicplayer.adapters

import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.view.*
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback
import com.bignerdranch.android.multiselector.MultiSelector
import com.bignerdranch.android.multiselector.SwappingHolder
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.activity_main.view.*
import java.util.*

class SongAdapter(val activity: SimpleActivity, val songs: ArrayList<Song>, val itemClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {
    val multiSelector = MultiSelector()
    val views = ArrayList<View>()

    companion object {
        var actMode: ActionMode? = null
        val markedItems = HashSet<Int>()

        fun toggleItemSelection(itemView: View, select: Boolean, pos: Int = -1) {
            /*itemView.item_frame.isSelected = select
            if (pos == -1)
                return

            if (select)
                markedItems.add(pos)
            else
                markedItems.remove(pos)*/
        }
    }

    val multiSelectorMode = object : ModalMultiSelectorCallback(multiSelector) {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                else -> false
            }
        }

        override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
            super.onCreateActionMode(actionMode, menu)
            actMode = actionMode
            activity.menuInflater.inflate(R.menu.cab, menu)
            return true
        }

        override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu): Boolean {
            val menuItem = menu.findItem(R.id.cab_edit)
            menuItem.isVisible = multiSelector.selectedPositions.size <= 1
            return true
        }

        override fun onDestroyActionMode(actionMode: ActionMode?) {
            super.onDestroyActionMode(actionMode)
            views.forEach { toggleItemSelection(it, false) }
            markedItems.clear()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.song, parent, false)
        return ViewHolder(activity, view, itemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        views.add(holder.bindView(multiSelectorMode, multiSelector, songs[position], position))
    }

    override fun getItemCount() = songs.size

    class ViewHolder(val activity: SimpleActivity, view: View, val itemClick: (Song) -> (Unit)) : SwappingHolder(view, MultiSelector()) {
        fun bindView(multiSelectorCallback: ModalMultiSelectorCallback, multiSelector: MultiSelector, song: Song, pos: Int): View {
            itemView.song_title.text = song.title
            itemView.song_artist.text = song.artist

            return itemView
        }
    }
}
