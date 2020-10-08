package com.simplemobiletools.musicplayer.services

import android.annotation.SuppressLint
import android.app.*
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.AudioManager.*
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.session.PlaybackState.PLAYBACK_POSITION_UNKNOWN
import android.net.Uri
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore.Audio
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Size
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_WRITE_STORAGE
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.receivers.ControlActionsListener
import com.simplemobiletools.musicplayer.receivers.HeadsetPlugReceiver
import com.simplemobiletools.musicplayer.receivers.NotificationDismissedReceiver
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.*

class MusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener, OnAudioFocusChangeListener {
    companion object {
        private const val MIN_INITIAL_DURATION = 30
        private const val PROGRESS_UPDATE_INTERVAL = 1000L
        private const val MAX_CLICK_DURATION = 700L
        private const val FAST_FORWARD_SKIP_MS = 10000
        private const val NOTIFICATION_CHANNEL = "music_player_channel"
        private const val NOTIFICATION_ID = 78    // just a random number

        var mCurrSong: Track? = null
        var mCurrSongCover: Bitmap? = null
        var mEqualizer: Equalizer? = null
        private var mHeadsetPlugReceiver = HeadsetPlugReceiver()
        private var mPlayer: MediaPlayer? = null
        private var mPlayedSongIndexes = ArrayList<Int>()
        private var mProgressHandler = Handler()
        private var mSleepTimer: CountDownTimer? = null
        private var mSongs = ArrayList<Track>()
        private var mAudioManager: AudioManager? = null
        private var mCoverArtHeight = 0
        private var mOreoFocusHandler: OreoAudioFocusHandler? = null

        private var mWasPlayingAtFocusLost = false
        private var mPlayOnPrepare = true
        private var mIsThirdPartyIntent = false
        private var mIntentUri: Uri? = null
        private var mMediaSession: MediaSessionCompat? = null
        private var mIsServiceInitialized = false
        private var mPrevAudioFocusState = 0

        fun getIsPlaying() = mPlayer?.isPlaying == true
    }

    private var mClicksCnt = 0
    private val mRemoteControlHandler = Handler()
    private val mRunnable = Runnable {
        if (mClicksCnt == 0) {
            return@Runnable
        }

        when (mClicksCnt) {
            1 -> handlePlayPause()
            2 -> handleNext()
            else -> handlePrevious()
        }
        mClicksCnt = 0
    }

