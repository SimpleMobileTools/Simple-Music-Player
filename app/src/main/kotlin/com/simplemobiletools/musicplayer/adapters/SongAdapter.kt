package com.simplemobiletools.musicplayer.adapters

import android.content.Intent
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
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
import com.simplemobiletools.musicplayer.extensions.songsDAO
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.interfaces.SongListListener
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.item_navigation.view.*
import kotlinx.android.synthetic.main.item_song.view.*

class SongAdapter(activity: SimpleActivity, var songs: ArrayList<Song>, val listener: SongListListener, val transparentView: View,
                  recyclerView: MyRecyclerView, fastScroller: FastScroller, itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    private val VIEW_TYPE_TRANSPARENT = 0
    private val VIEW_TYPE_NAVIGATION = 1
    private val VIEW_TYPE_ITEM = 2

    private var currentSongIndex = 0
    private var songsHashCode = songs.hashCode()
    private var currentSong: Song? = null
    private var initialProgress = 0
    private var initialIsPlaying = false
    private var textToHighlight = ""

    private var transparentViewHolder: TransparentViewHolder? = null
    private var transparentViewHeight = 0

    private var navigationView: ViewGroup? = null
    private var navigationViewHolder: NavigationViewHolder? = null
    private var navigationViewHeight = 0

    var isThirdPartyIntent = false
    private var adjustedPrimaryColor = activity.getAdjustedPrimaryColor()

    init {
        setupDragListener(true)
        positionOffset = LIST_HEADERS_COUNT
    }

    override fun getActionMenuId() = R.menu.cab

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_TRANSPARENT -> getTransparentViewHolder()
            VIEW_TYPE_NAVIGATION -> getNavigationViewHolder()
            else -> createViewHolder(R.layout.item_song, parent)
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

    private fun getItemWithKey(key: Int): Song? = songs.firstOrNull { it.path.hashCode() == key }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = isOneItemSelected()
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
            initNavigationView()
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
            if (it == MusicService.mCurrSong) {
                Intent(activity, MusicService::class.java).apply {
                    putExtra(EDITED_SONG, it)
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
            val removeSongs = ArrayList<Song>(selectedKeys.size)
            val positions = ArrayList<Int>()

            for (key in selectedKeys) {
                val song = getItemWithKey(key) ?: continue

                val position = songs.indexOfFirst { it.path.hashCode() == key }
                if (position != -1) {
                    positions.add(position + positionOffset)
                    files.add(FileDirItem(song.path))
                    removeSongs.add(song)
                    activity.songsDAO.removeSongPath(song.path)
                    if (song == MusicService.mCurrSong) {
                        activity.sendIntent(RESET)
                    }
                }
            }

            positions.sortDescending()
            activity.runOnUiThread {
                removeSelectedItems(positions)
            }
            activity.deleteFiles(files)

            val songIds = removeSongs.map { it.path.hashCode() } as ArrayList<Int>
            Intent(activity, MusicService::class.java).apply {
                putExtra(SONG_IDS, songIds)
                action = REMOVE_SONG_IDS
                activity.startService(this)
            }

            songs.removeAll(removeSongs)
            if (songs.isEmpty()) {
                listener.refreshItems()
            }
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
            putExtra(SONG_IDS, songIds)
            action = REMOVE_SONG_IDS
            activity.startService(this)
        }

        val removeSongs = ArrayList<Song>(selectedKeys.size)
        val positions = ArrayList<Int>()

        for (key in selectedKeys) {
            val song = getItemWithKey(key) ?: continue

            val position = songs.indexOfFirst { it.path.hashCode() == key }
            if (position != -1) {
                positions.add(position + positionOffset)
                removeSongs.add(song)
                if (song == MusicService.mCurrSong) {
                    if (songs.size == removeSongs.size) {
                        activity.sendIntent(REMOVE_CURRENT_SONG)
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
            activity.songsDAO.removeSongsFromPlaylists(removeSongs)

            if (songs.isEmpty()) {
                listener.refreshItems()
            }
        }
    }

    private fun getFirstSelectedItemPath() = getSelectedSongs().firstOrNull()?.path ?: ""

    private fun getSelectedSongs(): ArrayList<Song> {
        val selectedSongs = ArrayList<Song>(selectedKeys.size)
        selectedKeys.forEach {
            getItemWithKey(it)?.apply {
                selectedSongs.add(this)
            }
        }
        return selectedSongs
    }

    fun updateSongs(newSongs: ArrayList<Song>, highlightText: String = "") {
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

    fun updateCurrentSongIndex(index: Int) {
        val correctIndex = index + LIST_HEADERS_COUNT
        val prevIndex = currentSongIndex
        currentSongIndex = -1
        notifyItemChanged(prevIndex)

        currentSongIndex = correctIndex
        if (index >= 0) {
            notifyItemChanged(correctIndex)
        }
    }

    fun updateSong(song: Song?) {
        currentSong = song
        navigationView?.apply {
            song_info_title.text = song?.title ?: ""
            song_info_artist.text = song?.artist ?: ""
            song_progressbar.max = song?.duration ?: 0
            song_progressbar.progress = 0
        }
    }

    fun removeCurrentSongFromPlaylist() {
        if (currentSong != null) {
            selectedKeys.clear()
            selectedKeys.add(currentSong!!.path.hashCode())
            removeFromPlaylist()
            selectedKeys.clear()
        }
    }

    fun deleteCurrentSong() {
        ConfirmationDialog(activity) {
            selectedKeys.clear()
            if (songs.isNotEmpty() && currentSong != null) {
                selectedKeys.add(currentSong!!.path.hashCode())
                activity.sendIntent(NEXT)
                ensureBackgroundThread {
                    deleteSongs()
                    selectedKeys.clear()
                }
            }
        }
    }

    private fun initNavigationView() {
        navigationView?.apply {
            shuffle_btn.setOnClickListener { listener.listToggleShuffle() }
            previous_btn.setOnClickListener { activity.sendIntent(PREVIOUS) }
            play_pause_btn.setOnClickListener { activity.sendIntent(PLAYPAUSE) }
            next_btn.setOnClickListener { activity.sendIntent(NEXT) }
            repeat_btn.setOnClickListener { listener.listToggleSongRepetition() }
            song_progress_current.setOnClickListener { activity.sendIntent(SKIP_BACKWARD) }
            song_progress_max.setOnClickListener { activity.sendIntent(SKIP_FORWARD) }

            updateColors()

            song_progressbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val duration = song_progressbar.max.getFormattedDuration()
                    val formattedProgress = progress.getFormattedDuration()
                    song_progress_current.text = formattedProgress
                    song_progress_max.text = duration
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    Intent(activity, MusicService::class.java).apply {
                        putExtra(PROGRESS, seekBar.progress)
                        action = SET_PROGRESS
                        activity.startService(this)
                    }
                }
            })

            updateSong(currentSong)
            updateSongProgress(initialProgress)
            updateSongState(initialIsPlaying)
        }
    }

    fun updateColors() {
        val config = activity.config
        textColor = config.textColor
        primaryColor = config.primaryColor
        backgroundColor = config.backgroundColor
        adjustedPrimaryColor = activity.getAdjustedPrimaryColor()
        navigationView?.apply {
            previous_btn.applyColorFilter(textColor)
            play_pause_btn.applyColorFilter(textColor)
            next_btn.applyColorFilter(textColor)
            repeat_btn.applyColorFilter(textColor)

            shuffle_btn.applyColorFilter(if (config.isShuffleEnabled) adjustedPrimaryColor else textColor)
            shuffle_btn.alpha = if (config.isShuffleEnabled) 1f else LOWER_ALPHA

            repeat_btn.applyColorFilter(if (config.repeatSong) adjustedPrimaryColor else textColor)
            repeat_btn.alpha = if (config.repeatSong) 1f else LOWER_ALPHA

            song_info_title.setTextColor(textColor)
            song_info_artist.setTextColor(textColor)
            song_progress_current.setTextColor(textColor)
            song_progress_max.setTextColor(textColor)
            song_progressbar.setColors(textColor, adjustedPrimaryColor, backgroundColor)
        }
    }

    fun updateSongState(isPlaying: Boolean) {
        if (navigationView == null) {
            initialIsPlaying = isPlaying
        }
        navigationView?.play_pause_btn?.setImageDrawable(resources.getDrawable(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector))
    }

    fun updateSongProgress(progress: Int) {
        if (navigationView == null) {
            initialProgress = progress
        }
        navigationView?.song_progressbar?.progress = progress
    }

    fun updateShuffle(enable: Boolean) {
        navigationView?.apply {
            shuffle_btn.applyColorFilter(if (enable) adjustedPrimaryColor else textColor)
            shuffle_btn.alpha = if (enable) 1f else LOWER_ALPHA
            shuffle_btn.contentDescription = resources.getString(if (enable) R.string.disable_shuffle else R.string.enable_shuffle)
        }
    }

    fun updateRepeatSong(repeat: Boolean) {
        navigationView?.apply {
            repeat_btn.applyColorFilter(if (repeat) adjustedPrimaryColor else textColor)
            repeat_btn.alpha = if (repeat) 1f else LOWER_ALPHA
            repeat_btn.contentDescription = resources.getString(if (repeat) R.string.disable_song_repetition else R.string.enable_song_repetition)
        }
    }

    inner class TransparentViewHolder(view: View) : ViewHolder(view)

    inner class NavigationViewHolder(view: View) : ViewHolder(view)

    private fun setupView(view: View, song: Song, layoutPosition: Int) {
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
        }
    }
}
