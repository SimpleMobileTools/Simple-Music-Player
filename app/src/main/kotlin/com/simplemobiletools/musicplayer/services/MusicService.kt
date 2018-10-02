package com.simplemobiletools.musicplayer.services

import android.annotation.SuppressLint
import android.annotation.TargetApi
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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.support.v4.app.NotificationCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
import android.util.Log
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_WRITE_STORAGE
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getPlaylistSongs
import com.simplemobiletools.musicplayer.extensions.songsDAO
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.receivers.ControlActionsListener
import com.simplemobiletools.musicplayer.receivers.HeadsetPlugReceiver
import com.simplemobiletools.musicplayer.receivers.RemoteControlReceiver
import com.squareup.otto.Bus
import java.io.File
import java.util.*

class MusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
    companion object {
        private val TAG = MusicService::class.java.simpleName
        private const val MIN_INITIAL_DURATION = 30
        private const val PROGRESS_UPDATE_INTERVAL = 1000
        private const val MIN_SKIP_LENGTH = 2000
        private const val NOTIFICATION_ID = 78    // just a random number

        var mCurrSong: Song? = null
        var mCurrSongCover: Bitmap? = null
        var mEqualizer: Equalizer? = null
        private var mHeadsetPlugReceiver: HeadsetPlugReceiver? = null
        private var mPlayer: MediaPlayer? = null
        private var mPlayedSongIndexes = ArrayList<Int>()
        private var mBus: Bus? = null
        private var mProgressHandler: Handler? = null
        private var mSongs = ArrayList<Song>()
        private var mAudioManager: AudioManager? = null
        private var mCoverArtHeight = 0
        private var mOreoFocusHandler: OreoAudioFocusHandler? = null

        private var mWasPlayingAtFocusLost = false
        private var mPlayOnPrepare = true
        private var mIsThirdPartyIntent = false
        private var intentUri: Uri? = null
        private var mediaSession: MediaSessionCompat? = null
        private var isServiceInitialized = false
        private var prevAudioFocusState = 0

        fun getIsPlaying() = mPlayer?.isPlaying == true
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate() {
        super.onCreate()

        if (mBus == null) {
            mBus = BusProvider.instance
            mBus!!.register(this)
        }

        mCoverArtHeight = resources.getDimension(R.dimen.top_art_height).toInt()
        mProgressHandler = Handler()
        mediaSession = MediaSessionCompat(this, "MusicService")

        val remoteControlComponent = ComponentName(packageName, RemoteControlReceiver::class.java.name)
        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioManager!!.registerMediaButtonEventReceiver(remoteControlComponent)
        if (isOreoPlus()) {
            mOreoFocusHandler = OreoAudioFocusHandler(applicationContext)
        }

        if (!hasPermission(PERMISSION_WRITE_STORAGE)) {
            mBus!!.post(Events.NoStoragePermission())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyPlayer()
        SongsDatabase.destroyInstance()
        mediaSession?.isActive = false
    }

    private fun initService() {
        mSongs.clear()
        mPlayedSongIndexes = ArrayList()
        mCurrSong = null
        if (mIsThirdPartyIntent && intentUri != null) {
            val path = getRealPathFromURI(intentUri!!) ?: ""
            val song = RoomHelper(this).getSongFromPath(path)
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
                Thread {
                    if (!isServiceInitialized) {
                        initService()
                    }
                    initSongs()
                }.start()
            }
            INIT_PATH -> {
                mIsThirdPartyIntent = true
                if (intentUri != intent.data) {
                    intentUri = intent.data
                    initService()
                    initSongs()
                } else {
                    updateUI()
                }
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
            RESET -> {
                if (mPlayedSongIndexes.size - 1 != -1) {
                    mPlayOnPrepare = true
                    setSong(mPlayedSongIndexes[mPlayedSongIndexes.size - 1], false)
                }
            }
            PLAYPOS -> playSong(intent)
            EDIT -> {
                mCurrSong = intent.getSerializableExtra(EDITED_SONG) as Song
                songChanged(mCurrSong)
                setupNotification()
            }
            FINISH -> {
                mBus!!.post(Events.ProgressUpdated(0))
                destroyPlayer()
            }
            REFRESH_LIST -> {
                mSongs.clear()
                Thread {
                    getSortedSongs()
                    Handler(Looper.getMainLooper()).post {
                        mBus!!.post(Events.PlaylistUpdated(mSongs))
                    }

                    if (intent.getBooleanExtra(CALL_SETUP_AFTER, false)) {
                        mPlayOnPrepare = false
                        setupNextSong()
                    }
                }.start()
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
            SKIP_BACKWARD -> skipBackward()
            SKIP_FORWARD -> skipForward()
            REMOVE_CURRENT_SONG -> {
                pauseSong()
                mCurrSong = null
                songChanged(null)
                setupNotification()
            }
            REMOVE_SONG_IDS -> {
                val ids = intent.getIntegerArrayListExtra(SONG_IDS)
                val songsToRemove = ArrayList<Song>()
                mSongs.forEach {
                    if (ids.contains(it.mediaStoreId.toInt())) {
                        songsToRemove.add(it)
                    }
                }
                mSongs.removeAll(songsToRemove)
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
        if (mCurrSong == null) {
            setupSong()
        }
        updateUI()
    }

    private fun updateUI() {
        Handler(Looper.getMainLooper()).post {
            if (mPlayer != null) {
                mBus!!.post(Events.PlaylistUpdated(mSongs))
                mCurrSongCover = getAlbumImage(mCurrSong).first
                mBus!!.post(Events.SongChanged(mCurrSong))

                val secs = mPlayer!!.currentPosition / 1000
                mBus!!.post(Events.ProgressUpdated(secs))
            }
        }
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

        val storedAllSongPaths = songsDAO.getSongsFromPlaylist(ALL_SONGS_PLAYLIST_ID).map { it.path }
        paths.removeAll(storedAllSongPaths)
        RoomHelper(this).addSongsToPlaylist(paths)
    }

    private fun getSortedSongs() {
        if (config.currentPlaylist == ALL_SONGS_PLAYLIST_ID) {
            getAllDeviceSongs()
        }

        mSongs = getPlaylistSongs(config.currentPlaylist)
        Song.sorting = config.sorting
        try {
            mSongs.sort()
        } catch (ignored: Exception) {
        }
    }

    private fun setupEqualizer() {
        try {
            mEqualizer = Equalizer(1, mPlayer!!.audioSessionId)
        } catch (ignored: Exception) {
        }
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
        val playPauseIcon = if (getIsPlaying()) R.drawable.ic_pause else R.drawable.ic_play
        val showDuration = config.showDuration;

        var notifWhen = 0L
        var showWhen = false
        var usesChronometer = false
        var ongoing = false
        if (getIsPlaying() && showDuration) {
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

        if (mCurrSongCover?.isRecycled == true) {
            mCurrSongCover = resources.getColoredBitmap(R.drawable.ic_headset, config.textColor)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_headset_small)
                .setLargeIcon(mCurrSongCover)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setWhen(notifWhen)
                .setShowWhen(showWhen)
                .setUsesChronometer(usesChronometer)
                .setContentIntent(getContentIntent())
                .setOngoing(ongoing)
                .setChannelId(channelId)
                .setStyle(android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSession?.sessionToken))
                .addAction(R.drawable.ic_previous, getString(R.string.previous), getIntent(PREVIOUS))
                .addAction(playPauseIcon, getString(R.string.playpause), getIntent(PLAYPAUSE))
                .addAction(R.drawable.ic_next, getString(R.string.next), getIntent(NEXT))

        startForeground(NOTIFICATION_ID, notification.build())

        if (!getIsPlaying()) {
            Handler(Looper.getMainLooper()).postDelayed({
                stopForeground(false)
            }, 500)
        }

        val playbackState = if (getIsPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession!!.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(playbackState, PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build())
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
        val newSongIndex = if (mPlayedSongIndexes.isEmpty()) 0 else mPlayedSongIndexes[mPlayedSongIndexes.size - 1]
        setSong(newSongIndex, false)
    }

    private fun playSong(intent: Intent) {
        if (mIsThirdPartyIntent) {
            setupSong()
        } else {
            mPlayOnPrepare = true
            val pos = intent.getIntExtra(SONG_POS, 0)
            setSong(pos, true)
        }
        mediaSession?.isActive = true
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

        mCurrSong = mSongs.getOrNull(Math.min(songIndex, mSongs.size - 1)) ?: return

        try {
            val trackUri = if (mCurrSong!!.mediaStoreId == 0L) {
                Uri.fromFile(File(mCurrSong!!.path))
            } else {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCurrSong!!.mediaStoreId)
            }
            mPlayer!!.setDataSource(applicationContext, trackUri)
            mPlayer!!.prepareAsync()
            songChanged(mCurrSong)
        } catch (e: Exception) {
            Log.e(TAG, "setSong IOException $e")
        }
    }

    private fun handleEmptyPlaylist() {
        mPlayer?.pause()
        abandonAudioFocus()
        mCurrSong = null
        songChanged(null)
        songStateChanged(false)
    }

    override fun onBind(intent: Intent) = null

    override fun onCompletion(mp: MediaPlayer) {
        if (!config.autoplay) {
            return
        }

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

    private fun songChanged(song: Song?) {
        val albumImage = getAlbumImage(song)
        mCurrSongCover = albumImage.first
        Handler(Looper.getMainLooper()).post {
            mBus!!.post(Events.SongChanged(song))
        }

        val lockScreenImage = if (albumImage.second) albumImage.first else null
        val metadata = MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, lockScreenImage)
                .build()

        mediaSession?.setMetadata(metadata)
    }

    // do not just return the album cover, but also a boolean to indicate if it a real cover, or just the placeholder
    private fun getAlbumImage(song: Song?): Pair<Bitmap, Boolean> {
        if (File(song?.path ?: "").exists()) {
            try {
                val mediaMetadataRetriever = MediaMetadataRetriever()
                mediaMetadataRetriever.setDataSource(song!!.path)
                val rawArt = mediaMetadataRetriever.embeddedPicture
                if (rawArt != null) {
                    val options = BitmapFactory.Options()
                    val bitmap = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
                    if (bitmap != null) {
                        val resultBitmap = if (bitmap.height > mCoverArtHeight * 2) {
                            val ratio = bitmap.width / bitmap.height.toFloat()
                            Bitmap.createScaledBitmap(bitmap, (mCoverArtHeight * ratio).toInt(), mCoverArtHeight, false)
                        } else {
                            bitmap
                        }
                        return Pair(resultBitmap, true)
                    }
                }
            } catch (e: Exception) {
            }
        }

        return Pair(resources.getColoredBitmap(R.drawable.ic_headset, config.textColor), false)
    }

    private fun destroyPlayer() {
        mPlayer?.stop()
        mPlayer?.release()
        mPlayer = null

        if (mBus != null) {
            songStateChanged(false)
            songChanged(null)
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
            AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> duckAudio()
            AUDIOFOCUS_LOSS, AUDIOFOCUS_LOSS_TRANSIENT -> audioFocusLost()
        }
        prevAudioFocusState = focusChange
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
            if (prevAudioFocusState == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                unduckAudio()
            } else {
                resumeSong()
            }
        }

        mWasPlayingAtFocusLost = false
    }

    private fun duckAudio() {
        mPlayer?.setVolume(0.3f, 0.3f)
        mWasPlayingAtFocusLost = getIsPlaying()
    }

    private fun unduckAudio() {
        mPlayer?.setVolume(1f, 1f)
    }

    private fun updateProgress(progress: Int) {
        mPlayer!!.seekTo(progress * 1000)
        resumeSong()
    }

    private fun songStateChanged(isPlaying: Boolean) {
        handleProgressHandler(isPlaying)
        setupNotification()
        mediaSession?.isActive = isPlaying
        Handler(Looper.getMainLooper()).post {
            mBus!!.post(Events.SongStateChanged(isPlaying))
        }

        if (isPlaying) {
            val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
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

    private fun skipBackward() {
        skip(false)
    }

    private fun skipForward() {
        skip(true)
    }

    private fun skip(forward: Boolean) {
        val curr = mPlayer!!.currentPosition
        val twoPercents = Math.max(mPlayer!!.duration / 50, MIN_SKIP_LENGTH)
        val newProgress = if (forward) curr + twoPercents else curr - twoPercents
        mPlayer!!.seekTo(newProgress)
        resumeSong()
    }
}