    override fun onCreate() {
        super.onCreate()
        mCoverArtHeight = resources.getDimension(R.dimen.top_art_height).toInt()
        mMediaSession = MediaSessionCompat(this, "MusicService")
        mMediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS)
        mMediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                handleMediaButton(mediaButtonEvent)
                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        })

        mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (isOreoPlus()) {
            mOreoFocusHandler = OreoAudioFocusHandler(applicationContext)
        }

        if (!isQPlus() && !hasPermission(PERMISSION_WRITE_STORAGE)) {
            EventBus.getDefault().post(Events.NoStoragePermission())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyPlayer()
        SongsDatabase.destroyInstance()
        mMediaSession?.isActive = false
        mSleepTimer?.cancel()
        config.sleepInTS = 0L
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!isQPlus() && !hasPermission(PERMISSION_WRITE_STORAGE)) {
            return START_NOT_STICKY
        }

        val action = intent.action
        if (isOreoPlus() && action != NEXT && action != PREVIOUS && action != PLAYPAUSE) {
            setupFakeNotification()
        }

        when (action) {
            INIT -> handleInit(intent)
            INIT_PATH -> handleInitPath(intent)
            SETUP -> handleSetup()
            PREVIOUS -> handlePrevious()
            PAUSE -> pauseSong()
            PLAYPAUSE -> handlePlayPause()
            NEXT -> handleNext()
            RESET -> handleReset()
            PLAYPOS -> playSong(intent)
            EDIT -> handleEdit(intent)
            FINISH -> handleFinish()
            FINISH_IF_NOT_PLAYING -> finishIfNotPlaying()
            REFRESH_LIST -> handleRefreshList(intent)
            SET_PROGRESS -> handleSetProgress(intent)
            SET_EQUALIZER -> handleSetEqualizer(intent)
            SKIP_BACKWARD -> skip(false)
            SKIP_FORWARD -> skip(true)
            REMOVE_CURRENT_SONG -> handleRemoveCurrentSong()
            REMOVE_SONG_IDS -> handleRemoveSongIDS(intent)
            START_SLEEP_TIMER -> startSleepTimer()
            STOP_SLEEP_TIMER -> stopSleepTimer()
            BROADCAST_STATUS -> broadcastPlayerStatus()
        }

        MediaButtonReceiver.handleIntent(mMediaSession!!, intent)
        setupNotification()
        return START_NOT_STICKY
    }

    private fun initService(intent: Intent?) {
        mSongs.clear()
        mPlayedSongIndexes = ArrayList()
        mCurrSong = null
        if (mIsThirdPartyIntent && mIntentUri != null) {
            val path = getRealPathFromURI(mIntentUri!!) ?: ""
            val song = RoomHelper(this).getSongFromPath(path)
            if (song != null) {
                mSongs.add(song)
            }
        } else {
            getSortedSongs()

            val wantedSongId = intent?.getLongExtra(TRACK_ID, -1L)
            mCurrSong = mSongs.firstOrNull { it.id == wantedSongId }
        }

        mWasPlayingAtFocusLost = false
        initMediaPlayerIfNeeded()
        setupNotification()
        mIsServiceInitialized = true
    }

    private fun handleInit(intent: Intent? = null) {
        mIsThirdPartyIntent = false
        ensureBackgroundThread {
            initService(intent)

            val wantedTrackId = intent?.getLongExtra(TRACK_ID, -1L) ?: -1L
            setTrack(wantedTrackId, true)
        }
    }

    private fun handleInitPath(intent: Intent) {
        mIsThirdPartyIntent = true
        if (mIntentUri != intent.data) {
            mIntentUri = intent.data
            initService(intent)
            initSongs()
        } else {
            updateUI()
        }
    }

    private fun handleSetup() {
        mPlayOnPrepare = true
        setupNextSong()
    }

    private fun handlePrevious() {
        mPlayOnPrepare = true
        playPreviousSong()
    }

    private fun handlePlayPause() {
        mPlayOnPrepare = true
        if (getIsPlaying()) {
            pauseSong()
        } else {
            resumeSong()
        }
    }

    private fun handleNext() {
        mPlayOnPrepare = true
        setupNextSong()
    }

    private fun handleReset() {
        if (mPlayedSongIndexes.size - 1 != -1) {
            mPlayOnPrepare = true
            //setTrack(mPlayedSongIndexes[mPlayedSongIndexes.size - 1], false)
        }
    }

    private fun handleEdit(intent: Intent) {
        mCurrSong = intent.getSerializableExtra(EDITED_SONG) as Track
        songChanged(mCurrSong)
    }

    private fun finishIfNotPlaying() {
        if (!getIsPlaying()) {
            handleFinish()
        }
    }

    private fun handleFinish() {
        EventBus.getDefault().post(Events.ProgressUpdated(0))
        destroyPlayer()
    }

    private fun handleRefreshList(intent: Intent) {
        mSongs.clear()
        ensureBackgroundThread {
            getSortedSongs()
            EventBus.getDefault().post(Events.PlaylistUpdated(mSongs))

            if (intent.getBooleanExtra(CALL_SETUP_AFTER, false)) {
                mPlayOnPrepare = false
                setupNextSong()
            }

        }
    }

    private fun handleSetProgress(intent: Intent) {
        if (mPlayer != null) {
            val progress = intent.getIntExtra(PROGRESS, mPlayer!!.currentPosition / 1000)
            updateProgress(progress)
        }
    }

    private fun handleSetEqualizer(intent: Intent) {
        if (intent.extras?.containsKey(EQUALIZER) == true) {
            val presetID = intent.extras?.getInt(EQUALIZER) ?: 0
            if (mEqualizer != null) {
                setPreset(presetID)
            }
        }
    }

    private fun handleRemoveCurrentSong() {
        pauseSong()
        mCurrSong = null
        songChanged(null)
    }

    private fun handleRemoveSongIDS(intent: Intent) {
        val ids = intent.getIntegerArrayListExtra(SONG_IDS)
        val songsToRemove = ArrayList<Track>()
        mSongs.sortedDescending().forEach {
            if (ids.contains(it.path.hashCode())) {
                songsToRemove.add(it)
            }
        }
        mSongs.removeAll(songsToRemove)
    }

    private fun setupSong() {
        if (mIsThirdPartyIntent) {
            initMediaPlayerIfNeeded()

            try {
                mPlayer!!.apply {
                    reset()
                    setDataSource(applicationContext, mIntentUri!!)
                    setOnPreparedListener(null)
                    prepare()
                    start()
                }
                requestAudioFocus()

                val song = mSongs.first()
                mSongs.clear()
                mSongs.add(song)
                mCurrSong = song
                updateUI()
            } catch (ignored: Exception) {
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
        if (mPlayer != null) {
            EventBus.getDefault().post(Events.PlaylistUpdated(mSongs))
            mCurrSongCover = getAlbumImage(mCurrSong).first
            broadcastSongChange(mCurrSong)

            val secs = mPlayer!!.currentPosition / 1000
            EventBus.getDefault().post(Events.ProgressUpdated(secs))
        }
        songStateChanged(getIsPlaying())
    }

    private fun initMediaPlayerIfNeeded() {
        if (mPlayer != null) {
            return
        }

        mPlayer = MediaPlayer().apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(STREAM_MUSIC)
            setOnPreparedListener(this@MusicService)
            setOnCompletionListener(this@MusicService)
            setOnErrorListener(this@MusicService)
        }
        setupEqualizer()
    }

    private fun getAllDeviceSongs() {
        val uri = Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            Audio.Media._ID,
            Audio.Media.DURATION,
            Audio.Media.DATA,
            Audio.Media.ALBUM_ID,
            Audio.Media.ALBUM,
            Audio.Media.TITLE,
            Audio.Media.TRACK,
            Audio.Media.ARTIST)

        if (isQPlus()) {
            val showFilename = config.showFilename
            val songs = ArrayList<Track>()
            queryCursor(uri, projection) { cursor ->
                val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
                if (duration > MIN_INITIAL_DURATION) {
                    val id = cursor.getLongValue(Audio.Media._ID)
                    val path = ContentUris.withAppendedId(uri, id).toString()
                    val title = cursor.getStringValue(Audio.Media.TITLE)
                    val artist = cursor.getStringValue(Audio.Media.ARTIST)
                    val album = cursor.getStringValue(Audio.Media.ALBUM)
                    val albumId = cursor.getLongValue(Audio.Media.ALBUM_ID)
                    val coverArt = ContentUris.withAppendedId(artworkUri, albumId).toString()
                    val trackId = cursor.getIntValue(Audio.Media.TRACK) % 1000
                    val song = Track(id, title, artist, path, duration, album, coverArt, ALL_SONGS_PLAYLIST_ID, trackId)
                    song.title = song.getProperTitle(showFilename)
                    songs.add(song)
                }
            }
            RoomHelper(this).addSongsToPlaylist(songs)
        } else {
            val ignoredPaths = config.ignoredPaths
            val paths = ArrayList<String>()
            queryCursor(uri, projection) { cursor ->
                val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
                if (duration > MIN_INITIAL_DURATION) {
                    val path = cursor.getStringValue(Audio.Media.DATA)
                    if (!ignoredPaths.contains(path) && !path.doesThisOrParentHaveNoMedia()) {
                        paths.add(path)
                    }
                }
            }

            val storedAllSongPaths = songsDAO.getSongsFromPlaylist(ALL_SONGS_PLAYLIST_ID).map { it.path }
            paths.removeAll(storedAllSongPaths)
            RoomHelper(this).addPathsToPlaylist(paths)
        }
    }

    private fun getSortedSongs() {
        if (config.currentPlaylist == ALL_SONGS_PLAYLIST_ID) {
            getAllDeviceSongs()
        }

        mSongs = getQueuedTracks()
        Track.sorting = config.sorting
        try {
            mSongs.sort()
        } catch (ignored: Exception) {
        }
    }

    private fun setupEqualizer() {
        try {
            mEqualizer = Equalizer(1, mPlayer!!.audioSessionId)
        } catch (e: Exception) {
            showErrorToast(e)
            mEqualizer?.enabled = true
            setPreset(config.equalizer)
        }
    }

    private fun setPreset(id: Int) {
        try {
            mEqualizer?.usePreset(id.toShort())
        } catch (ignored: IllegalArgumentException) {
        }
    }

    private fun getQueuedTracks(): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        val allTracks = songsDAO.getAll()
        val queueItemIds = queueDAO.getAll().map { it.trackId }.toMutableList().getChoppedList()
        queueItemIds.forEach { wantedTrackIds ->
            val wantedTracks = allTracks.filter { wantedTrackIds.contains(it.id) }
            tracks.addAll(wantedTracks)
        }

        return tracks
    }

    @SuppressLint("NewApi")
    private fun setupNotification() {
        val title = mCurrSong?.title ?: ""
        val artist = mCurrSong?.artist ?: ""
        val playPauseIcon = if (getIsPlaying()) R.drawable.ic_pause_vector else R.drawable.ic_play_vector

        var notifWhen = 0L
        var showWhen = false
        var usesChronometer = false
        var ongoing = false
        if (getIsPlaying()) {
            notifWhen = System.currentTimeMillis() - (mPlayer?.currentPosition ?: 0)
            showWhen = true
            usesChronometer = true
            ongoing = true
        }

        if (isOreoPlus()) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val name = resources.getString(R.string.app_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            NotificationChannel(NOTIFICATION_CHANNEL, name, importance).apply {
                enableLights(false)
                enableVibration(false)
                notificationManager.createNotificationChannel(this)
            }
        }

        if (mCurrSongCover?.isRecycled == true) {
            mCurrSongCover = resources.getColoredBitmap(R.drawable.ic_headset, config.textColor)
        }

        val notificationDismissedIntent = Intent(this, NotificationDismissedReceiver::class.java).apply {
            action = NOTIFICATION_DISMISSED
        }
        val notificationDismissedPendingIntent = PendingIntent.getBroadcast(this, 0, notificationDismissedIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_headset_small)
            .setLargeIcon(mCurrSongCover)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setWhen(notifWhen)
            .setShowWhen(showWhen)
            .setUsesChronometer(usesChronometer)
            .setContentIntent(getContentIntent())
            .setOngoing(ongoing)
            .setChannelId(NOTIFICATION_CHANNEL)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mMediaSession?.sessionToken))
            .setDeleteIntent(notificationDismissedPendingIntent)
            .addAction(R.drawable.ic_previous_vector, getString(R.string.previous), getIntent(PREVIOUS))
            .addAction(playPauseIcon, getString(R.string.playpause), getIntent(PLAYPAUSE))
            .addAction(R.drawable.ic_next_vector, getString(R.string.next), getIntent(NEXT))

        startForeground(NOTIFICATION_ID, notification.build())

        // delay foreground state updating a bit, so the notification can be swiped away properly after initial display
        Handler(Looper.getMainLooper()).postDelayed({
            if (!getIsPlaying()) {
                stopForeground(false)
            }
        }, 200L)

        val playbackState = if (getIsPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        try {
            mMediaSession!!.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(playbackState, PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build())
        } catch (ignored: IllegalStateException) {
        }
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

    // on Android 8+ the service is launched with startForegroundService(), so startForeground must be called within a few secs
    @SuppressLint("NewApi")
    private fun setupFakeNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = resources.getString(R.string.app_name)
        val importance = NotificationManager.IMPORTANCE_LOW
        NotificationChannel(NOTIFICATION_CHANNEL, name, importance).apply {
            enableLights(false)
            enableVibration(false)
            notificationManager.createNotificationChannel(this)
        }

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(R.drawable.ic_headset_small)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setChannelId(NOTIFICATION_CHANNEL)
            .setCategory(Notification.CATEGORY_SERVICE)

        startForeground(NOTIFICATION_ID, notification.build())
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
            //setTrack(mPlayedSongIndexes[mPlayedSongIndexes.size - 1], false)
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
            //setTrack(getNewSongId(), true)
        }
    }

    private fun restartSong() {
        /*val newSongIndex = if (mPlayedSongIndexes.isEmpty()) 0 else mPlayedSongIndexes[mPlayedSongIndexes.size - 1]
        setTrack(newSongIndex, false)*/
    }

    private fun playSong(intent: Intent) {
        if (mIsThirdPartyIntent) {
            setupSong()
        } else {
            mPlayOnPrepare = true
            /*val pos = intent.getIntExtra(SONG_POS, 0)
            setTrack(pos, true)*/
        }
        mMediaSession?.isActive = true
    }

    private fun setTrack(wantedTrackId: Long, addNewSongToHistory: Boolean) {
        if (mSongs.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        mPlayer!!.reset()
        /*if (addNewSongToHistory) {
            mPlayedSongIndexes.add(trackId)
            if (mPlayedSongIndexes.size >= mSongs.size) {
                mPlayedSongIndexes.clear()
            }
        }*/

        mCurrSong = mSongs.firstOrNull { it.id == wantedTrackId } ?: return

        try {
            val trackUri = if (mCurrSong!!.id == 0L) {
                Uri.fromFile(File(mCurrSong!!.path))
            } else {
                ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, mCurrSong!!.id)
            }
            mPlayer!!.setDataSource(applicationContext, trackUri)
            mPlayer!!.prepareAsync()
            songChanged(mCurrSong)
        } catch (ignored: Exception) {
        }
    }

    private fun handleEmptyPlaylist() {
        mPlayer?.pause()
        abandonAudioFocus()
        mCurrSong = null
        songChanged(null)
        songStateChanged(false)

        if (!mIsServiceInitialized) {
            handleInit()
        }
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

    private fun songChanged(song: Track?) {
        val albumImage = getAlbumImage(song)
        mCurrSongCover = albumImage.first
        broadcastSongChange(song)

        val lockScreenImage = if (albumImage.second) albumImage.first else null
        val metadata = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, lockScreenImage)
            .build()

        mMediaSession?.setMetadata(metadata)
    }

    private fun broadcastSongChange(song: Track?) {
        Handler(Looper.getMainLooper()).post {
            broadcastUpdateWidgetSong(song)
            EventBus.getDefault().post(Events.SongChanged(song))
        }
    }

    private fun broadcastSongStateChange(isPlaying: Boolean) {
        broadcastUpdateWidgetSongState(isPlaying)
        EventBus.getDefault().post(Events.SongStateChanged(isPlaying))
    }

    // do not just return the album cover, but also a boolean to indicate if it a real cover, or just the placeholder
    @SuppressLint("NewApi")
    private fun getAlbumImage(song: Track?): Pair<Bitmap, Boolean> {
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

                val songParentDirectory = File(song.path).parent.trimEnd('/')
                val albumArtFiles = arrayListOf("folder.jpg", "albumart.jpg", "cover.jpg")
                albumArtFiles.forEach {
                    val albumArtFilePath = "$songParentDirectory/$it"
                    if (File(albumArtFilePath).exists()) {
                        val bitmap = BitmapFactory.decodeFile(albumArtFilePath)
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
                }
            } catch (e: Exception) {
            }
        }

        if (isQPlus() && song?.path?.startsWith("content://") == true) {
            try {
                val size = Size(512, 512)
                val thumbnail = contentResolver.loadThumbnail(Uri.parse(song.path), size, null)
                return Pair(thumbnail, true)
            } catch (ignored: Exception) {
            }
        }

        return Pair(resources.getColoredBitmap(R.drawable.ic_headset, config.textColor), false)
    }

    private fun destroyPlayer() {
        mPlayer?.stop()
        mPlayer?.release()
        mPlayer = null

        songStateChanged(false)
        songChanged(null)

        mEqualizer?.release()

        stopForeground(true)
        stopSelf()
        mIsThirdPartyIntent = false
        mIsServiceInitialized = false
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        if (isOreoPlus()) {
            mOreoFocusHandler?.requestAudioFocus(this)
        } else {
            mAudioManager?.requestAudioFocus(this, STREAM_MUSIC, AUDIOFOCUS_GAIN)
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
        mPrevAudioFocusState = focusChange
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
            if (mPrevAudioFocusState == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
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
        mMediaSession?.isActive = isPlaying
        broadcastSongStateChange(isPlaying)

        if (isPlaying) {
            val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            filter.addAction(ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(mHeadsetPlugReceiver, filter)
        } else {
            try {
                unregisterReceiver(mHeadsetPlugReceiver)
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    private fun handleProgressHandler(isPlaying: Boolean) {
        if (isPlaying) {
            mProgressHandler.post(object : Runnable {
                override fun run() {
                    val secs = mPlayer!!.currentPosition / 1000
                    EventBus.getDefault().post(Events.ProgressUpdated(secs))
                    mProgressHandler.removeCallbacksAndMessages(null)
                    mProgressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
                }
            })
        } else {
            mProgressHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun skip(forward: Boolean) {
        val curr = mPlayer?.currentPosition ?: return
        val newProgress = if (forward) curr + FAST_FORWARD_SKIP_MS else curr - FAST_FORWARD_SKIP_MS
        mPlayer!!.seekTo(newProgress)
        resumeSong()
    }

    private fun startSleepTimer() {
        val millisInFuture = config.sleepInTS - System.currentTimeMillis() + 1000L
        mSleepTimer?.cancel()
        mSleepTimer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                EventBus.getDefault().post(Events.SleepTimerChanged(seconds))
            }

            override fun onFinish() {
                EventBus.getDefault().post(Events.SleepTimerChanged(0))
                config.sleepInTS = 0
                sendIntent(FINISH)
            }
        }
        mSleepTimer?.start()
    }

    private fun stopSleepTimer() {
        config.sleepInTS = 0
        mSleepTimer?.cancel()
    }

    // used at updating the widget at create or resize
    private fun broadcastPlayerStatus() {
        broadcastSongStateChange(mPlayer?.isPlaying ?: false)
        broadcastSongChange(mCurrSong)
    }

    private fun handleMediaButton(mediaButtonEvent: Intent) {
        if (mediaButtonEvent.action == Intent.ACTION_MEDIA_BUTTON) {
            val swapPrevNext = config.swapPrevNext
            val event = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (event.action == KeyEvent.ACTION_UP) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> handlePlayPause()
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (swapPrevNext) handleNext() else handlePrevious()
                    KeyEvent.KEYCODE_MEDIA_NEXT -> if (swapPrevNext) handlePrevious() else handleNext()
                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                        mClicksCnt++

                        mRemoteControlHandler.removeCallbacks(mRunnable)
                        if (mClicksCnt >= 3) {
                            mRemoteControlHandler.post(mRunnable)
                        } else {
                            mRemoteControlHandler.postDelayed(mRunnable, MAX_CLICK_DURATION)
                        }
                    }
                }
            }
        }
    }
}
