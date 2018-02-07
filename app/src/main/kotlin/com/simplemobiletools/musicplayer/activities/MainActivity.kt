package com.simplemobiletools.musicplayer.activities

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Environment
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.SeekBar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.interfaces.RecyclerScrollCallback
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.Release
import com.simplemobiletools.commons.views.MyLinearLayoutManager
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SongAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_navigation.*
import java.io.File
import java.util.*

class MainActivity : SimpleActivity(), RefreshRecyclerViewListener {
    private var isThirdPartyIntent = false
    private var songs = ArrayList<Song>()
    private var searchMenuItem: MenuItem? = null
    private var isSearchOpen = false
    private var artView: ViewGroup? = null

    private var actionbarSize = 0
    private var topArtHeight = 0

    private var storedUseEnglish = false

    lateinit var bus: Bus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched()
        isThirdPartyIntent = intent.action == Intent.ACTION_VIEW

        bus = BusProvider.instance
        bus.register(this)
        initSeekbarChangeListener()

        actionbarSize = getActionBarHeight()
        topArtHeight = resources.getDimensionPixelSize(R.dimen.top_art_height)
        artView = layoutInflater.inflate(R.layout.item_transparent, null) as ViewGroup

        songs_fastscroller.measureItemIndex = LIST_HEADERS_COUNT

        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                initializePlayer()
            } else {
                toast(R.string.no_storage_permissions)
            }
        }

        shuffle_btn.setOnClickListener { }
        previous_btn.setOnClickListener { sendIntent(PREVIOUS) }
        play_pause_btn.setOnClickListener { sendIntent(PLAYPAUSE) }
        next_btn.setOnClickListener { sendIntent(NEXT) }
        repeat_btn.setOnClickListener { }

        songs_playlist_empty_add_folder.setOnClickListener { addFolderToPlaylist() }
        volumeControlStream = AudioManager.STREAM_MUSIC
        checkWhatsNewDialog()
        storeStateVariables()

        songs_list.recyclerScrollCallback = object : RecyclerScrollCallback {
            override fun onScrolled(scrollY: Int) {
                top_navigation.beVisibleIf(scrollY > topArtHeight && !isSearchOpen)
                val minOverlayTransitionY = actionbarSize - topArtHeight
                art_holder.translationY = Math.min(0, Math.max(minOverlayTransitionY, -scrollY / 2)).toFloat()
                song_list_background.translationY = Math.max(0, -scrollY + topArtHeight).toFloat()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (storedUseEnglish != config.useEnglish) {
            restartActivity()
            return
        }

        setupIconColors()
        markCurrentSong()
        updateTextColors(main_holder)
        songs_playlist_empty_add_folder.setTextColor(getAdjustedPrimaryColor())
        songs_playlist_empty_add_folder.paintFlags = songs_playlist_empty_add_folder.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        songs_fastscroller.allowBubbleDisplay = config.showInfoBubble
        songs_fastscroller.updateBubbleColors()
        art_holder.background = ColorDrawable(config.backgroundColor)
        song_list_background.background = ColorDrawable(config.backgroundColor)
        top_navigation.background = ColorDrawable(config.backgroundColor)
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onStop() {
        super.onStop()
        searchMenuItem?.collapseActionView()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus.unregister(this)

        if (isThirdPartyIntent) {
            sendIntent(FINISH)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        setupSearch(menu)

        val songRepetition = menu.findItem(R.id.toggle_song_repetition)
        songRepetition.title = getString(if (config.repeatSong) R.string.disable_song_repetition else R.string.enable_song_repetition)
        songRepetition.icon = resources.getDrawable(if (config.repeatSong) R.drawable.ic_repeat else R.drawable.ic_repeat_off)

        val shuffle = menu.findItem(R.id.toggle_shuffle)
        shuffle.title = getString(if (config.isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        shuffle.icon = resources.getDrawable(if (config.isShuffleEnabled) R.drawable.ic_shuffle else R.drawable.ic_shuffle_off)

        val autoplay = menu.findItem(R.id.toggle_autoplay)
        autoplay.title = getString(if (config.autoplay) R.string.disable_autoplay else R.string.enable_autoplay)

        menu.apply {
            findItem(R.id.sort).isVisible = !isThirdPartyIntent
            findItem(R.id.toggle_song_repetition).isVisible = !isThirdPartyIntent
            findItem(R.id.toggle_shuffle).isVisible = !isThirdPartyIntent
            findItem(R.id.toggle_autoplay).isVisible = !isThirdPartyIntent
            findItem(R.id.sort).isVisible = !isThirdPartyIntent
            findItem(R.id.open_playlist).isVisible = !isThirdPartyIntent
            findItem(R.id.add_folder_to_playlist).isVisible = !isThirdPartyIntent
            findItem(R.id.add_file_to_playlist).isVisible = !isThirdPartyIntent
            findItem(R.id.remove_playlist).isVisible = !isThirdPartyIntent
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sort -> showSortingDialog()
            R.id.toggle_song_repetition -> toggleSongRepetition()
            R.id.open_playlist -> openPlaylist()
            R.id.toggle_shuffle -> toggleShuffle()
            R.id.toggle_autoplay -> toggleAutoplay()
            R.id.add_folder_to_playlist -> addFolderToPlaylist()
            R.id.add_file_to_playlist -> addFileToPlaylist()
            R.id.create_playlist_from_folder -> createPlaylistFromFolder()
            R.id.remove_playlist -> removePlaylist()
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun storeStateVariables() {
        config.apply {
            storedUseEnglish = useEnglish
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        searchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                songs_playlist_empty.text = getString(R.string.no_items_found)
                songs_playlist_empty_add_folder.beGone()
                art_holder.beGone()
                getSongsAdapter()?.searchOpened()
                top_navigation.beGone()
                isSearchOpen = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                songs_playlist_empty.text = getString(R.string.playlist_empty)
                songs_playlist_empty_add_folder.beVisibleIf(songs.isEmpty())
                art_holder.beVisibleIf(songs.isNotEmpty())
                if (isSearchOpen) {
                    searchQueryChanged("")
                    getSongsAdapter()?.searchClosed()
                }
                isSearchOpen = false
                return true
            }
        })
    }

    private fun launchSettings() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        startAboutActivity(R.string.app_name, LICENSE_KOTLIN or LICENSE_OTTO or LICENSE_MULTISELECT or LICENSE_STETHO, BuildConfig.VERSION_NAME)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this) {
            sendIntent(REFRESH_LIST)
        }
    }

    private fun toggleShuffle() {
        config.isShuffleEnabled = !config.isShuffleEnabled
        invalidateOptionsMenu()
        toast(if (config.isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
    }

    private fun toggleSongRepetition() {
        config.repeatSong = !config.repeatSong
        invalidateOptionsMenu()
        toast(if (config.repeatSong) R.string.song_repetition_enabled else R.string.song_repetition_disabled)
    }

    private fun toggleAutoplay() {
        config.autoplay = !config.autoplay
        invalidateOptionsMenu()
        toast(if (config.autoplay) R.string.autoplay_enabled else R.string.autoplay_disabled)
    }

    private fun removePlaylist() {
        if (config.currentPlaylist == DBHelper.ALL_SONGS_ID) {
            toast(R.string.all_songs_cannot_be_deleted)
        } else {
            val playlist = dbHelper.getPlaylistWithId(config.currentPlaylist)
            RemovePlaylistDialog(this, playlist) {
                if (it) {
                    val paths = dbHelper.getPlaylistSongPaths(config.currentPlaylist)
                    val files = paths.map(::File) as ArrayList<File>
                    dbHelper.removeSongsFromPlaylist(paths, -1)
                    deleteFiles(files) { }
                }
                dbHelper.removePlaylist(config.currentPlaylist)
            }
        }
    }

    private fun openPlaylist() {
        dbHelper.getPlaylists {
            val items = arrayListOf<RadioItem>()
            it.mapTo(items) { RadioItem(it.id, it.title) }
            items.add(RadioItem(-1, getString(R.string.create_playlist)))

            RadioGroupDialog(this, items, config.currentPlaylist) {
                if (it == -1) {
                    NewPlaylistDialog(this) {
                        playlistChanged(it)
                    }
                } else {
                    playlistChanged(it as Int)
                }
            }
        }
    }

    private fun addFolderToPlaylist() {
        FilePickerDialog(this, getFilePickerInitialPath(), pickFile = false) {
            toast(R.string.fetching_songs)
            Thread {
                val songs = getFolderSongs(File(it))
                dbHelper.addSongsToPlaylist(songs)
                sendIntent(REFRESH_LIST)
            }.start()
        }
    }

    private fun getFolderSongs(folder: File): ArrayList<String> {
        val songFiles = ArrayList<String>()
        val files = folder.listFiles() ?: return songFiles
        files.forEach {
            if (it.isDirectory) {
                songFiles.addAll(getFolderSongs(it))
            } else if (it.isAudioFast()) {
                songFiles.add(it.absolutePath)
            }
        }
        return songFiles
    }

    private fun addFileToPlaylist() {
        FilePickerDialog(this, getFilePickerInitialPath()) {
            if (it.isAudioFast()) {
                dbHelper.addSongToPlaylist(it)
                sendIntent(REFRESH_LIST)
            } else {
                toast(R.string.invalid_file_format)
            }
        }
    }

    private fun createPlaylistFromFolder() {
        FilePickerDialog(this, getFilePickerInitialPath(), pickFile = false) {
            Thread {
                createPlaylistFrom(it)
            }.start()
        }
    }

    private fun createPlaylistFrom(path: String) {
        val songs = getFolderSongs(File(path))
        if (songs.isEmpty()) {
            toast(R.string.folder_contains_no_audio)
            return
        }

        val folderName = path.getFilenameFromPath()
        var playlistName = folderName
        var curIndex = 1
        val playlistIdWithTitle = dbHelper.getPlaylistIdWithTitle(folderName)
        if (playlistIdWithTitle != -1) {
            while (true) {
                playlistName = "${folderName}_$curIndex"
                if (dbHelper.getPlaylistIdWithTitle(playlistName) == -1) {
                    break
                }

                curIndex++
            }
        }

        val playlist = Playlist(0, playlistName)
        val newPlaylistId = dbHelper.insertPlaylist(playlist)
        dbHelper.addSongsToPlaylist(songs, newPlaylistId)
        playlistChanged(newPlaylistId)
    }

    private fun getFilePickerInitialPath() = if (songs.isEmpty()) Environment.getExternalStorageDirectory().toString() else songs[0].path

    private fun initializePlayer() {
        if (isThirdPartyIntent) {
            Intent(this, MusicService::class.java).apply {
                data = intent.data
                action = INIT_PATH
                startService(this)
            }
        } else {
            sendIntent(INIT)
        }
    }

    private fun setupIconColors() {
        val textColor = config.textColor
        shuffle_btn.applyColorFilter(textColor)
        previous_btn.applyColorFilter(textColor)
        play_pause_btn.applyColorFilter(textColor)
        next_btn.applyColorFilter(textColor)
        repeat_btn.applyColorFilter(textColor)

        getSongsAdapter()?.textColor = textColor
        songs_fastscroller.updatePrimaryColor()
    }

    private fun songPicked(pos: Int) {
        setupIconColors()
        Intent(this, MusicService::class.java).apply {
            putExtra(SONG_POS, pos)
            action = PLAYPOS
            startService(this)
        }
    }

    private fun updateSongInfo(song: Song?) {
        song_info_title.text = song?.title ?: ""
        song_info_artist.text = song?.artist ?: ""
        song_progressbar.max = song?.duration ?: 0
        song_progressbar.progress = 0

        getSongsAdapter()?.updateSong(song)
        if (songs.isEmpty() && !isThirdPartyIntent) {
            toast(R.string.empty_playlist)
        }
    }

    private fun fillSongsListView(songs: ArrayList<Song>) {
        this.songs = songs
        val currAdapter = songs_list.adapter
        songs_fastscroller.setViews(songs_list) {
            val item = getSongsAdapter()?.songs?.getOrNull(it)
            songs_fastscroller.updateBubbleText(item?.getBubbleText() ?: "")
        }

        if (currAdapter == null) {
            SongAdapter(this@MainActivity, songs, this, artView!!, songs_list, songs_fastscroller) {
                songPicked(getSongIndex(it as Song))
            }.apply {
                setupDragListener(true)
                isThirdPartyIntent = this@MainActivity.isThirdPartyIntent
                addVerticalDividers(true)
                songs_list.adapter = this
            }
        } else {
            val state = (songs_list.layoutManager as MyLinearLayoutManager).onSaveInstanceState()
            (currAdapter as SongAdapter).apply {
                isThirdPartyIntent = this@MainActivity.isThirdPartyIntent
                updateSongs(songs)
            }
            (songs_list.layoutManager as MyLinearLayoutManager).onRestoreInstanceState(state)
        }
        markCurrentSong()

        songs_list.beVisibleIf(songs.isNotEmpty())
        art_holder.beVisibleIf(songs_list.isVisible())
        songs_playlist_empty.beVisibleIf(songs.isEmpty())
        songs_playlist_empty_add_folder.beVisibleIf(songs.isEmpty())
    }

    private fun getSongIndex(song: Song): Int = songs.indexOfFirstOrNull { it == song } ?: 0

    private fun getSongsAdapter() = songs_list.adapter as? SongAdapter

    @Subscribe
    fun songChangedEvent(event: Events.SongChanged) {
        updateSongInfo(event.song)
        markCurrentSong()
        val cover = getAlbumImage(event.song)
        val options = RequestOptions().placeholder(art_image.drawable).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        Glide.with(this).load(cover).apply(options).into(art_image)
    }

    @Subscribe
    fun songStateChanged(event: Events.SongStateChanged) {
        val isPlaying = event.isPlaying
        play_pause_btn.setImageDrawable(resources.getDrawable(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play))
        getSongsAdapter()?.updateSongState(isPlaying)
    }

    @Subscribe
    fun playlistUpdated(event: Events.PlaylistUpdated) {
        fillSongsListView(event.songs)
    }

    @Subscribe
    fun progressUpdated(event: Events.ProgressUpdated) {
        val progress = event.progress
        song_progressbar.progress = progress
        getSongsAdapter()?.updateSongProgress(progress)
    }

    @Subscribe
    fun noStoragePermission(event: Events.NoStoragePermission) {
        toast(R.string.no_storage_permissions)
    }

    private fun markCurrentSong() {
        val newSongId = MusicService.mCurrSong?.id ?: -1L
        val cnt = songs.size - 1
        val songIndex = (0..cnt).firstOrNull { songs[it].id == newSongId }?.plus(0) ?: -1
        getSongsAdapter()?.updateCurrentSongIndex(songIndex)
    }

    private fun searchQueryChanged(text: String) {
        val filtered = songs.filter { it.artist.contains(text, true) || it.title.contains(text, true) } as ArrayList
        filtered.sortBy { !(it.artist.startsWith(text, true) || it.title.startsWith(text, true)) }
        songs_playlist_empty.beVisibleIf(filtered.isEmpty())
        getSongsAdapter()?.updateSongs(filtered)
    }

    private fun initSeekbarChangeListener() {
        song_progressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val duration = song_progressbar.max.getFormattedDuration()
                val formattedProgress = progress.getFormattedDuration()
                song_progress.text = String.format(resources.getString(R.string.progress), formattedProgress, duration)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Intent(this@MainActivity, MusicService::class.java).apply {
                    putExtra(PROGRESS, seekBar.progress)
                    action = SET_PROGRESS
                    startService(this)
                }
            }
        })
    }

    override fun refreshItems() {
        sendIntent(REFRESH_LIST)
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(25, R.string.release_25))
            add(Release(27, R.string.release_27))
            add(Release(28, R.string.release_28))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
