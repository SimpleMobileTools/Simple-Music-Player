package com.simplemobiletools.musicplayer.adapters

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.util.SparseArray
import android.view.*
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback
import com.bignerdranch.android.multiselector.MultiSelector
import com.bignerdranch.android.multiselector.SwappingHolder
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.extensions.beInvisibleIf
import com.simplemobiletools.commons.extensions.deleteFiles
import com.simplemobiletools.commons.extensions.shareUris
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.EditDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.EDIT
import com.simplemobiletools.musicplayer.helpers.EDITED_SONG
import com.simplemobiletools.musicplayer.helpers.NEXT
import com.simplemobiletools.musicplayer.helpers.REFRESH_LIST
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.item_song.view.*
import java.io.File
import java.util.*

class SongAdapter(val activity: SimpleActivity, var songs: ArrayList<Song>, val listener: ItemOperationsListener?, val itemClick: (Int) -> Unit) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {
    private val multiSelector = MultiSelector()

    private var actMode: ActionMode? = null
    private var itemViews = SparseArray<View>()
    private val selectedPositions = HashSet<Int>()

    private var currentSongIndex = 0
    var textColor = activity.config.textColor
    var isThirdPartyIntent = false

    fun toggleItemSelection(select: Boolean, pos: Int) {
        itemViews[pos]?.song_frame?.isSelected = select

        if (select) {
            selectedPositions.add(pos)
        } else {
            selectedPositions.remove(pos)
        }

        if (selectedPositions.isEmpty()) {
            actMode?.finish()
            return
        }

        updateTitle(selectedPositions.size)
    }

    private fun updateTitle(cnt: Int) {
        actMode?.title = "$cnt / ${songs.size}"
        actMode?.invalidate()
    }

    private val adapterListener = object : MyAdapterListener {
        override fun toggleItemSelectionAdapter(select: Boolean, position: Int) {
            toggleItemSelection(select, position)
        }

        override fun getSelectedPositions(): HashSet<Int> = selectedPositions
    }

    private val multiSelectorMode = object : ModalMultiSelectorCallback(multiSelector) {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.cab_properties -> showProperties()
                R.id.cab_rename -> displayEditDialog()
                R.id.cab_share -> shareItems()
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
            menuItem.isVisible = selectedPositions.size <= 1
            return true
        }

