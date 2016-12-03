package com.simplemobiletools.musicplayer.services

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.PowerManager
import android.provider.MediaStore
import android.support.v4.content.ContextCompat
import android.support.v7.app.NotificationCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.simplemobiletools.filepicker.extensions.toast
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.receivers.ControlActionsListener
import com.simplemobiletools.musicplayer.receivers.HeadsetPlugReceiver
import com.simplemobiletools.musicplayer.receivers.IncomingCallReceiver
import com.simplemobiletools.musicplayer.receivers.RemoteControlReceiver
import com.squareup.otto.Bus
import java.io.IOException
import java.util.*

class MusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    companion object {
        private val TAG = MusicService::class.java.simpleName
        private val MIN_DURATION_MS = 20000
        private val PROGRESS_UPDATE_INTERVAL = 1000
        private val NOTIFICATION_ID = 78

        var mCurrSong: Song? = null
        var mEqualizer: Equalizer? = null
        private var mHeadsetPlugReceiver: HeadsetPlugReceiver? = null
        private var mIncomingCallReceiver: IncomingCallReceiver? = null
        private var mSongs: ArrayList<Song>? = null
        private var mPlayer: MediaPlayer? = null
        private var mPlayedSongIndexes: ArrayList<Int>? = null
        private var mBus: Bus? = null
        private var mConfig: Config? = null
        private var mProgressHandler: Handler? = null
        private var mPreviousIntent: PendingIntent? = null
        private var mNextIntent: PendingIntent? = null
        private var mPlayPauseIntent: PendingIntent? = null

