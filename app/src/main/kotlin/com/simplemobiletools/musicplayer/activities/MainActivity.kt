package com.simplemobiletools.musicplayer.activities

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.interfaces.RecyclerScrollCallback
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.Release
import com.simplemobiletools.commons.views.MyLinearLayoutManager
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SongAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.SleepTimerCustomDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.interfaces.SongListListener
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_navigation.*
import kotlinx.android.synthetic.main.item_navigation.view.*
import java.io.File
import java.util.*

class MainActivity : SimpleActivity(), SongListListener {
    private var isThirdPartyIntent = false
    private var songs = ArrayList<Song>()
    private var searchMenuItem: MenuItem? = null
    private var isSearchOpen = false
    private var wasInitialPlaylistSet = false
    private var lastFilePickerPath = ""
    private var artView: ViewGroup? = null

    private var actionbarSize = 0
    private var topArtHeight = 0

    private var storedTextColor = 0
    private var storedShowAlbumCover = true

    lateinit var bus: Bus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        isThirdPartyIntent = intent.action == Intent.ACTION_VIEW

        bus = BusProvider.instance
        bus.register(this)
        initSeekbarChangeListener()

        actionbarSize = getActionBarHeight()
        artView = layoutInflater.inflate(R.layout.item_transparent, null) as ViewGroup
        setTopArtHeight()
        songs_fastscroller.measureItemIndex = LIST_HEADERS_COUNT

        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                initializePlayer()
            } else {
                toast(R.string.no_storage_permissions)
            }
        }

        shuffle_btn.setOnClickListener { toggleShuffle() }
        previous_btn.setOnClickListener { sendIntent(PREVIOUS) }
        play_pause_btn.setOnClickListener { sendIntent(PLAYPAUSE) }
        next_btn.setOnClickListener { sendIntent(NEXT) }
        repeat_btn.setOnClickListener { toggleSongRepetition() }
        song_progress_current.setOnClickListener { sendIntent(SKIP_BACKWARD) }
        song_progress_max.setOnClickListener { sendIntent(SKIP_FORWARD) }
        sleep_timer_stop.setOnClickListener { stopSleepTimer() }

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

        if (savedInstanceState != null) {
            songs_list.onGlobalLayout {
                songs_list.scrollToPosition(0)
            }
        }

        checkAppOnSDCard()
    }

    override fun onResume() {
        super.onResume()
        if (storedTextColor != config.textColor) {
            updateAlbumCover()
        }

        if (storedShowAlbumCover != config.showAlbumCover) {
            setTopArtHeight()
            songs_list.adapter?.notifyDataSetChanged()
            if (config.showAlbumCover) {
                updateAlbumCover()
            } else {
                try {
                    art_image.setImageDrawable(null)
                } catch (ignored: Exception) {
                }
            }
        }

        setupIconColors()
        setupIconDescriptions()
        markCurrentSong()
        updateTextColors(main_holder)
        getSongsAdapter()?.updateColors()
        songs_playlist_empty_add_folder.setTextColor(getAdjustedPrimaryColor())
        songs_playlist_empty_add_folder.paintFlags = songs_playlist_empty_add_folder.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        songs_fastscroller.allowBubbleDisplay = true
        songs_fastscroller.updateBubbleColors()

        arrayListOf(art_holder, song_list_background, top_navigation, sleep_timer_holder).forEach {
            it.background = ColorDrawable(config.backgroundColor)
        }

        val stopDrawable = resources.getDrawable(R.drawable.ic_stop_shape)
        (stopDrawable as LayerDrawable).findDrawableByLayerId(R.id.ic_stop_shape_background).applyColorFilter(config.textColor)
        sleep_timer_stop.setImageDrawable(stopDrawable)
        invalidateOptionsMenu()
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

        if (isThirdPartyIntent && !isChangingConfigurations) {
            sendIntent(FINISH)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        setupSearch(menu)

        val autoplay = menu.findItem(R.id.toggle_autoplay)
        autoplay.title = getString(if (config.autoplay) R.string.disable_autoplay else R.string.enable_autoplay)

        menu.apply {
            findItem(R.id.sort).isVisible = !isThirdPartyIntent
            findItem(R.id.sort).isVisible = !isThirdPartyIntent
            findItem(R.id.open_playlist).isVisible = !isThirdPartyIntent
            findItem(R.id.add_folder_to_playlist).isVisible = !isThirdPartyIntent
            findItem(R.id.add_file_to_playlist).isVisible = !isThirdPartyIntent
            findItem(R.id.remove_playlist).isVisible = !isThirdPartyIntent
        }

        updateMenuItemColors(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val isSongSelected = MusicService.mCurrSong != null
        menu.apply {
            findItem(R.id.remove_current).isVisible = !isThirdPartyIntent && isSongSelected
            findItem(R.id.delete_current).isVisible = !isThirdPartyIntent && isSongSelected
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sort -> showSortingDialog()
            R.id.remove_current -> getSongsAdapter()?.removeCurrentSongFromPlaylist()
            R.id.delete_current -> getSongsAdapter()?.deleteCurrentSong()
            R.id.sleep_timer -> showSleepTimer()
            R.id.open_playlist -> openPlaylist()
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
            storedTextColor = textColor
            storedShowAlbumCover = showAlbumCover
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            setIntent(intent)
            initThirdPartyIntent()
        }
    }

    private fun setTopArtHeight() {
        topArtHeight = if (config.showAlbumCover) resources.getDimensionPixelSize(R.dimen.top_art_height) else 0
        artView!!.setPadding(0, topArtHeight, 0, 0)
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
                    markCurrentSong()
                    (songs_list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).scrollToPositionWithOffset(0, 0)
                    songs_fastscroller?.setScrollToY(0)
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
        val licenses = LICENSE_OTTO or LICENSE_PICASSO

        val faqItems = arrayListOf(
                FAQItem(R.string.faq_1_title, R.string.faq_1_text),
                FAQItem(R.string.faq_1_title_commons, R.string.faq_1_text_commons),
                FAQItem(R.string.faq_4_title_commons, R.string.faq_4_text_commons),
                FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
                FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this) {
            sendIntent(REFRESH_LIST)
        }
    }

    private fun toggleShuffle() {
        val isShuffleEnabled = !config.isShuffleEnabled
        config.isShuffleEnabled = isShuffleEnabled
        shuffle_btn.applyColorFilter(if (isShuffleEnabled) getAdjustedPrimaryColor() else config.textColor)
        shuffle_btn.alpha = if (isShuffleEnabled) 1f else LOWER_ALPHA
        shuffle_btn.contentDescription = getString(if (isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        getSongsAdapter()?.updateShuffle(isShuffleEnabled)
        toast(if (isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
    }

    private fun toggleSongRepetition() {
        val repeatSong = !config.repeatSong
        config.repeatSong = repeatSong
        repeat_btn.applyColorFilter(if (repeatSong) getAdjustedPrimaryColor() else config.textColor)
        repeat_btn.alpha = if (repeatSong) 1f else LOWER_ALPHA
        repeat_btn.contentDescription = getString(if (repeatSong) R.string.disable_song_repetition else R.string.enable_song_repetition)
        getSongsAdapter()?.updateRepeatSong(repeatSong)
        toast(if (repeatSong) R.string.song_repetition_enabled else R.string.song_repetition_disabled)
    }

    private fun toggleAutoplay() {
        config.autoplay = !config.autoplay
        invalidateOptionsMenu()
        toast(if (config.autoplay) R.string.autoplay_enabled else R.string.autoplay_disabled)
    }

    private fun showSleepTimer() {
        val minutes = getString(R.string.minutes_raw)
        val hour = resources.getQuantityString(R.plurals.hours, 1, 1)

        val items = arrayListOf(
                RadioItem(5 * 60, "5 $minutes"),
                RadioItem(10 * 60, "10 $minutes"),
                RadioItem(20 * 60, "20 $minutes"),
                RadioItem(30 * 60, "30 $minutes"),
                RadioItem(60 * 60, hour))

        if (items.none { it.id == config.lastSleepTimerSeconds }) {
            val lastSleepTimerMinutes = config.lastSleepTimerSeconds / 60
            val text = resources.getQuantityString(R.plurals.minutes, lastSleepTimerMinutes, lastSleepTimerMinutes)
            items.add(RadioItem(config.lastSleepTimerSeconds, text))
        }

        items.sortBy { it.id }
        items.add(RadioItem(-1, getString(R.string.custom)))

        RadioGroupDialog(this, items, config.lastSleepTimerSeconds) {
            if (it as Int == -1) {
                SleepTimerCustomDialog(this) {
                    if (it > 0) {
                        pickedSleepTimer(it)
                    }
                }
            } else if (it > 0) {
                pickedSleepTimer(it)
            }
        }
    }

    private fun pickedSleepTimer(seconds: Int) {
        config.lastSleepTimerSeconds = seconds
        config.sleepInTS = System.currentTimeMillis() + seconds * 1000
        startSleepTimer()
    }

    private fun startSleepTimer() {
        sleep_timer_holder.beVisible()
        sendIntent(START_SLEEP_TIMER)
    }

    private fun stopSleepTimer() {
        sendIntent(STOP_SLEEP_TIMER)
        sleep_timer_holder.beGone()
    }

    private fun removePlaylist() {
        if (config.currentPlaylist == ALL_SONGS_PLAYLIST_ID) {
            toast(R.string.all_songs_cannot_be_deleted)
        } else {
            ensureBackgroundThread {
                val playlist = playlistDAO.getPlaylistWithId(config.currentPlaylist)
                runOnUiThread {
                    RemovePlaylistDialog(this, playlist) {
                        ensureBackgroundThread {
                            if (it) {
                                val paths = getPlaylistSongs(config.currentPlaylist).map { it.path }
                                val files = paths.map { FileDirItem(it, it.getFilenameFromPath()) } as ArrayList<FileDirItem>
                                paths.forEach {
                                    songsDAO.removeSongPath(it)
                                }
                                deleteFiles(files)
                            }

                            if (playlist != null) {
                                deletePlaylists(arrayListOf(playlist))
                            }
                            playlistChanged(ALL_SONGS_PLAYLIST_ID)
                        }
                    }
                }
            }
        }
    }

    private fun openPlaylist() {
        ensureBackgroundThread {
            val playlists = playlistDAO.getAll() as ArrayList<Playlist>
            runOnUiThread {
                showPlaylists(playlists)
            }
        }
    }

    private fun showPlaylists(playlists: ArrayList<Playlist>) {
        val items = arrayListOf<RadioItem>()
        playlists.mapTo(items) { RadioItem(it.id, it.title) }
        items.add(RadioItem(-1, getString(R.string.create_playlist)))

        RadioGroupDialog(this, items, config.currentPlaylist) {
            if (it == -1) {
                NewPlaylistDialog(this) {
                    wasInitialPlaylistSet = false
                    MusicService.mCurrSong = null
                    playlistChanged(it, false)
                    invalidateOptionsMenu()
                }
            } else {
                wasInitialPlaylistSet = false
                playlistChanged(it as Int)
                invalidateOptionsMenu()
            }
        }
    }

    private fun addFolderToPlaylist() {
        FilePickerDialog(this, getFilePickerInitialPath(), pickFile = false) {
            toast(R.string.fetching_songs)
            ensureBackgroundThread {
                val folderSongs = getFolderSongs(File(it))
                RoomHelper(applicationContext).addSongsToPlaylist(folderSongs)
                sendIntent(REFRESH_LIST)
            }
        }
    }

    private fun getFolderSongs(folder: File): ArrayList<String> {
        val songFiles = ArrayList<String>()
        val files = folder.listFiles() ?: return songFiles
        files.forEach {
            if (it.isDirectory) {
                songFiles.addAll(getFolderSongs(it))
                lastFilePickerPath = it.absolutePath
            } else if (it.isAudioFast()) {
                songFiles.add(it.absolutePath)
            }
        }
        return songFiles
    }

    private fun addFileToPlaylist() {
        FilePickerDialog(this, getFilePickerInitialPath()) {
            ensureBackgroundThread {
                lastFilePickerPath = it
                if (it.isAudioFast()) {
                    RoomHelper(applicationContext).addSongToPlaylist(it)
                    sendIntent(REFRESH_LIST)
                } else {
                    toast(R.string.invalid_file_format)
                }
            }
        }
    }

    private fun createPlaylistFromFolder() {
        FilePickerDialog(this, getFilePickerInitialPath(), pickFile = false) {
            ensureBackgroundThread {
                createPlaylistFrom(it)
            }
        }
    }

    private fun createPlaylistFrom(path: String) {
        val folderSongs = getFolderSongs(File(path))
        if (folderSongs.isEmpty()) {
            toast(R.string.folder_contains_no_audio)
            return
        }

        lastFilePickerPath = path
        val folderName = path.getFilenameFromPath()
        var playlistName = folderName
        var curIndex = 1
        val playlistIdWithTitle = getPlaylistIdWithTitle(folderName)
        if (playlistIdWithTitle != -1) {
            while (true) {
                playlistName = "${folderName}_$curIndex"
                if (getPlaylistIdWithTitle(playlistName) == -1) {
                    break
                }

                curIndex++
            }
        }

        val playlist = Playlist(0, playlistName)
        val newPlaylistId = playlistDAO.insert(playlist).toInt()
        RoomHelper(applicationContext).addSongsToPlaylist(folderSongs, newPlaylistId)
        playlistChanged(newPlaylistId)
    }

    private fun getFilePickerInitialPath(): String {
        if (lastFilePickerPath.isEmpty()) {
            lastFilePickerPath = if (songs.isEmpty()) {
                Environment.getExternalStorageDirectory().toString()
            } else {
                songs[0].path
            }
        }

        return lastFilePickerPath
    }

    private fun initializePlayer() {
        if (isThirdPartyIntent) {
            initThirdPartyIntent()
        } else {
            sendIntent(INIT)
        }
    }

    private fun initThirdPartyIntent() {
        val realPath = intent.getStringExtra(REAL_FILE_PATH) ?: ""
        var fileUri = intent.data
        if (realPath.isNotEmpty()) {
            fileUri = Uri.fromFile(File(realPath))
        }

        Intent(this, MusicService::class.java).apply {
            data = fileUri
            action = INIT_PATH
            startService(this)
        }
    }

    private fun setupIconColors() {
        val textColor = config.textColor
        previous_btn.applyColorFilter(textColor)
        play_pause_btn.applyColorFilter(textColor)
        next_btn.applyColorFilter(textColor)

        shuffle_btn.applyColorFilter(if (config.isShuffleEnabled) getAdjustedPrimaryColor() else config.textColor)
        shuffle_btn.alpha = if (config.isShuffleEnabled) 1f else LOWER_ALPHA

        repeat_btn.applyColorFilter(if (config.repeatSong) getAdjustedPrimaryColor() else config.textColor)
        repeat_btn.alpha = if (config.repeatSong) 1f else LOWER_ALPHA

        getSongsAdapter()?.updateTextColor(textColor)
        songs_fastscroller.updatePrimaryColor()
    }

    private fun setupIconDescriptions() {
        shuffle_btn.contentDescription = getString(if (config.isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        repeat_btn.contentDescription = getString(if (config.repeatSong) R.string.disable_song_repetition else R.string.enable_song_repetition)
    }

    private fun songPicked(pos: Int) {
        setupIconColors()
        setupIconDescriptions()
        Intent(this, MusicService::class.java).apply {
            putExtra(SONG_POS, pos)
            action = PLAYPOS
            startService(this)
        }
    }

    private fun updateSongInfo(song: Song?) {
        top_navigation.song_info_title.text = song?.title ?: ""
        top_navigation.song_info_artist.text = song?.artist ?: ""
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
                isThirdPartyIntent = this@MainActivity.isThirdPartyIntent
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

    private fun getSongIndex(song: Song) = songs.indexOfFirstOrNull { it == song } ?: 0

    private fun getSongsAdapter() = songs_list.adapter as? SongAdapter

    @Subscribe
    fun songChangedEvent(event: Events.SongChanged) {
        if (!wasInitialPlaylistSet) {
            return
        }

        val song = event.song
        updateSongInfo(song)
        markCurrentSong()
        updateAlbumCover()
    }

    @Subscribe
    fun songStateChanged(event: Events.SongStateChanged) {
        val isPlaying = event.isPlaying
        play_pause_btn.setImageDrawable(resources.getDrawable(if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector))
        getSongsAdapter()?.updateSongState(isPlaying)
    }

    @Subscribe
    fun playlistUpdated(event: Events.PlaylistUpdated) {
        wasInitialPlaylistSet = true
        fillSongsListView(event.songs.clone() as ArrayList<Song>)
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

    @Subscribe
    fun sleepTimerChanged(event: Events.SleepTimerChanged) {
        sleep_timer_holder.beVisible()
        sleep_timer_value.text = event.seconds.getFormattedDuration()

        if (event.seconds == 0) {
            finish()
        }
    }

    private fun markCurrentSong() {
        getSongsAdapter()?.updateCurrentSongIndex(getCurrentSongIndex())
    }

    private fun getCurrentSongIndex(): Int {
        val newSong = MusicService.mCurrSong
        val cnt = songs.size - 1
        return (0..cnt).firstOrNull { songs[it] == newSong } ?: -1
    }

    private fun updateAlbumCover() {
        if (!config.showAlbumCover) {
            return
        }

        val coverToUse = if (MusicService.mCurrSongCover?.isRecycled == true) {
            resources.getColoredBitmap(R.drawable.ic_headset, config.textColor)
        } else {
            MusicService.mCurrSongCover
        }

        // Picasso cannot load bitmaps/drawables directly, so as a workaround load images as placeholders
        val bitmapDrawable = BitmapDrawable(resources, coverToUse)
        val uri: Uri? = null
        Picasso.get()
                .load(uri)
                .fit()
                .placeholder(bitmapDrawable)
                .into(art_image)
    }

    private fun searchQueryChanged(text: String) {
        val filtered = songs.filter { it.artist.contains(text, true) || it.title.contains(text, true) } as ArrayList
        filtered.sortBy { !(it.artist.startsWith(text, true) || it.title.startsWith(text, true)) }
        songs_playlist_empty.beVisibleIf(filtered.isEmpty())
        getSongsAdapter()?.updateSongs(filtered, text)
    }

    private fun initSeekbarChangeListener() {
        song_progressbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val duration = song_progressbar.max.getFormattedDuration()
                val formattedProgress = progress.getFormattedDuration()
                song_progress_current.text = formattedProgress
                song_progress_max.text = duration
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

    override fun listToggleShuffle() {
        toggleShuffle()
    }

    override fun listToggleSongRepetition() {
        toggleSongRepetition()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(25, R.string.release_25))
            add(Release(27, R.string.release_27))
            add(Release(28, R.string.release_28))
            add(Release(37, R.string.release_37))
            add(Release(59, R.string.release_59))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
