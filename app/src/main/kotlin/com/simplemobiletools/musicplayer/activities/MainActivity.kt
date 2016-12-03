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
import com.simplemobiletools.filepicker.extensions.toast
import com.simplemobiletools.filepicker.views.RecyclerViewDivider
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SongAdapter
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.enable_song_repetition).isVisible = !mConfig.repeatSong
        menu.findItem(R.id.disable_song_repetition).isVisible = mConfig.repeatSong
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.settings -> {
                startActivity(Intent(applicationContext, SettingsActivity::class.java))
                true
            }
            R.id.about -> {
                startActivity(Intent(applicationContext, AboutActivity::class.java))
                true
            }
            R.id.enable_song_repetition -> {
                toggleSongRepetition(true)
                true
            }
            R.id.disable_song_repetition -> {
                toggleSongRepetition(false)
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

    private fun toggleSongRepetition(enable: Boolean) {
        mConfig.repeatSong = enable
        invalidateOptionsMenu()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        mConfig.isFirstRun = false
        mBus.unregister(this)
    }

    @Subscribe
    fun songChangedEvent(event: Events.SongChanged) {
        updateSongInfo(event.song)
    }

    @Subscribe
    fun songStateChanged(event: Events.SongStateChanged) {
        play_pause_btn.setImageDrawable(resources.getDrawable(if (event.isPlaying) R.mipmap.pause else R.mipmap.play))
    }

    @Subscribe
    fun playlistUpdated(event: Events.PlaylistUpdated) {
        fillSongsListView(event.songs)
    }

    @Subscribe
    fun songChangedEvent(event: Events.ProgressUpdated) {
        progressbar.progress = event.progress
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