        override fun onDestroyActionMode(actionMode: ActionMode?) {
            super.onDestroyActionMode(actionMode)
            selectedPositions.forEach {
                itemViews[it]?.isSelected = false
            }
            selectedPositions.clear()
            actMode = null
        }
    }

    private fun showProperties() {
        if (selectedPositions.size <= 1) {
            PropertiesDialog(activity, songs[selectedPositions.first()].path)
        } else {
            val paths = ArrayList<String>()
            selectedPositions.forEach { paths.add(songs[it].path) }
            PropertiesDialog(activity, paths)
        }
    }

    private fun displayEditDialog() {
        if (selectedPositions.size == 1) {
            EditDialog(activity, songs[selectedPositions.first()]) {
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

    private fun shareItems() {
        val uris = ArrayList<Uri>()
        selectedPositions.forEach {
            val file = File(songs[it].path)
            uris.add(Uri.fromFile(file))
        }
        activity.shareUris(uris, BuildConfig.APPLICATION_ID)
    }

    private fun selectAll() {
        val cnt = songs.size
        for (i in 0 until cnt) {
            selectedPositions.add(i)
            notifyItemChanged(i)
        }
        updateTitle(cnt)
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            deleteSongs()
            actMode?.finish()
        }
    }

    private fun deleteSongs() {
        if (selectedPositions.isEmpty())
            return

        val paths = ArrayList<String>(selectedPositions.size)
        val files = ArrayList<File>(selectedPositions.size)
        val removeSongs = ArrayList<Song>(selectedPositions.size)

        activity.handleSAFDialog(File(songs[selectedPositions.first()].path)) {
            selectedPositions.sortedDescending().forEach {
                val song = songs[it]
                paths.add(song.path)
                files.add(File(song.path))
                removeSongs.add(song)
                notifyItemRemoved(it)
                if (song == MusicService.mCurrSong) {
                    activity.sendIntent(NEXT)
                }
            }

            songs.removeAll(removeSongs)
            selectedPositions.clear()
            activity.dbHelper.removeSongsFromPlaylist(paths, -1)
            activity.deleteFiles(files) { }
            activity.sendIntent(REFRESH_LIST)
        }
    }

    private fun removeFromPlaylist() {
        val paths = ArrayList<String>(selectedPositions.size)
        val removeSongs = ArrayList<Song>(selectedPositions.size)

        selectedPositions.sortedDescending().forEach {
            val song = songs[it]
            paths.add(song.path)
            removeSongs.add(song)
            notifyItemRemoved(it)
            if (song == MusicService.mCurrSong) {
                activity.sendIntent(NEXT)
            }
        }

        activity.config.addIgnoredPaths(paths)
        songs.removeAll(removeSongs)
        activity.dbHelper.removeSongsFromPlaylist(paths)
        actMode?.finish()
        activity.sendIntent(REFRESH_LIST)
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
        return ViewHolder(view, adapterListener, activity, multiSelectorMode, multiSelector, listener, itemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        itemViews.put(position, holder.bindView(songs[position], currentSongIndex, textColor, isThirdPartyIntent))
        toggleItemSelection(selectedPositions.contains(position), position)
        holder.itemView.tag = holder
    }

    override fun getItemCount() = songs.size

    fun selectItem(pos: Int) {
        toggleItemSelection(true, pos)
    }

    fun selectRange(from: Int, to: Int, min: Int, max: Int) {
        if (from == to) {
            (min..max).filter { it != from }
                    .forEach { toggleItemSelection(false, it) }
            return
        }

        if (to < from) {
            for (i in to..from)
                toggleItemSelection(true, i)

            if (min > -1 && min < to) {
                (min until to).filter { it != from }
                        .forEach { toggleItemSelection(false, it) }
            }
            if (max > -1) {
                for (i in from + 1..max)
                    toggleItemSelection(false, i)
            }
        } else {
            for (i in from..to)
                toggleItemSelection(true, i)

            if (max > -1 && max > to) {
                (to + 1..max).filter { it != from }
                        .forEach { toggleItemSelection(false, it) }
            }

            if (min > -1) {
                for (i in min until from)
                    toggleItemSelection(false, i)
            }
        }
    }

    class ViewHolder(view: View, val adapterListener: MyAdapterListener, val activity: SimpleActivity, val multiSelectorCallback: ModalMultiSelectorCallback,
                     val multiSelector: MultiSelector, val listener: ItemOperationsListener?, val itemClick: (Int) -> (Unit)) : SwappingHolder(view, MultiSelector()) {
        fun bindView(song: Song, currentSongIndex: Int, textColor: Int, isThirdPartyIntent: Boolean): View {
            itemView.apply {
                song_title.text = song.title
                song_title.setTextColor(textColor)

                song_artist.text = song.artist
                song_artist.setTextColor(textColor)

                song_note_image.beInvisibleIf(currentSongIndex != layoutPosition)
                if (currentSongIndex == layoutPosition) {
                    song_note_image.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
                }

                setOnClickListener { viewClicked() }

                if (!isThirdPartyIntent) {
                    setOnLongClickListener { viewLongClicked(); true }
                }
            }

            return itemView
        }

        private fun viewClicked() {
            if (multiSelector.isSelectable) {
                val isSelected = adapterListener.getSelectedPositions().contains(layoutPosition)
                adapterListener.toggleItemSelectionAdapter(!isSelected, layoutPosition)
            } else {
                itemClick(layoutPosition)
            }
        }

        private fun viewLongClicked() {
            if (listener != null) {
                if (!multiSelector.isSelectable) {
                    activity.startSupportActionMode(multiSelectorCallback)
                    adapterListener.toggleItemSelectionAdapter(true, layoutPosition)
                }
                listener.itemLongClicked(layoutPosition)
            }
        }
    }

    interface MyAdapterListener {
        fun toggleItemSelectionAdapter(select: Boolean, position: Int)

        fun getSelectedPositions(): HashSet<Int>
    }

    interface ItemOperationsListener {
        fun itemLongClicked(position: Int)
    }
}
