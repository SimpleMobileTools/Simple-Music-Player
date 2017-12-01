package com.simplemobiletools.musicplayer.adapters

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.extensions.beInvisibleIf
import com.simplemobiletools.commons.extensions.deleteFiles
import com.simplemobiletools.commons.extensions.shareUris
import com.simplemobiletools.commons.views.MyRecyclerView
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

class SongAdapter(activity: SimpleActivity, var songs: ArrayList<Song>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit)
    : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    private var currentSongIndex = 0
    var isThirdPartyIntent = false

    override fun getActionMenuId() = R.menu.cab

    override fun prepareItemSelection(view: View) {}

    override fun markItemSelection(select: Boolean, view: View?) {
        view?.song_frame?.isSelected = select
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = createViewHolder(R.layout.item_song, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = songs[position]
        val view = holder.bindView(song, !isThirdPartyIntent) { itemView, layoutPosition ->
            setupView(itemView, song, layoutPosition)
        }
        bindViewHolder(holder, position, view)
    }

    override fun getItemCount() = songs.size

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = selectedPositions.size == 1
        }
    }

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_properties -> showProperties()
            R.id.cab_rename -> displayEditDialog()
            R.id.cab_share -> shareItems()
            R.id.cab_select_all -> selectAll()
            R.id.cab_remove_from_playlist -> removeFromPlaylist()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = songs.size

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
                activity.runOnUiThread { finishActMode() }
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

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            deleteSongs()
            finishActMode()
        }
    }

    private fun deleteSongs() {
        if (selectedPositions.isEmpty()) {
            return
        }

        val paths = ArrayList<String>(selectedPositions.size)
        val files = ArrayList<File>(selectedPositions.size)
        val removeSongs = ArrayList<Song>(selectedPositions.size)

        val SAFPath = songs[selectedPositions.first()].path
        activity.handleSAFDialog(File(SAFPath)) {
            selectedPositions.sortedDescending().forEach {
                val song = songs[it]
                paths.add(song.path)
                files.add(File(song.path))
                removeSongs.add(song)
                if (song == MusicService.mCurrSong) {
                    activity.sendIntent(NEXT)
                }
            }

            songs.removeAll(removeSongs)
            activity.dbHelper.removeSongsFromPlaylist(paths, -1)
            activity.deleteFiles(files) { }
            removeSelectedItems()
        }
    }

    private fun removeFromPlaylist() {
        if (selectedPositions.isEmpty()) {
            return
        }

        val paths = ArrayList<String>(selectedPositions.size)
        val removeSongs = ArrayList<Song>(selectedPositions.size)

        selectedPositions.sortedDescending().forEach {
            val song = songs[it]
            paths.add(song.path)
            removeSongs.add(song)
            if (song == MusicService.mCurrSong) {
                activity.sendIntent(NEXT)
            }
        }

        activity.config.addIgnoredPaths(paths)
        songs.removeAll(removeSongs)
        activity.dbHelper.removeSongsFromPlaylist(paths)
        removeSelectedItems()
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

    private fun setupView(view: View, song: Song, layoutPosition: Int) {
        view.apply {
            song_title.text = song.title
            song_title.setTextColor(textColor)

            song_artist.text = song.artist
            song_artist.setTextColor(textColor)

            song_note_image.beInvisibleIf(currentSongIndex != layoutPosition)
            if (currentSongIndex == layoutPosition) {
                song_note_image.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
            }
        }
    }
}
