package com.simplemobiletools.musicplayer.adapters

import android.content.Intent
import android.graphics.PorterDuff
import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.view.*
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback
import com.bignerdranch.android.multiselector.MultiSelector
import com.bignerdranch.android.multiselector.SwappingHolder
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.extensions.beInvisible
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.deleteFiles
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.EditDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.EDIT
import com.simplemobiletools.musicplayer.helpers.EDITED_SONG
import com.simplemobiletools.musicplayer.helpers.REFRESH_LIST
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.item_song.view.*
import java.io.File
import java.util.*

class SongAdapter(val activity: SimpleActivity, var songs: ArrayList<Song>, val itemClick: (Int) -> Unit) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {
    val multiSelector = MultiSelector()
    val views = ArrayList<View>()

    companion object {
        var actMode: ActionMode? = null
        val markedItems = HashSet<Int>()
        var currentSongIndex = 0
        var textColor = 0
        var itemCnt = 0

        fun toggleItemSelection(itemView: View, select: Boolean, pos: Int = -1) {
            itemView.song_frame.isSelected = select
            if (pos == -1)
                return

            if (select)
                markedItems.add(pos)
            else
                markedItems.remove(pos)
        }

        fun updateTitle(cnt: Int) {
            actMode?.title = "$cnt / $itemCnt"
        }
    }

    init {
        textColor = activity.config.textColor
        itemCnt = songs.size
    }

    val multiSelectorMode = object : ModalMultiSelectorCallback(multiSelector) {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.cab_properties -> showProperties()
                R.id.cab_rename -> displayEditDialog()
                R.id.cab_select_all -> selectAll()
                R.id.cab_remove_from_playlist -> removeFromPlaylist()
                R.id.cab_delete -> askConfirmDelete()
                else -> return false
            }
            return true
        }

        override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
            super.onCreateActionMode(actionMode, menu)
            actMode = actionMode
            activity.menuInflater.inflate(R.menu.cab, menu)
            return true
        }

        override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu): Boolean {
            val menuItem = menu.findItem(R.id.cab_rename)
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
                if (it == MusicService.mCurrSong) {
                    Intent(activity, MusicService::class.java).apply {
                        putExtra(EDITED_SONG, it)
                        action = EDIT
                        activity.startService(this)
                    }
                }

                activity.sendIntent(REFRESH_LIST)
                activity.runOnUiThread { actMode?.finish() }
            }
        }
    }

    private fun selectAll() {
        val cnt = songs.size
        for (i in 0..cnt - 1) {
            markedItems.add(i)
            multiSelector.setSelected(i, 0, true)
            notifyItemChanged(i)
        }
        updateTitle(cnt)
        actMode?.invalidate()
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
        val files = ArrayList<File>(selections.size)
        val removeSongs = ArrayList<Song>(selections.size)

        activity.handleSAFDialog(File(songs[selections[0]].path)) {
            selections.reverse()
            selections.forEach {
                val song = songs[it]
                paths.add(song.path)
                files.add(File(song.path))
                removeSongs.add(song)
                notifyItemRemoved(it)
            }

            songs.removeAll(removeSongs)
            markedItems.clear()
            itemCnt = songs.size
            activity.dbHelper.removeSongsFromPlaylist(paths, -1)
            activity.deleteFiles(files) { }
        }
    }

    private fun removeFromPlaylist() {
        val selections = multiSelector.selectedPositions
        val paths = ArrayList<String>(selections.size)
        val removeSongs = ArrayList<Song>(selections.size)

        selections.reverse()
        selections.forEach {
            val song = songs[it]
            paths.add(song.path)
            removeSongs.add(song)
            notifyItemRemoved(it)
        }

        songs.removeAll(removeSongs)
        markedItems.clear()
        itemCnt = songs.size
        activity.dbHelper.removeSongsFromPlaylist(paths)
        actMode?.finish()
    }

    fun updateSongs(newSongs: ArrayList<Song>) {
        songs = newSongs
        currentSongIndex = -1
        notifyDataSetChanged()
    }

    fun updateCurrentSongIndex(index: Int) {
        val prevIndex = currentSongIndex
        currentSongIndex = -1
        notifyItemChanged(prevIndex)

        currentSongIndex = index
        if (index >= 0)
            notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.item_song, parent, false)
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
                song_title.setTextColor(textColor)

                song_artist.text = song.artist
                song_artist.setTextColor(textColor)

                if (currentSongIndex == pos) {
                    song_note_image.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
                    song_note_image.beVisible()
                } else {
                    song_note_image.beInvisible()
                }
                toggleItemSelection(itemView, markedItems.contains(pos), pos)

                setOnClickListener { viewClicked(multiSelector, pos) }
                setOnLongClickListener {
                    if (!multiSelector.isSelectable) {
                        activity.startSupportActionMode(multiSelectorCallback)
                        multiSelector.setSelected(this@ViewHolder, true)
                        updateTitle(multiSelector.selectedPositions.size)
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
                    updateTitle(selectedCnt)
                }
                actMode?.invalidate()
            } else {
                itemClick(pos)
            }
        }
    }
}
