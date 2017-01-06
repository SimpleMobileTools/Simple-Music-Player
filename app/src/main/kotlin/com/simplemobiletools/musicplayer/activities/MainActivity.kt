package com.simplemobiletools.musicplayer.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.media.AudioManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.views.RecyclerViewDivider
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SongAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.getTimeString
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : SimpleActivity(), SeekBar.OnSeekBarChangeListener {
    companion object {
        private val STORAGE_PERMISSION = 1

        lateinit var mBus: Bus
        private var mSongs: List<Song> = ArrayList()

        private var mIsNumericProgressShown = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBus = BusProvider.instance
        mBus.register(this)
        progressbar.setOnSeekBarChangeListener(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initializePlayer()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION)
        }

        previous_btn.setOnClickListener { sendIntent(PREVIOUS) }
        play_pause_btn.setOnClickListener { sendIntent(PLAYPAUSE) }
        next_btn.setOnClickListener { sendIntent(NEXT) }
    }

    override fun onResume() {
        super.onResume()
        mIsNumericProgressShown = mConfig.isNumericProgressEnabled
        setupIconColors()
        song_progress.visibility = if (mIsNumericProgressShown) View.VISIBLE else View.GONE
        markCurrentSong()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)

        val songRepetition = menu.findItem(R.id.toggle_song_repetition)
        songRepetition.title = getString(if (mConfig.repeatSong) R.string.disable_song_repetition else R.string.enable_song_repetition)

        val shuffle = menu.findItem(R.id.toggle_shuffle)
        shuffle.title = getString(if (mConfig.isShuffleEnabled) R.string.disable_shuffle else R.string.enable_shuffle)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sort -> {
                showSortingDialog()
                true
            }
            R.id.toggle_shuffle -> {
                toggleShuffle()
                true
            }
            R.id.toggle_song_repetition -> {
                toggleSongRepetition()
                true
            }
            R.id.settings -> {
                startActivity(Intent(applicationContext, SettingsActivity::class.java))
                true
            }
            R.id.about -> {
                startActivity(Intent(applicationContext, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

    private fun showSortingDialog() {
        ChangeSortingDialog(this) {
            sendIntent(REFRESH_LIST)
        }
    }

    private fun toggleShuffle() {
        mConfig.isShuffleEnabled = !mConfig.isShuffleEnabled
        invalidateOptionsMenu()
        toast(if (mConfig.isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
    }

    private fun toggleSongRepetition() {
        mConfig.repeatSong = !mConfig.repeatSong
        invalidateOptionsMenu()
        toast(if (mConfig.repeatSong) R.string.song_repetition_enabled else R.string.song_repetition_disabled)
    }

    private fun initializePlayer() {
        sendIntent(INIT)
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    private fun setupIconColors() {
        val color = song_title.currentTextColor
        previous_btn.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        play_pause_btn.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        next_btn.setColorFilter(color, PorterDuff.Mode.SRC_IN)

        SongAdapter.iconColor = color
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
        if (song != null) {
            song_title.text = song.title
            song_artist.text = song.artist
            progressbar.max = song.duration
            progressbar.progress = 0
        } else {
            song_title.text = ""
            song_artist.text = ""
        }
    }

    private fun fillSongsListView(songs: ArrayList<Song>) {
        mSongs = songs
        val adapter = SongAdapter(this, songs) {
            songPicked(it)
        }

        val currAdapter = songs_list.adapter
        if (currAdapter != null) {
            (currAdapter as SongAdapter).updateSongs(songs)
        } else {
            songs_list.apply {
                this@apply.adapter = adapter
                addItemDecoration(RecyclerViewDivider(context))
            }
        }
        markCurrentSong()
    }

    override fun onDestroy() {
        super.onDestroy()
        mConfig.isFirstRun = false
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

    private fun markCurrentSong() {
        val newSongId = MusicService.mCurrSong?.id ?: -1L
        val cnt = mSongs.size - 1
        val songIndex = (0..cnt).firstOrNull { mSongs[it].id == newSongId } ?: -1
        if (songs_list.adapter != null)
            (songs_list.adapter as SongAdapter).updateCurrentSongIndex(songIndex)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (mIsNumericProgressShown) {
            val duration = progressbar.max.getTimeString()
            val formattedProgress = progress.getTimeString()

            val progressText = String.format(resources.getString(R.string.progress), formattedProgress, duration)
            song_progress.text = progressText
        }
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
}
