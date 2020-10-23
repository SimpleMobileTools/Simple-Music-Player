package com.simplemobiletools.musicplayer.adapters

import android.content.Intent
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.EditDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.item_navigation.view.*
import kotlinx.android.synthetic.main.item_old_song.view.*

class OldSongAdapter(activity: SimpleActivity, var songs: ArrayList<Track>, val transparentView: View,
                     recyclerView: MyRecyclerView, fastScroller: FastScroller, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    private val VIEW_TYPE_TRANSPARENT = 0
    private val VIEW_TYPE_NAVIGATION = 1
    private val VIEW_TYPE_ITEM = 2

    private val placeholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, textColor)

    private var currentSongIndex = 0
    private var songsHashCode = songs.hashCode()
    private var currentSong: Track? = null
    private var textToHighlight = ""

    private var transparentViewHolder: TransparentViewHolder? = null
    private var transparentViewHeight = 0

    private var navigationView: ViewGroup? = null
    private var navigationViewHolder: NavigationViewHolder? = null
    private var navigationViewHeight = 0

    init {
        setupDragListener(true)
        positionOffset = LIST_HEADERS_COUNT
    }

    override fun getActionMenuId() = R.menu.cab

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TRANSPARENT -> getTransparentViewHolder()
            VIEW_TYPE_NAVIGATION -> getNavigationViewHolder()
            else -> createViewHolder(R.layout.item_old_song, parent)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> VIEW_TYPE_TRANSPARENT
            1 -> VIEW_TYPE_NAVIGATION
            else -> VIEW_TYPE_ITEM
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder !is TransparentViewHolder && holder !is NavigationViewHolder) {
            val song = songs.getOrNull(position - LIST_HEADERS_COUNT) ?: return
            holder.bindView(song, true, true) { itemView, layoutPosition ->
                setupView(itemView, song, layoutPosition)
            }
            bindViewHolder(holder)
        } else {
            holder.itemView.tag = holder
        }
    }

    override fun getItemCount() = songs.size + LIST_HEADERS_COUNT

    private fun getItemWithKey(key: Int): Track? = songs.firstOrNull { it.path.hashCode() == key }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected() && getSelectedSongs().firstOrNull()?.path?.startsWith("content://") != true
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

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

    override fun getIsItemSelectable(position: Int) = position >= 0

    override fun getItemSelectionKey(position: Int) = songs.getOrNull(position)?.path?.hashCode()

    override fun getItemKeyPosition(key: Int) = songs.indexOfFirst { it.path.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    fun searchOpened() {
        transparentViewHeight = transparentView.height
        transparentView.layoutParams?.height = 0

        navigationViewHeight = navigationView?.height ?: 0
        navigationView?.layoutParams?.height = 0
    }

    fun searchClosed() {
        transparentView.layoutParams?.height = transparentViewHeight
        navigationView?.layoutParams?.height = navigationViewHeight
        notifyDataSetChanged()
    }

    private fun getTransparentViewHolder(): TransparentViewHolder {
        if (transparentViewHolder == null) {
            transparentViewHolder = TransparentViewHolder(transparentView)
        }

        return transparentViewHolder!!
    }

    private fun getNavigationViewHolder(): NavigationViewHolder {
        if (navigationView == null) {
            navigationView = activity.layoutInflater.inflate(R.layout.item_navigation, null) as ViewGroup
        }

        if (navigationViewHolder == null) {
            navigationViewHolder = NavigationViewHolder(navigationView!!)
        }

        return navigationViewHolder!!
    }

    private fun showProperties() {
        if (selectedKeys.size <= 1) {
            PropertiesDialog(activity, getFirstSelectedItemPath())
        } else {
            val paths = getSelectedSongs().map { it.path }
            PropertiesDialog(activity, paths)
        }
    }

    private fun displayEditDialog() {
        EditDialog(activity, getSelectedSongs().first()) {
            if (it == MusicService.mCurrTrack) {
                Intent(activity, MusicService::class.java).apply {
                    putExtra(EDITED_TRACK, it)
                    action = EDIT
                    activity.startService(this)
                }
            }

            activity.sendIntent(REFRESH_LIST)
            activity.runOnUiThread {
                finishActMode()
            }
        }
    }

    private fun shareItems() {
        val paths = getSelectedSongs().map { it.path } as ArrayList<String>
        activity.sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            ensureBackgroundThread {
                deleteSongs()
                activity.runOnUiThread {
                    finishActMode()
                }
            }
        }
    }

    private fun deleteSongs() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val SAFPath = getFirstSelectedItemPath()
        activity.handleSAFDialog(SAFPath) {
            val files = ArrayList<FileDirItem>(selectedKeys.size)
            val removeSongs = ArrayList<Track>(selectedKeys.size)
            val positions = ArrayList<Int>()

            for (key in selectedKeys) {
                val song = getItemWithKey(key) ?: continue

                val position = songs.indexOfFirst { it.path.hashCode() == key }
                if (position != -1) {
                    positions.add(position + positionOffset)
                    files.add(FileDirItem(song.path))
                    removeSongs.add(song)
                    activity.tracksDAO.removeSongPath(song.path)
                }
            }

            positions.sortDescending()
            activity.runOnUiThread {
                removeSelectedItems(positions)
            }
            activity.deleteFiles(files)

            val songIds = removeSongs.map { it.path.hashCode() } as ArrayList<Int>
            Intent(activity, MusicService::class.java).apply {
                putExtra(TRACK_IDS, songIds)
                action = REMOVE_TRACK_IDS
                activity.startService(this)
            }

            songs.removeAll(removeSongs)
        }
    }

    private fun removeFromPlaylist() {
        if (selectedKeys.isEmpty()) {
            return
        }

        // remove the songs from playlist asap, so they dont get played at Next, if the currently playing song is removed from playlist
        val songIds = ArrayList<Int>(selectedKeys.size)
        for (key in selectedKeys) {
            val song = getItemWithKey(key) ?: continue
            songIds.add(song.path.hashCode())
        }

        Intent(activity, MusicService::class.java).apply {
            putExtra(TRACK_IDS, songIds)
            action = REMOVE_TRACK_IDS
            activity.startService(this)
        }

        val removeSongs = ArrayList<Track>(selectedKeys.size)
        val positions = ArrayList<Int>()

        for (key in selectedKeys) {
            val song = getItemWithKey(key) ?: continue

            val position = songs.indexOfFirst { it.path.hashCode() == key }
            if (position != -1) {
                positions.add(position + positionOffset)
                removeSongs.add(song)
                if (song == MusicService.mCurrTrack) {
                    if (songs.size == removeSongs.size) {
                        activity.sendIntent(REMOVE_CURRENT_TRACK)
                    } else {
                        activity.sendIntent(NEXT)
                    }
                }
            }
        }

        val removePaths = removeSongs.map { it.path } as ArrayList<String>
        activity.config.addIgnoredPaths(removePaths)
        songs.removeAll(removeSongs)
        positions.sortDescending()
        removeSelectedItems(positions)
        ensureBackgroundThread {
            activity.tracksDAO.removeSongsFromPlaylists(removeSongs)
        }
    }

    private fun getFirstSelectedItemPath() = getSelectedSongs().firstOrNull()?.path ?: ""

    private fun getSelectedSongs(): ArrayList<Track> {
        val selectedSongs = ArrayList<Track>(selectedKeys.size)
        selectedKeys.forEach {
            getItemWithKey(it)?.apply {
                selectedSongs.add(this)
            }
        }
        return selectedSongs
    }

    fun updateSongs(newSongs: ArrayList<Track>, highlightText: String = "") {
        val newHashCode = newSongs.hashCode()
        if (newHashCode != songsHashCode) {
            songsHashCode = newHashCode
            textToHighlight = highlightText
            songs = newSongs
            currentSongIndex = -1
            notifyDataSetChanged()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
        fastScroller?.measureRecyclerView()
    }

    fun updateSong(song: Track?) {
        currentSong = song
        navigationView?.apply {
            song_info_title.text = song?.title ?: ""
            song_info_artist.text = song?.artist ?: ""
            song_progressbar.max = song?.duration ?: 0
            song_progressbar.progress = 0
        }
    }

    inner class TransparentViewHolder(view: View) : ViewHolder(view)

    inner class NavigationViewHolder(view: View) : ViewHolder(view)

    private fun setupView(view: View, song: Track, layoutPosition: Int) {
        view.apply {
            song_frame?.isSelected = selectedKeys.contains(song.path.hashCode())
            song_title.text = if (textToHighlight.isEmpty()) song.title else song.title.highlightTextPart(textToHighlight, adjustedPrimaryColor)
            song_title.setTextColor(textColor)

            song_artist.text = if (textToHighlight.isEmpty()) song.artist else song.artist.highlightTextPart(textToHighlight, adjustedPrimaryColor)
            song_artist.setTextColor(textColor)

            song_note_image.beInvisibleIf(currentSongIndex != layoutPosition)
            if (currentSongIndex == layoutPosition) {
                song_note_image.applyColorFilter(textColor)
            }

            val options = RequestOptions()
                .error(placeholder)
                .transform(CenterCrop(), RoundedCorners(8))

            Glide.with(activity)
                .load(song.coverArt)
                .apply(options)
                .into(findViewById(R.id.song_image))
        }
    }
}
