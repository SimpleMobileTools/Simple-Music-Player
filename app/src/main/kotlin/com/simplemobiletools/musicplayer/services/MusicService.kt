package com.simplemobiletools.musicplayer.services

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.AudioManager.*
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Handler
import android.os.PowerManager
import android.provider.MediaStore
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_WRITE_STORAGE
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.receivers.ControlActionsListener
import com.simplemobiletools.musicplayer.receivers.HeadsetPlugReceiver
import com.simplemobiletools.musicplayer.receivers.RemoteControlReceiver
import com.squareup.otto.Bus
import java.io.File
import java.io.IOException
import java.util.*

class MusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
    companion object {
        private val TAG = MusicService::class.java.simpleName
        private val MIN_INITIAL_DURATION = 30
        private val PROGRESS_UPDATE_INTERVAL = 1000
        private val NOTIFICATION_ID = 78    // just a random number

        var mCurrSong: Song? = null
        var mEqualizer: Equalizer? = null
        private var mHeadsetPlugReceiver: HeadsetPlugReceiver? = null
        private var mPlayer: MediaPlayer? = null
        private var mPlayedSongIndexes = ArrayList<Int>()
        private var mBus: Bus? = null
        private var mProgressHandler: Handler? = null
        private var mSongs = ArrayList<Song>()
        private var mAudioManager: AudioManager? = null
        private var mOreoFocusHandler: OreoAudioFocusHandler? = null

        private var mWasPlayingAtFocusLost = false
        private var mPlayOnPrepare = true
        private var mIsThirdPartyIntent = false
        private var intentUri: Uri? = null
        private var isServiceInitialized = false

        fun getIsPlaying() = mPlayer?.isPlaying == true
    }

    override fun onCreate() {
        super.onCreate()

        if (mBus == null) {
            mBus = BusProvider.instance
            mBus!!.register(this)
        }

        mProgressHandler = Handler()
        val remoteControlComponent = ComponentName(packageName, RemoteControlReceiver::class.java.name)
        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioManager!!.registerMediaButtonEventReceiver(remoteControlComponent)
        if (isOreoPlus()) {
            mOreoFocusHandler = OreoAudioFocusHandler(applicationContext)
        }

        if (hasPermission(PERMISSION_WRITE_STORAGE)) {
            initService()
        } else {
            mBus!!.post(Events.NoStoragePermission())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyPlayer()
    }

    private fun initService() {
        mSongs.clear()
        mPlayedSongIndexes = ArrayList()
        mCurrSong = null
        if (mIsThirdPartyIntent && intentUri != null) {
            val path = getRealPathFromURI(intentUri!!) ?: ""
            val song = dbHelper.getSongFromPath(path)
            if (song != null) {
                mSongs.add(song)
            }
        } else {
            getSortedSongs()
        }

        mHeadsetPlugReceiver = HeadsetPlugReceiver()
        mWasPlayingAtFocusLost = false
        initMediaPlayerIfNeeded()
        setupNotification()
        isServiceInitialized = true
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!hasPermission(PERMISSION_WRITE_STORAGE)) {
            return START_NOT_STICKY
        }

        when (intent.action) {
            INIT -> {
                mIsThirdPartyIntent = false
                if (!isServiceInitialized) {
                    initService()
                }
                initSongs()
            }
            INIT_PATH -> {
                mIsThirdPartyIntent = true
                intentUri = intent.data
                initService()
                initSongs()
            }
            SETUP -> {
                mPlayOnPrepare = true
                setupNextSong()
            }
            PREVIOUS -> {
                mPlayOnPrepare = true
                playPreviousSong()
            }
            PAUSE -> pauseSong()
            PLAYPAUSE -> {
                mPlayOnPrepare = true
                if (getIsPlaying()) {
                    pauseSong()
                } else {
                    resumeSong()
                }
            }
            NEXT -> {
                mPlayOnPrepare = true
                setupNextSong()
            }
            PLAYPOS -> playSong(intent)
            EDIT -> {
                mCurrSong = intent.getSerializableExtra(EDITED_SONG) as Song
                mBus!!.post(Events.SongChanged(mCurrSong))
                setupNotification()
            }
            FINISH -> {
                mBus!!.post(Events.ProgressUpdated(0))
                destroyPlayer()
            }
            REFRESH_LIST -> {
                getSortedSongs()
                mBus!!.post(Events.PlaylistUpdated(mSongs))
            }
            SET_PROGRESS -> {
                if (mPlayer != null) {
                    val progress = intent.getIntExtra(PROGRESS, mPlayer!!.currentPosition / 1000)
                    updateProgress(progress)
                }
            }
            SET_EQUALIZER -> {
                if (intent.extras?.containsKey(EQUALIZER) == true) {
                    val presetID = intent.extras.getInt(EQUALIZER)
                    if (mEqualizer != null) {
                        setPreset(presetID)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun setupSong() {
        if (mIsThirdPartyIntent) {
            initMediaPlayerIfNeeded()

            try {
                mPlayer!!.apply {
                    reset()
                    setDataSource(applicationContext, intentUri)
                    setOnPreparedListener(null)
                    prepare()
                    start()
                    requestAudioFocus()
                }

                val song = mSongs.first()
                mSongs.clear()
                mSongs.add(song)
                mCurrSong = song
                updateUI()
            } catch (e: Exception) {
                Log.e(TAG, "setupSong Exception $e")
            }
        } else {
            mPlayOnPrepare = false
            setupNextSong()
        }
    }

    private fun initSongs() {
        updateUI()
        if (mCurrSong == null) {
            setupSong()
        } else {
            val secs = mPlayer!!.currentPosition / 1000
            mBus!!.post(Events.ProgressUpdated(secs))
        }
    }

    private fun updateUI() {
        mBus!!.post(Events.PlaylistUpdated(mSongs))
        mBus!!.post(Events.SongChanged(mCurrSong))
        songStateChanged(getIsPlaying())
    }

    private fun initMediaPlayerIfNeeded() {
        if (mPlayer != null)
            return

        mPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOnPreparedListener(this@MusicService)
            setOnCompletionListener(this@MusicService)
            setOnErrorListener(this@MusicService)
        }
        setupEqualizer()
    }

    private fun getAllDeviceSongs() {
        val ignoredPaths = config.ignoredPaths
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA)

        var cursor: Cursor? = null
        val paths = ArrayList<String>()

        try {
            cursor = contentResolver.query(uri, columns, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val duration = cursor.getIntValue(MediaStore.Audio.Media.DURATION) / 1000
                    if (duration > MIN_INITIAL_DURATION) {
                        val path = cursor.getStringValue(MediaStore.Audio.Media.DATA)
                        if (!ignoredPaths.contains(path)) {
                            paths.add(path)
                        }
                    }
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }

        dbHelper.addSongsToPlaylist(paths)
    }

    private fun getSortedSongs() {
        if (config.currentPlaylist == DBHelper.ALL_SONGS_ID) {
            getAllDeviceSongs()
        }

        mSongs = dbHelper.getSongs()
        Song.sorting = config.sorting
        mSongs.sort()
    }

    private fun setupEqualizer() {
        mEqualizer = Equalizer(0, mPlayer!!.audioSessionId)
        mEqualizer?.enabled = true
        setPreset(config.equalizer)
    }

    private fun setPreset(id: Int) {
        try {
            mEqualizer?.usePreset(id.toShort())
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "setPreset $e")
        }
    }

    @SuppressLint("NewApi")
    private fun setupNotification() {
        val title = mCurrSong?.title ?: ""
        val artist = mCurrSong?.artist ?: ""
        val playPauseButtonPosition = 1
        val nextButtonPosition = 2
        val playPauseIcon = if (getIsPlaying()) R.drawable.ic_pause else R.drawable.ic_play

        var notifWhen = 0L
        var showWhen = false
        var usesChronometer = false
        var ongoing = false
        if (getIsPlaying()) {
            notifWhen = System.currentTimeMillis() - mPlayer!!.currentPosition
            showWhen = true
            usesChronometer = true
            ongoing = true
        }

        val channelId = "music_player_channel"
        if (isOreoPlus()) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val name = resources.getString(R.string.app_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            NotificationChannel(channelId, name, importance).apply {
                enableLights(false)
                enableVibration(false)
                notificationManager.createNotificationChannel(this)
            }
        }

        val notification = NotificationCompat.Builder(this)
                .setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(playPauseButtonPosition, nextButtonPosition))
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_headset_small)
                .setLargeIcon(getAlbumImage())
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setWhen(notifWhen)
                .setShowWhen(showWhen)
                .setUsesChronometer(usesChronometer)
                .setContentIntent(getContentIntent())
                .setOngoing(ongoing)
                .setChannelId(channelId)
                .addAction(R.drawable.ic_previous, getString(R.string.previous), getIntent(PREVIOUS))
                .addAction(playPauseIcon, getString(R.string.playpause), getIntent(PLAYPAUSE))
                .addAction(R.drawable.ic_next, getString(R.string.next), getIntent(NEXT))

        startForeground(NOTIFICATION_ID, notification.build())

        if (!getIsPlaying()) {
            Handler().postDelayed({ stopForeground(false) }, 500)
        }
    }

    private fun getAlbumImage(): Bitmap {
        if (File(mCurrSong?.path ?: "").exists()) {
            try {
                val mediaMetadataRetriever = MediaMetadataRetriever()
                mediaMetadataRetriever.setDataSource(mCurrSong!!.path)
                val rawArt = mediaMetadataRetriever.embeddedPicture
                if (rawArt != null) {
                    val options = BitmapFactory.Options()
                    val bitmap = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
                    if (bitmap != null)
                        return bitmap
                }
            } catch (e: Exception) {
            }
        }
        return BitmapFactory.decodeResource(resources, R.drawable.ic_headset)
    }

    private fun getContentIntent(): PendingIntent {
        val contentIntent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, contentIntent, 0)
    }

    private fun getIntent(action: String): PendingIntent {
        val intent = Intent(this, ControlActionsListener::class.java)
        intent.action = action
        return PendingIntent.getBroadcast(applicationContext, 0, intent, 0)
    }

    private fun getNewSongId(): Int {
        return if (config.isShuffleEnabled) {
            val cnt = mSongs.size
            when (cnt) {
                0 -> -1
                1 -> 0
                else -> {
                    val random = Random()
                    var newSongIndex = random.nextInt(cnt)
                    while (mPlayedSongIndexes.contains(newSongIndex)) {
                        newSongIndex = random.nextInt(cnt)
                    }
                    newSongIndex
                }
            }
        } else {
            if (mPlayedSongIndexes.isEmpty()) {
                return 0
            }

            val lastIndex = mPlayedSongIndexes[mPlayedSongIndexes.size - 1]
            (lastIndex + 1) % Math.max(mSongs.size, 1)
        }
    }

    private fun playPreviousSong() {
        if (mSongs.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        // play the previous song if we are less than 5 secs into the song, else restart
        // remove the latest song from the list
        if (mPlayedSongIndexes.size > 1 && mPlayer!!.currentPosition < 5000) {
            mPlayedSongIndexes.removeAt(mPlayedSongIndexes.size - 1)
            setSong(mPlayedSongIndexes[mPlayedSongIndexes.size - 1], false)
        } else {
            restartSong()
        }
    }

    private fun pauseSong() {
        if (mSongs.isEmpty())
            return

        initMediaPlayerIfNeeded()

        mPlayer!!.pause()
        songStateChanged(false)
    }

    private fun resumeSong() {
        if (mSongs.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        if (mCurrSong == null) {
            setupNextSong()
        } else {
            mPlayer!!.start()
            requestAudioFocus()
        }

        songStateChanged(true)
    }

    private fun setupNextSong() {
        if (mIsThirdPartyIntent) {
            setupSong()
        } else {
            setSong(getNewSongId(), true)
        }
    }

    private fun restartSong() {
        if (mPlayedSongIndexes.isNotEmpty())
            setSong(mPlayedSongIndexes[mPlayedSongIndexes.size - 1], false)
    }

    private fun playSong(intent: Intent) {
        if (mIsThirdPartyIntent) {
            setupSong()
        } else {
            mPlayOnPrepare = true
            val pos = intent.getIntExtra(SONG_POS, 0)
            setSong(pos, true)
        }
    }

    private fun setSong(songIndex: Int, addNewSongToHistory: Boolean) {
        if (mSongs.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        mPlayer!!.reset()
        if (addNewSongToHistory) {
            mPlayedSongIndexes.add(songIndex)
            if (mPlayedSongIndexes.size >= mSongs.size) {
                mPlayedSongIndexes.clear()
            }
        }

        mCurrSong = mSongs[Math.min(songIndex, mSongs.size - 1)]

        try {
            val trackUri = if (mCurrSong!!.id == 0L) {
                Uri.fromFile(File(mCurrSong!!.path))
            } else {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCurrSong!!.id)
            }
            mPlayer!!.setDataSource(applicationContext, trackUri)
            mPlayer!!.prepareAsync()

            mBus!!.post(Events.SongChanged(mCurrSong))
        } catch (e: IOException) {
            Log.e(TAG, "setSong IOException $e")
        }
    }

    private fun handleEmptyPlaylist() {
        mPlayer!!.pause()
        abandonAudioFocus()
        mCurrSong = null
        mBus!!.post(Events.SongChanged(null))
        songStateChanged(false)
    }

    override fun onBind(intent: Intent) = null

    override fun onCompletion(mp: MediaPlayer) {
        if (!config.autoplay)
            return

        if (config.repeatSong) {
            restartSong()
        } else if (mPlayer!!.currentPosition > 0) {
            mPlayer!!.reset()
            setupNextSong()
        }
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        mPlayer!!.reset()
        return false
    }

    override fun onPrepared(mp: MediaPlayer) {
        if (mPlayOnPrepare) {
            mp.start()
            requestAudioFocus()
        }
        songStateChanged(getIsPlaying())
        setupNotification()
    }

    private fun destroyPlayer() {
        mPlayer?.stop()
        mPlayer?.release()
        mPlayer = null

        if (mBus != null) {
            songStateChanged(false)
            mBus!!.post(Events.SongChanged(null))
            mBus!!.unregister(this)
        }

        mEqualizer?.release()

        stopForeground(true)
        stopSelf()
        mIsThirdPartyIntent = false
        isServiceInitialized = false

        val remoteControlComponent = ComponentName(packageName, RemoteControlReceiver::class.java.name)
        mAudioManager!!.unregisterMediaButtonEventReceiver(remoteControlComponent)
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        if (isOreoPlus()) {
            mOreoFocusHandler?.requestAudioFocus(this)
        } else {
            mAudioManager?.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (isOreoPlus()) {
            mOreoFocusHandler?.abandonAudioFocus()
        } else {
            mAudioManager?.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AUDIOFOCUS_GAIN -> audioFocusGained()
            AUDIOFOCUS_LOSS, AUDIOFOCUS_LOSS_TRANSIENT, AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioFocusLost()
        }
    }

    private fun audioFocusLost() {
        if (getIsPlaying()) {
            mWasPlayingAtFocusLost = true
            pauseSong()
        } else {
            mWasPlayingAtFocusLost = false
        }
    }

    private fun audioFocusGained() {
        if (mWasPlayingAtFocusLost) {
            resumeSong()
        }

        mWasPlayingAtFocusLost = false
    }

    private fun updateProgress(progress: Int) {
        mPlayer!!.seekTo(progress * 1000)
        resumeSong()
    }

    private fun songStateChanged(isPlaying: Boolean) {
        handleProgressHandler(isPlaying)
        setupNotification()
        mBus!!.post(Events.SongStateChanged(isPlaying))

        if (isPlaying) {
            val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            registerReceiver(mHeadsetPlugReceiver, filter)
        } else {
            try {
                unregisterReceiver(mHeadsetPlugReceiver)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "IllegalArgumentException $e")
            }
        }
    }

    private fun handleProgressHandler(isPlaying: Boolean) {
        if (isPlaying) {
            mProgressHandler!!.post(object : Runnable {
                override fun run() {
                    val secs = mPlayer!!.currentPosition / 1000
                    mBus!!.post(Events.ProgressUpdated(secs))
                    mProgressHandler!!.removeCallbacksAndMessages(null)
                    mProgressHandler!!.postDelayed(this, PROGRESS_UPDATE_INTERVAL.toLong())
                }
            })
        } else {
            mProgressHandler!!.removeCallbacksAndMessages(null)
        }
    }
}
