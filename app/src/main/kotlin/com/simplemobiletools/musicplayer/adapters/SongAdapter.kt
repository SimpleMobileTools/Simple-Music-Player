package com.simplemobiletools.musicplayer.adapters

import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.view.*
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback
import com.bignerdranch.android.multiselector.MultiSelector
import com.bignerdranch.android.multiselector.SwappingHolder
import com.simplemobiletools.filepicker.dialogs.ConfirmationDialog
import com.simplemobiletools.filepicker.extensions.scanPaths
import com.simplemobiletools.fileproperties.dialogs.PropertiesDialog
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.EditDialog
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.REFRESH_LIST
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.song.view.*
import java.io.File
import java.util.*

class SongAdapter(val activity: SimpleActivity, var songs: ArrayList<Song>, val itemClick: (Int) -> Unit) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {
    val multiSelector = MultiSelector()
    val views = ArrayList<View>()

    companion object {
        var actMode: ActionMode? = null
        val markedItems = HashSet<Int>()

        fun toggleItemSelection(itemView: View, select: Boolean, pos: Int = -1) {
            itemView.song_frame.isSelected = select
            if (pos == -1)
                return

            if (select)
                markedItems.add(pos)
            else
                markedItems.remove(pos)
        }
    }

    val multiSelectorMode = object : ModalMultiSelectorCallback(multiSelector) {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.cab_properties -> {
                    showProperties()
                    true
                }
                R.id.cab_edit -> {
                    displayEditDialog()
                    true
                }
                R.id.cab_delete -> {
                    askConfirmDelete()
                    true
                }
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

    private fun showProperties() {
        val selections = multiSelector.selectedPositions
        if (selections.size <= 1) {
            PropertiesDialog(activity, songs[selections[0]].path)
        } else {
            val paths = ArrayList<String>()
            selections.forEach { paths.add(songs[it].path) }
            PropertiesDialog(activity, paths)
        }
    }

    private fun displayEditDialog() {
        val selections = multiSelector.selectedPositions
        if (selections.size == 1) {
            EditDialog(activity, songs[selections[0]]) {
                activity.sendIntent(REFRESH_LIST)
                activity.runOnUiThread { actMode?.finish() }
            }
        }
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            actMode?.finish()
            deleteSongs()
        }
    }

    private fun deleteSongs() {
        val selections = multiSelector.selectedPositions
        val paths = ArrayList<String>(selections.size)
        selections.forEach { paths.add(songs[it].path) }
        for (path in paths) {
            File(path).delete()
        }

        activity.scanPaths(paths) {
            activity.sendIntent(REFRESH_LIST)
        }
    }

    fun updateSongs(newSongs: ArrayList<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.song, parent, false)
        return ViewHolder(activity, view, itemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        views.add(holder.bindView(multiSelectorMode, multiSelector, songs[position], position))
    }

    override fun getItemCount() = songs.size

    class ViewHolder(val activity: SimpleActivity, view: View, val itemClick: (Int) -> (Unit)) : SwappingHolder(view, MultiSelector()) {
        fun bindView(multiSelectorCallback: ModalMultiSelectorCallback, multiSelector: MultiSelector, song: Song, pos: Int): View {
            itemView.apply {
                song_title.text = song.title
                song_artist.text = song.artist
                toggleItemSelection(itemView, markedItems.contains(pos), pos)

                setOnClickListener { viewClicked(multiSelector, pos) }
                setOnLongClickListener {
                    if (!multiSelector.isSelectable) {
                        activity.startSupportActionMode(multiSelectorCallback)
                        multiSelector.setSelected(this@ViewHolder, true)
                        actMode?.title = multiSelector.selectedPositions.size.toString()
                        toggleItemSelection(itemView, true, pos)
                        actMode?.invalidate()
                    }
                    true
                }
            }

            return itemView
        }

        fun viewClicked(multiSelector: MultiSelector, pos: Int) {
            if (multiSelector.isSelectable) {
                val isSelected = multiSelector.selectedPositions.contains(layoutPosition)
                multiSelector.setSelected(this, !isSelected)
                toggleItemSelection(itemView, !isSelected, pos)

                val selectedCnt = multiSelector.selectedPositions.size
                if (selectedCnt == 0) {
                    actMode?.finish()
                } else {
                    actMode?.title = selectedCnt.toString()
                }
                actMode?.invalidate()
            } else {
                itemClick(pos)
            }
        }
    }
}
