package com.simplemobiletools.musicplayer.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.media.AudioManager
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.LICENSE_KOTLIN
import com.simplemobiletools.commons.helpers.LICENSE_MULTISELECT
import com.simplemobiletools.commons.helpers.LICENSE_OTTO
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.Release
import com.simplemobiletools.commons.views.RecyclerViewDivider
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SongAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.extensions.playlistChanged
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*

class MainActivity : SimpleActivity(), SeekBar.OnSeekBarChangeListener {
    companion object {
        private val STORAGE_PERMISSION = 1

        lateinit var mBus: Bus
        private var mSongs: ArrayList<Song> = ArrayList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBus = BusProvider.instance
        mBus.register(this)
        progressbar.setOnSeekBarChangeListener(this)

        if (hasWriteStoragePermission()) {
            initializePlayer()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION)
        }

        previous_btn.setOnClickListener { sendIntent(PREVIOUS) }
        play_pause_btn.setOnClickListener { sendIntent(PLAYPAUSE) }
        next_btn.setOnClickListener { sendIntent(NEXT) }
        songs_playlist_empty_add_folder.setOnClickListener { addFolderToPlaylist() }
        checkWhatsNewDialog()
        storeStoragePaths()
    }

    override fun onResume() {
        super.onResume()
        setupIconColors()
        markCurrentSong()
        updateTextColors(main_holder)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val songRepetition = menu.findItem(R.id.toggle_song_repetition)
        songRepetition.title = getString(if (config.repeatSong) R.string.disable_song_repetition else R.string.enable_song_repetition)
        songRepetition.icon = resources.getDrawable(if (config.repeatSong) R.drawable.ic_repeat else R.drawable.ic_repeat_off)

        val shuffle = menu.findItem(R.id.toggle_shuffle)
        shuffle.title = getString(if (config.isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)
        shuffle.icon = resources.getDrawable(if (config.isShuffleEnabled) R.drawable.ic_shuffle else R.drawable.ic_shuffle_off)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sort -> showSortingDialog()
            R.id.toggle_song_repetition -> toggleSongRepetition()
            R.id.open_playlist -> openPlaylist()
            R.id.toggle_shuffle -> toggleShuffle()
            R.id.add_folder_to_playlist -> addFolderToPlaylist()
            R.id.add_file_to_playlist -> addFileToPlaylist()
            R.id.remove_playlist -> removePlaylist()
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializePlayer()
            } else {
                toast(R.string.no_permissions)
            }
        }
    }

    private fun launchSettings() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        startAboutActivity(R.string.app_name, LICENSE_KOTLIN or LICENSE_OTTO or LICENSE_MULTISELECT, BuildConfig.VERSION_NAME)
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

    private fun removePlaylist() {
        if (config.currentPlaylist == DBHelper.INITIAL_PLAYLIST_ID) {
            toast(R.string.initial_playlist_cannot_be_deleted)
        } else {
            val playlist = dbHelper.getPlaylistWithId(config.currentPlaylist)
            RemovePlaylistDialog(this, playlist) {
                if (it) {
                    val paths = dbHelper.getPlaylistSongPaths(config.currentPlaylist)
                    val files = paths.map(::File) as ArrayList<File>
                    dbHelper.removeSongsFromPlaylist(paths, -1)
                    dbHelper.removePlaylist(config.currentPlaylist)
                    deleteFiles(files) { }
                } else {
                    dbHelper.removePlaylist(config.currentPlaylist)
                }
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
        val initialPath = if (mSongs.isEmpty()) Environment.getExternalStorageDirectory().toString() else mSongs[0].path
        FilePickerDialog(this, initialPath, pickFile = false) {
            val files = File(it).listFiles() ?: return@FilePickerDialog
            val paths = files.mapTo(ArrayList<String>()) { it.absolutePath }
            dbHelper.addSongsToPlaylist(paths)
            sendIntent(REFRESH_LIST)
        }
    }

    private fun addFileToPlaylist() {
        val initialPath = if (mSongs.isEmpty()) Environment.getExternalStorageDirectory().toString() else mSongs[0].path
        FilePickerDialog(this, initialPath) {
            dbHelper.addSongToPlaylist(it)
            sendIntent(REFRESH_LIST)
        }
    }

    private fun initializePlayer() {
        sendIntent(INIT)
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    private fun setupIconColors() {
        val color = config.textColor
        previous_btn.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        play_pause_btn.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        next_btn.setColorFilter(color, PorterDuff.Mode.SRC_IN)

        SongAdapter.textColor = color
        songs_fastscroller.updateHandleColor()
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
        song_title.text = song?.title ?: ""
        song_artist.text = song?.artist ?: ""
        progressbar.max = song?.duration ?: 0
        progressbar.progress = 0

        if (mSongs.isEmpty()) {
            toast(R.string.empty_playlist)
        }
    }

    private fun fillSongsListView(songs: ArrayList<Song>) {
        mSongs = songs
        val adapter = SongAdapter(this, songs) {
            songPicked(it)
        }

        val currAdapter = songs_list.adapter
        songs_fastscroller.setViews(songs_list)
        if (currAdapter != null) {
            (currAdapter as SongAdapter).updateSongs(songs)
        } else {
            songs_list.apply {
                this@apply.adapter = adapter
                addItemDecoration(RecyclerViewDivider(context))
            }
        }
        markCurrentSong()
        songs_playlist_empty.beVisibleIf(songs.isEmpty())
        songs_playlist_empty_add_folder.beVisibleIf(songs.isEmpty())
    }

    override fun onDestroy() {
        super.onDestroy()
        mBus.unregister(this)
    }

    @Subscribe
    fun songChangedEvent(event: Events.SongChanged) {
        updateSongInfo(event.song)
        markCurrentSong()
    }

    @Subscribe
    fun songStateChanged(event: Events.SongStateChanged) {
        play_pause_btn.setImageDrawable(resources.getDrawable(if (event.isPlaying) R.drawable.ic_pause else R.drawable.ic_play))
    }

    @Subscribe
    fun playlistUpdated(event: Events.PlaylistUpdated) {
        fillSongsListView(event.songs)
    }

    @Subscribe
    fun progressUpdated(event: Events.ProgressUpdated) {
        progressbar.progress = event.progress
    }

    @Subscribe
    fun noStoragePermission(event: Events.NoStoragePermission) {
        toast(R.string.no_permissions)
    }

    private fun markCurrentSong() {
        val newSongId = MusicService.mCurrSong?.id ?: -1L
        val cnt = mSongs.size - 1
        val songIndex = (0..cnt).firstOrNull { mSongs[it].id == newSongId } ?: -1
        if (songs_list.adapter != null)
            (songs_list.adapter as SongAdapter).updateCurrentSongIndex(songIndex)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        val duration = progressbar.max.getFormattedDuration()
        val formattedProgress = progress.getFormattedDuration()

        val progressText = String.format(resources.getString(R.string.progress), formattedProgress, duration)
        song_progress.text = progressText
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        Intent(this, MusicService::class.java).apply {
            putExtra(PROGRESS, seekBar.progress)
            action = SET_PROGRESS
            startService(this)
        }
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
