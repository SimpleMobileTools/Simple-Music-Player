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
import com.simplemobiletools.commons.extensions.getColoredBitmap
import com.simplemobiletools.commons.extensions.getRealPathFromURI
import com.simplemobiletools.commons.extensions.hasPermission
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.databases.SongsDatabase
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
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
        private const val PROGRESS_UPDATE_INTERVAL = 1000L
        private const val MAX_CLICK_DURATION = 700L
        private const val FAST_FORWARD_SKIP_MS = 10000
        private const val NOTIFICATION_CHANNEL = "music_player_channel"
        private const val NOTIFICATION_ID = 78    // just a random number

        var mCurrTrack: Track? = null
        var mEqualizer: Equalizer? = null
        var mTracks = ArrayList<Track>()
        private var mCurrTrackCover: Bitmap? = null
        private var mHeadsetPlugReceiver = HeadsetPlugReceiver()
        private var mPlayer: MediaPlayer? = null
        private var mProgressHandler = Handler()
        private var mSleepTimer: CountDownTimer? = null
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
        private var mSetProgressOnPrepare = 0

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
            INIT_QUEUE -> handleInitQueue()
            PREVIOUS -> handlePrevious()
            PAUSE -> pauseTrack()
            PLAYPAUSE -> handlePlayPause()
            NEXT -> handleNext()
            PLAY_TRACK -> playTrack(intent)
            EDIT -> handleEdit(intent)
            FINISH -> handleFinish()
            FINISH_IF_NOT_PLAYING -> finishIfNotPlaying()
            REFRESH_LIST -> handleRefreshList(intent)
            UPDATE_NEXT_TRACK -> broadcastNextTrackChange()
            SET_PROGRESS -> handleSetProgress(intent)
            SET_EQUALIZER -> handleSetEqualizer(intent)
            SKIP_BACKWARD -> skip(false)
            SKIP_FORWARD -> skip(true)
            REMOVE_CURRENT_TRACK -> handleRemoveCurrentTrack()
            REMOVE_TRACK_IDS -> handleRemoveTrackIds(intent)
            START_SLEEP_TIMER -> startSleepTimer()
            STOP_SLEEP_TIMER -> stopSleepTimer()
            BROADCAST_STATUS -> broadcastPlayerStatus()
        }

        MediaButtonReceiver.handleIntent(mMediaSession!!, intent)
        setupNotification()
        return START_NOT_STICKY
    }

    private fun initService(intent: Intent?) {
        mTracks.clear()
        mCurrTrack = null
        if (mIsThirdPartyIntent && mIntentUri != null) {
            val path = getRealPathFromURI(mIntentUri!!) ?: ""
            val track = RoomHelper(this).getTrackFromPath(path)
            if (track != null) {
                mTracks.add(track)
            }
        } else {
            mTracks = getQueuedTracks()

            val wantedTrackId = intent?.getLongExtra(TRACK_ID, -1L)
            mCurrTrack = mTracks.firstOrNull { it.id == wantedTrackId }
            checkTrackOrder()
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
            mPlayOnPrepare = true
            setTrack(wantedTrackId)
        }
    }

    private fun handleInitPath(intent: Intent) {
        mIsThirdPartyIntent = true
        if (mIntentUri != intent.data) {
            mIntentUri = intent.data
            initService(intent)
            initTracks()
        } else {
            updateUI()
        }
    }

    private fun handleInitQueue() {
        ensureBackgroundThread {
            val unsortedTracks = getQueuedTracks()

            mTracks.clear()
            queueDAO.getAll().forEach { queueItem ->
                unsortedTracks.firstOrNull { it.id == queueItem.trackId }?.apply {
                    mTracks.add(this)
                }
            }

            val currentQueueItem = queueDAO.getAll().firstOrNull { it.isCurrent }
            if (currentQueueItem != null) {
                mCurrTrack = mTracks.firstOrNull { it.id == currentQueueItem.trackId }
                mPlayOnPrepare = false
                mSetProgressOnPrepare = currentQueueItem.lastPosition
                setTrack(mCurrTrack!!.id)
            }
        }
    }

    private fun handlePrevious() {
        mPlayOnPrepare = true
        playPreviousTrack()
    }

    private fun handlePlayPause() {
        mPlayOnPrepare = true
        if (getIsPlaying()) {
            pauseTrack()
        } else {
            resumeTrack()
        }
    }

    private fun handleNext() {
        mPlayOnPrepare = true
        setupNextTrack()
    }

    private fun handleEdit(intent: Intent) {
        mCurrTrack = intent.getSerializableExtra(EDITED_TRACK) as Track
        trackChanged()
    }

    private fun finishIfNotPlaying() {
        if (!getIsPlaying()) {
            handleFinish()
        }
    }

    private fun handleFinish() {
        broadcastTrackProgress(0)
        destroyPlayer()
    }

    private fun handleRefreshList(intent: Intent) {
        ensureBackgroundThread {
            mTracks = getQueuedTracks()
            checkTrackOrder()
            EventBus.getDefault().post(Events.QueueUpdated(mTracks))
            broadcastNextTrackChange()

            if (intent.getBooleanExtra(CALL_SETUP_AFTER, false)) {
                mPlayOnPrepare = false
                setupNextTrack()
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

    private fun handleRemoveCurrentTrack() {
        pauseTrack()
        mCurrTrack = null
        trackChanged()
    }

    private fun handleRemoveTrackIds(intent: Intent) {
        val ids = intent.getIntegerArrayListExtra(TRACK_IDS)
        val tracksToRemove = ArrayList<Track>()
        mTracks.sortedDescending().forEach {
            if (ids.contains(it.path.hashCode())) {
                tracksToRemove.add(it)
            }
        }
        mTracks.removeAll(tracksToRemove)
    }

    private fun setupTrack() {
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

                val track = mTracks.first()
                mTracks.clear()
                mTracks.add(track)
                mCurrTrack = track
                updateUI()
            } catch (ignored: Exception) {
            }
        } else {
            mPlayOnPrepare = false
            setupNextTrack()
        }
    }

    private fun initTracks() {
        if (mCurrTrack == null) {
            setupTrack()
        }
        updateUI()
    }

    private fun updateUI() {
        if (mPlayer != null) {
            EventBus.getDefault().post(Events.QueueUpdated(mTracks))
            mCurrTrackCover = getAlbumImage().first
            broadcastTrackChange()

            val secs = mPlayer!!.currentPosition / 1000
            broadcastTrackProgress(secs)
        }
        trackStateChanged(getIsPlaying())
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

    // make sure tracks don't get duplicated in the queue, if they exist in multiple playlists
    private fun getQueuedTracks(): ArrayList<Track> {
        val tracks = ArrayList<Track>()
        val allTracks = tracksDAO.getAll()
        val wantedIds = queueDAO.getAll().map { it.trackId }
        val wantedTracks = allTracks.filter { wantedIds.contains(it.id) }
        tracks.addAll(wantedTracks)
        return tracks.distinctBy { it.id }.toMutableList() as ArrayList<Track>
    }

    private fun checkTrackOrder() {
        if (config.isShuffleEnabled) {
            mTracks.shuffle()

            if (mCurrTrack != null) {
                mTracks.remove(mCurrTrack)
                mTracks.add(0, mCurrTrack!!)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun setupNotification() {
        val title = mCurrTrack?.title ?: ""
        val artist = mCurrTrack?.artist ?: ""
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

        if (mCurrTrackCover?.isRecycled == true) {
            mCurrTrackCover = resources.getColoredBitmap(R.drawable.ic_headset, config.textColor)
        }

        val notificationDismissedIntent = Intent(this, NotificationDismissedReceiver::class.java).apply {
            action = NOTIFICATION_DISMISSED
        }
        val notificationDismissedPendingIntent = PendingIntent.getBroadcast(this, 0, notificationDismissedIntent, PendingIntent.FLAG_CANCEL_CURRENT)

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_headset_small)
            .setLargeIcon(mCurrTrackCover)
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

    private fun getNewTrackId(): Long {
        return when (mTracks.size) {
            0 -> -1L
            1 -> mTracks.first().id
            else -> {
                val currentTrackIndex = mTracks.indexOfFirstOrNull { it.id == mCurrTrack?.id }
                if (currentTrackIndex != null) {
                    val nextTrack = mTracks[(currentTrackIndex + 1) % mTracks.size]
                    nextTrack.id
                } else {
                    -1L
                }
            }
        }
    }

    private fun playPreviousTrack() {
        if (mTracks.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        // play the previous track if we are less than 5 secs into it, else restart
        val currentTrackIndex = mTracks.indexOfFirstOrNull { it.id == mCurrTrack?.id } ?: 0
        if (currentTrackIndex == 0 || mPlayer!!.currentPosition < 5000) {
            restartTrack()
        } else {
            val previousTrack = mTracks[currentTrackIndex - 1]
            setTrack(previousTrack.id)
        }
    }

    private fun pauseTrack() {
        initMediaPlayerIfNeeded()

        mPlayer!!.pause()
        trackStateChanged(false)
    }

    private fun resumeTrack() {
        if (mTracks.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        if (mCurrTrack == null) {
            setupNextTrack()
        } else {
            mPlayer!!.start()
            requestAudioFocus()
        }

        trackStateChanged(true)
    }

    private fun setupNextTrack() {
        if (mIsThirdPartyIntent) {
            setupTrack()
        } else {
            setTrack(getNewTrackId())
        }
    }

    private fun restartTrack() {
        if (mCurrTrack != null) {
            setTrack(mCurrTrack!!.id)
        }
    }

    private fun playTrack(intent: Intent) {
        if (mIsThirdPartyIntent) {
            setupTrack()
        } else {
            mPlayOnPrepare = true
            val trackId = intent.getLongExtra(TRACK_ID, 0L)
            setTrack(trackId)
            broadcastTrackChange()
        }

        mMediaSession?.isActive = true
    }

    private fun setTrack(wantedTrackId: Long) {
        if (mTracks.isEmpty()) {
            handleEmptyPlaylist()
            return
        }

        initMediaPlayerIfNeeded()

        mPlayer!!.reset()
        mCurrTrack = mTracks.firstOrNull { it.id == wantedTrackId } ?: return

        try {
            val trackUri = if (mCurrTrack!!.id == 0L) {
                Uri.fromFile(File(mCurrTrack!!.path))
            } else {
                ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, mCurrTrack!!.id)
            }
            mPlayer!!.setDataSource(applicationContext, trackUri)
            mPlayer!!.prepareAsync()
            trackChanged()
        } catch (ignored: Exception) {
        }
    }

    private fun handleEmptyPlaylist() {
        mPlayer?.pause()
        abandonAudioFocus()
        mCurrTrack = null
        trackChanged()
        trackStateChanged(false)

        if (!mIsServiceInitialized) {
            handleInit()
        }
    }

    override fun onBind(intent: Intent) = null

    override fun onCompletion(mp: MediaPlayer) {
        if (!config.autoplay) {
            return
        }

        if (config.repeatTrack) {
            restartTrack()
        } else if (mPlayer!!.currentPosition > 0) {
            mPlayer!!.reset()
            setupNextTrack()
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
        } else if (mSetProgressOnPrepare > 0) {
            mPlayer?.seekTo(mSetProgressOnPrepare)
            broadcastTrackProgress(mSetProgressOnPrepare / 1000)
            mSetProgressOnPrepare = 0
        }

        trackStateChanged(getIsPlaying())
        setupNotification()
    }

    private fun trackChanged() {
        val albumImage = getAlbumImage()
        mCurrTrackCover = albumImage.first
        broadcastTrackChange()

        val lockScreenImage = if (albumImage.second) albumImage.first else null
        val metadata = MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, lockScreenImage)
            .build()

        mMediaSession?.setMetadata(metadata)
    }

    private fun broadcastTrackChange() {
        Handler(Looper.getMainLooper()).post {
            broadcastUpdateWidgetTrack(mCurrTrack)
            EventBus.getDefault().post(Events.TrackChanged(mCurrTrack))
            broadcastNextTrackChange()
        }
    }

    private fun broadcastTrackStateChange(isPlaying: Boolean) {
        broadcastUpdateWidgetTrackState(isPlaying)
        EventBus.getDefault().post(Events.TrackStateChanged(isPlaying))
    }

    private fun broadcastNextTrackChange() {
        Handler(Looper.getMainLooper()).post {
            val currentTrackIndex = mTracks.indexOfFirstOrNull { it.id == mCurrTrack?.id }
            if (currentTrackIndex != null) {
                val nextTrack = mTracks[(currentTrackIndex + 1) % mTracks.size]
                EventBus.getDefault().post(Events.NextTrackChanged(nextTrack))
            }
        }
    }

    private fun broadcastTrackProgress(progress: Int) {
        EventBus.getDefault().post(Events.ProgressUpdated(progress))
    }

    // do not just return the album cover, but also a boolean to indicate if it a real cover, or just the placeholder
    @SuppressLint("NewApi")
    private fun getAlbumImage(): Pair<Bitmap, Boolean> {
        if (File(mCurrTrack?.path ?: "").exists()) {
            try {
                val mediaMetadataRetriever = MediaMetadataRetriever()
                mediaMetadataRetriever.setDataSource(mCurrTrack!!.path)
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

                val trackParentDirectory = File(mCurrTrack!!.path).parent.trimEnd('/')
                val albumArtFiles = arrayListOf("folder.jpg", "albumart.jpg", "cover.jpg")
                albumArtFiles.forEach {
                    val albumArtFilePath = "$trackParentDirectory/$it"
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

        if (isQPlus() && mCurrTrack?.path?.startsWith("content://") == true) {
            try {
                val size = Size(512, 512)
                val thumbnail = contentResolver.loadThumbnail(Uri.parse(mCurrTrack!!.path), size, null)
                return Pair(thumbnail, true)
            } catch (ignored: Exception) {
            }
        }

        return Pair(resources.getColoredBitmap(R.drawable.ic_headset, config.textColor), false)
    }

    private fun destroyPlayer() {
        val position = mPlayer?.currentPosition ?: 0
        ensureBackgroundThread {
            queueDAO.resetCurrent()

            if (mCurrTrack != null) {
                queueDAO.saveCurrentTrack(mCurrTrack!!.id, position)
            }

            mTracks.forEachIndexed { index, track ->
                queueDAO.setOrder(track.id, index)
            }

            mCurrTrack = null
        }

        mPlayer?.stop()
        mPlayer?.release()
        mPlayer = null

        trackStateChanged(false)
        trackChanged()

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
            pauseTrack()
        } else {
            mWasPlayingAtFocusLost = false
        }
    }

    private fun audioFocusGained() {
        if (mWasPlayingAtFocusLost) {
            if (mPrevAudioFocusState == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                unduckAudio()
            } else {
                resumeTrack()
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
        resumeTrack()
    }

    private fun trackStateChanged(isPlaying: Boolean) {
        handleProgressHandler(isPlaying)
        setupNotification()
        mMediaSession?.isActive = isPlaying
        broadcastTrackStateChange(isPlaying)

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
                    broadcastTrackProgress(secs)
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
        resumeTrack()
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
        broadcastTrackStateChange(mPlayer?.isPlaying ?: false)
        broadcastTrackChange()
        broadcastNextTrackChange()
        broadcastTrackProgress((mPlayer?.currentPosition ?: 0) / 1000)
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