        private var mWasPlayingAtCall = false
    }

    override fun onCreate() {
        super.onCreate()

        if (mBus == null) {
            mBus = BusProvider.instance
            mBus!!.register(this)
        }

        mProgressHandler = Handler()
        val remoteControlComponent = ComponentName(packageName, RemoteControlReceiver::class.java.name)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.registerMediaButtonEventReceiver(remoteControlComponent)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initService()
        } else {
            toast(R.string.no_permissions)
        }
    }

    private fun initService() {
        mConfig = Config.newInstance(applicationContext)
        mSongs = ArrayList<Song>()
        mPlayedSongIndexes = ArrayList<Int>()
        mCurrSong = null
        setupIntents()
        getSortedSongs()
        mHeadsetPlugReceiver = HeadsetPlugReceiver()
        mIncomingCallReceiver = IncomingCallReceiver(this)
        mWasPlayingAtCall = false
        initMediaPlayerIfNeeded()
        setupNotification()
    }

    private fun setupIntents() {
        mPreviousIntent = getIntent(PREVIOUS)
        mNextIntent = getIntent(NEXT)
        mPlayPauseIntent = getIntent(PLAYPAUSE)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return START_NOT_STICKY
        }

        val action = intent.action
        if (action != null) {
            when (action) {
                INIT -> {
                    if (mSongs == null)
                        initService()
                    mBus!!.post(Events.PlaylistUpdated(mSongs!!))
                    mBus!!.post(Events.SongChanged(mCurrSong))
                    songStateChanged(getIsPlaying())
                }
                PREVIOUS -> playPreviousSong()
                PAUSE -> pauseSong()
                PLAYPAUSE -> {
                    if (getIsPlaying()) {
                        pauseSong()
                    } else {
                        resumeSong()
                    }
                }
                NEXT -> playNextSong()
                PLAYPOS -> playSong(intent)
                CALL_START -> incomingCallStart()
                CALL_STOP -> incomingCallStop()
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
                    mBus!!.post(Events.PlaylistUpdated(mSongs!!))
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
        }

        return START_NOT_STICKY
    }

    fun initMediaPlayerIfNeeded() {
        if (mPlayer != null)
            return

        mPlayer = MediaPlayer()
        mPlayer!!.apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOnPreparedListener(this@MusicService)
            setOnCompletionListener(this@MusicService)
            setOnErrorListener(this@MusicService)
        }
        setupEqualizer()
    }

    private fun fillPlaylist() {
        mSongs!!.clear()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA)

        val order = MediaStore.Audio.Media.TITLE
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(uri, columns, null, null, order)

            if (cursor?.moveToFirst() == true) {
                val idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val pathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                do {
                    if (cursor.getInt(durationIndex) > MIN_DURATION_MS) {
                        val id = cursor.getLong(idIndex)
                        val title = cursor.getString(titleIndex)
                        val artist = cursor.getString(artistIndex)
                        val path = cursor.getString(pathIndex)
                        val duration = cursor.getInt(durationIndex) / 1000
                        mSongs!!.add(Song(id, title, artist, path, duration))
                    }
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
    }

    private fun getSortedSongs() {
        fillPlaylist()
        Collections.sort(mSongs!!) { a, b ->
            if (mConfig!!.sorting == Config.SORT_BY_TITLE) {
                a.title.compareTo(b.title)
            } else {
                a.artist.compareTo(b.artist)
            }
        }
    }

    private fun setupEqualizer() {
        mEqualizer = Equalizer(0, mPlayer!!.audioSessionId)
        mEqualizer!!.enabled = true
        setPreset(mConfig!!.equalizer)
    }

    private fun setPreset(id: Int) {
        try {
            mEqualizer!!.usePreset(id.toShort())
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "setupEqualizer $e")
        }
    }

    private fun setupNotification() {
        val title = mCurrSong?.title ?: ""
        val artist = mCurrSong?.artist ?: ""
        val playPauseButtonPosition = 1
        val nextButtonPosition = 2
        val playPauseIcon = if (getIsPlaying()) R.mipmap.pause else R.mipmap.play

        var notifWhen: Long = 0
        var showWhen = false
        var usesChronometer = false
        var ongoing = false
        if (getIsPlaying()) {
            notifWhen = System.currentTimeMillis() - mPlayer!!.currentPosition
            showWhen = true
            usesChronometer = true
            ongoing = true
        }

        val notification = NotificationCompat.Builder(this)
                .setStyle(NotificationCompat.MediaStyle().setShowActionsInCompactView(*intArrayOf(playPauseButtonPosition, nextButtonPosition)))
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.mipmap.speakers)
                .setLargeIcon(getAlbumImage())
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setWhen(notifWhen)
                .setShowWhen(showWhen)
                .setUsesChronometer(usesChronometer)
                .setContentIntent(getContentIntent())
                .setOngoing(ongoing)
                .addAction(R.mipmap.previous, getString(R.string.previous), mPreviousIntent)
                .addAction(playPauseIcon, getString(R.string.playpause), mPlayPauseIntent)
                .addAction(R.mipmap.next, getString(R.string.next), mNextIntent)

        startForeground(NOTIFICATION_ID, notification.build())

        if (!getIsPlaying()) {
            stopForeground(false)
        }
    }

    private fun getAlbumImage(): Bitmap {
        if (mCurrSong != null) {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(mCurrSong!!.path)
            val rawArt = mediaMetadataRetriever.embeddedPicture
            if (rawArt != null) {
                val options = BitmapFactory.Options()
                return BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
            }
        }

        return BitmapFactory.decodeResource(resources, R.mipmap.no_album)
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
        return if (mConfig!!.isShuffleEnabled) {
            val cnt = mSongs!!.size
            if (cnt == 0) {
                -1
            } else if (cnt == 1) {
                0
            } else {
                val random = Random()
                var newSongIndex = random.nextInt(cnt)
                while (mPlayedSongIndexes!!.contains(newSongIndex)) {
                    newSongIndex = random.nextInt(cnt)
                }
                newSongIndex
            }
        } else {
            if (mPlayedSongIndexes!!.isEmpty()) {
                return 0
            }

            val lastIndex = mPlayedSongIndexes!![mPlayedSongIndexes!!.size - 1]
            (lastIndex + 1) % mSongs!!.size
        }
    }

    fun getIsPlaying() = mPlayer?.isPlaying == true

    fun playPreviousSong() {
        if (mSongs!!.isEmpty())
            return

        initMediaPlayerIfNeeded()

        // play the previous song if we are less than 5 secs into the song, else restart
        // remove the latest song from the list
        if (mPlayedSongIndexes!!.size > 1 && mPlayer!!.currentPosition < 5000) {
            mPlayedSongIndexes!!.removeAt(mPlayedSongIndexes!!.size - 1)
            setSong(mPlayedSongIndexes!![mPlayedSongIndexes!!.size - 1], false)
        } else {
            restartSong()
        }
    }

    fun pauseSong() {
        if (mSongs!!.isEmpty())
            return

        initMediaPlayerIfNeeded()

        mPlayer!!.pause()
        songStateChanged(false)
    }

    fun resumeSong() {
        if (mSongs!!.isEmpty()) {
            fillPlaylist()
        }

        if (mSongs!!.isEmpty())
            return

        initMediaPlayerIfNeeded()

        if (mCurrSong == null) {
            playNextSong()
        } else {
            mPlayer!!.start()
        }

        songStateChanged(true)
    }

    fun playNextSong() {
        setSong(getNewSongId(), true)
    }

    private fun restartSong() {
        mPlayer!!.seekTo(0)
        setupNotification()
    }

    private fun playSong(intent: Intent) {
        val pos = intent.getIntExtra(SONG_POS, 0)
        setSong(pos, true)
    }

    fun setSong(songIndex: Int, addNewSong: Boolean) {
        if (mSongs!!.isEmpty())
            return

        val wasPlaying = getIsPlaying()
        initMediaPlayerIfNeeded()

        mPlayer!!.reset()
        if (addNewSong) {
            mPlayedSongIndexes!!.add(songIndex)
            if (mPlayedSongIndexes!!.size >= mSongs!!.size) {
                mPlayedSongIndexes!!.clear()
            }
        }

        mCurrSong = mSongs!![songIndex]

        try {
            val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCurrSong!!.id)
            mPlayer!!.setDataSource(applicationContext, trackUri)
            mPlayer!!.prepareAsync()

            mBus!!.post(Events.SongChanged(mCurrSong))

            if (!wasPlaying) {
                songStateChanged(true)
            }
        } catch (e: IOException) {
            Log.e(TAG, "setSong IOException $e")
        }

    }

    override fun onBind(intent: Intent) = null

    override fun onCompletion(mp: MediaPlayer) {
        if (mConfig!!.repeatSong) {
            mPlayer!!.seekTo(0)
            mPlayer!!.start()
            setupNotification()
        } else if (mPlayer!!.currentPosition > 0) {
            mPlayer!!.reset()
            playNextSong()
        }
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        mPlayer!!.reset()
        return false
    }

    override fun onPrepared(mp: MediaPlayer) {
        mp.start()
        setupNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyPlayer()
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

        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.listen(mIncomingCallReceiver, PhoneStateListener.LISTEN_NONE)

        stopForeground(true)
        stopSelf()
    }

    fun incomingCallStart() {
        if (getIsPlaying()) {
            mWasPlayingAtCall = true
            pauseSong()
        } else {
            mWasPlayingAtCall = false
        }
    }

    fun incomingCallStop() {
        if (mWasPlayingAtCall)
            resumeSong()

        mWasPlayingAtCall = false
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

            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.listen(mIncomingCallReceiver, PhoneStateListener.LISTEN_CALL_STATE)
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
