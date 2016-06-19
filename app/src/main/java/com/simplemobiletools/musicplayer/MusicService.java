package com.simplemobiletools.musicplayer;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.simplemobiletools.musicplayer.activities.MainActivity;
import com.simplemobiletools.musicplayer.receivers.ControlActionsListener;
import com.simplemobiletools.musicplayer.receivers.HeadsetPlugReceiver;
import com.simplemobiletools.musicplayer.receivers.IncomingCallReceiver;
import com.simplemobiletools.musicplayer.receivers.RemoteControlReceiver;
import com.squareup.otto.Bus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class MusicService extends Service
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    private static final String TAG = MusicService.class.getSimpleName();
    private static final int MIN_DURATION_MS = 20000;

    private HeadsetPlugReceiver mHeadsetPlugReceiver;
    private IncomingCallReceiver mIncomingCallReceiver;
    private ArrayList<Song> mSongs;
    private AudioManager mAudioManager;
    private ComponentName mRemoteControlComponent;
    private BroadcastReceiver headsetPlugReceiver;
    private MediaPlayer mPlayer;
    private ArrayList<Integer> mPlayedSongIndexes;
    private Song mCurrSong;
    private Bus mBus;
    private List<String> mIgnoredPaths;
    private Bitmap mPrevBitmap;
    private Bitmap mPlayBitmap;
    private Bitmap mPauseBitmap;
    private Bitmap mNextBitmap;
    private Bitmap mCloseBitmap;

    private boolean mWasPlayingAtCall;

    @Override
    public void onCreate() {
        super.onCreate();

        if (mBus == null) {
            mBus = BusProvider.getInstance();
            mBus.register(this);
        }

        mRemoteControlComponent = new ComponentName(getPackageName(), RemoteControlReceiver.class.getName());
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.registerMediaButtonEventReceiver(mRemoteControlComponent);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initService();
        } else {
            Toast.makeText(this, getResources().getString(R.string.no_permissions), Toast.LENGTH_SHORT).show();
        }
    }

    private void initService() {
        mSongs = new ArrayList<>();
        mPlayedSongIndexes = new ArrayList<>();
        mIgnoredPaths = new ArrayList<>();
        getSortedSongs();
        mHeadsetPlugReceiver = new HeadsetPlugReceiver();
        mIncomingCallReceiver = new IncomingCallReceiver(this);
        mWasPlayingAtCall = false;
        initMediaPlayerIfNeeded();
        createNotificationButtons();
        setupNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return START_NOT_STICKY;
        }

        final String action = intent.getAction();
        if (action != null) {
            switch (action) {
                case Constants.INIT:
                    if (mSongs == null)
                        initService();
                    mBus.post(new Events.PlaylistUpdated(mSongs));
                    mBus.post(new Events.SongChanged(mCurrSong));
                    songStateChanged(isPlaying());
                    break;
                case Constants.PREVIOUS:
                    playPreviousSong();
                    break;
                case Constants.PAUSE:
                    pauseSong();
                    break;
                case Constants.PLAYPAUSE:
                    if (isPlaying()) {
                        pauseSong();
                    } else {
                        resumeSong();
                    }
                    break;
                case Constants.NEXT:
                    playNextSong();
                    break;
                case Constants.PLAYPOS:
                    playSong(intent);
                    break;
                case Constants.CALL_START:
                    incomingCallStart();
                    break;
                case Constants.CALL_STOP:
                    incomingCallStop();
                    break;
                case Constants.EDIT:
                    mCurrSong = (Song) intent.getSerializableExtra(Constants.EDITED_SONG);
                    mBus.post(new Events.SongChanged(mCurrSong));
                    setupNotification();
                    break;
                case Constants.FINISH:
                    destroyPlayer();
                    break;
                case Constants.REFRESH_LIST:
                    if (intent.getExtras() != null && intent.getExtras().containsKey(Constants.DELETED_SONGS)) {
                        mIgnoredPaths = Arrays.asList(intent.getStringArrayExtra(Constants.DELETED_SONGS));
                    }

                    fillPlaylist();

                    if (intent.getExtras() != null && intent.getExtras().containsKey(Constants.UPDATE_ACTIVITY)) {
                        mBus.post(new Events.PlaylistUpdated(mSongs));
                    }

                    if (mCurrSong != null && mIgnoredPaths.contains(mCurrSong.getPath())) {
                        playNextSong();
                    }
                    break;
                default:
                    break;
            }
        }

        return START_NOT_STICKY;
    }

    public void initMediaPlayerIfNeeded() {
        if (mPlayer != null)
            return;

        mPlayer = new MediaPlayer();
        mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnPreparedListener(this);
        mPlayer.setOnCompletionListener(this);
        mPlayer.setOnErrorListener(this);
    }

    private void fillPlaylist() {
        mSongs.clear();
        final Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        final String[] columns =
                {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA};

        final String order = MediaStore.Audio.Media.TITLE;
        final Cursor cursor = getContentResolver().query(uri, columns, null, null, order);

        if (cursor != null && cursor.moveToFirst()) {
            final int idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
            final int titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            final int artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            final int durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            final int pathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            do {
                if (cursor.getInt(durationIndex) > MIN_DURATION_MS) {
                    final long id = cursor.getLong(idIndex);
                    final String title = cursor.getString(titleIndex);
                    final String artist = cursor.getString(artistIndex);
                    final String path = cursor.getString(pathIndex);

                    if (!mIgnoredPaths.contains(path)) {
                        mSongs.add(new Song(id, title, artist, path));
                    }
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
    }

    private void getSortedSongs() {
        fillPlaylist();
        Collections.sort(mSongs, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return a.getTitle().compareTo(b.getTitle());
            }
        });
    }

    private void createNotificationButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Resources res = getResources();
            final int darkGrey = res.getColor(R.color.dark_grey);
            mPrevBitmap = Utils.getColoredIcon(res, darkGrey, R.mipmap.previous);
            mPlayBitmap = Utils.getColoredIcon(res, darkGrey, R.mipmap.play);
            mPauseBitmap = Utils.getColoredIcon(res, darkGrey, R.mipmap.pause);
            mNextBitmap = Utils.getColoredIcon(res, darkGrey, R.mipmap.next);
            mCloseBitmap = Utils.getColoredIcon(res, darkGrey, R.mipmap.close);
        }
    }

    private void setupNotification() {
        final Intent intent = new Intent(this, ControlActionsListener.class);

        final RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification);
        remoteViews.setInt(R.id.widget_holder, "setBackgroundColor", 0);

        final String title = (mCurrSong == null) ? "" : mCurrSong.getTitle();
        final String artist = (mCurrSong == null) ? "" : mCurrSong.getArtist();
        remoteViews.setTextViewText(R.id.songTitle, title);
        remoteViews.setTextViewText(R.id.songArtist, artist);

        updatePlayPauseButton(remoteViews);

        setupIntent(intent, remoteViews, Constants.PREVIOUS, R.id.previousBtn);
        setupIntent(intent, remoteViews, Constants.PLAYPAUSE, R.id.playPauseBtn);
        setupIntent(intent, remoteViews, Constants.NEXT, R.id.nextBtn);
        setupIntent(intent, remoteViews, Constants.FINISH, R.id.closeBtn);

        remoteViews.setViewVisibility(R.id.closeBtn, View.VISIBLE);
        remoteViews.setInt(R.id.widget_holder, "setBackgroundColor", Color.WHITE);
        final int darkGrey = getResources().getColor(R.color.dark_grey);
        remoteViews.setInt(R.id.songArtist, "setTextColor", darkGrey);
        remoteViews.setInt(R.id.songTitle, "setTextColor", darkGrey);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            remoteViews.setImageViewBitmap(R.id.previousBtn, mPrevBitmap);

            if (isPlaying()) {
                remoteViews.setImageViewBitmap(R.id.playPauseBtn, mPauseBitmap);
            } else {
                remoteViews.setImageViewBitmap(R.id.playPauseBtn, mPlayBitmap);
            }

            remoteViews.setImageViewBitmap(R.id.nextBtn, mNextBitmap);
            remoteViews.setImageViewBitmap(R.id.closeBtn, mCloseBitmap);
        }

        final Notification notification = new Notification.Builder(this).
                setContentTitle(title).
                setContentText(artist).
                setSmallIcon(R.mipmap.speakers).
                build();
        notification.bigContentView = remoteViews;

        final Intent contentIntent = new Intent(this, MainActivity.class);
        notification.contentIntent = PendingIntent.getActivity(this, 0, contentIntent, 0);
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.priority = Notification.PRIORITY_MAX;
        notification.when = System.currentTimeMillis();

        startForeground(1, notification);
    }

    private void setupIntent(Intent intent, RemoteViews remoteViews, String action, int id) {
        intent.setAction(action);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, 0);

        if (remoteViews != null)
            remoteViews.setOnClickPendingIntent(id, pendingIntent);
    }

    private void updatePlayPauseButton(RemoteViews remoteViews) {
        int playPauseIcon = R.mipmap.play;
        if (isPlaying())
            playPauseIcon = R.mipmap.pause;

        remoteViews.setImageViewResource(R.id.playPauseBtn, playPauseIcon);
    }

    private int getNewSongId() {
        final int cnt = mSongs.size();
        if (cnt == 0) {
            return -1;
        } else if (cnt == 1) {
            return 0;
        } else {
            final Random random = new Random();
            int newSongIndex = random.nextInt(cnt);
            while (mPlayedSongIndexes.contains(newSongIndex)) {
                newSongIndex = random.nextInt(cnt);
            }
            return newSongIndex;
        }
    }

    public boolean isPlaying() {
        return mPlayer != null && mPlayer.isPlaying();
    }

    public void playPreviousSong() {
        if (mSongs.isEmpty())
            return;

        initMediaPlayerIfNeeded();

        // play the previous song if we are less than 5 secs into the song, else restart
        // remove the latest song from the list
        if (mPlayedSongIndexes.size() > 1 && mPlayer.getCurrentPosition() < 5000) {
            mPlayedSongIndexes.remove(mPlayedSongIndexes.size() - 1);
            setSong(mPlayedSongIndexes.get(mPlayedSongIndexes.size() - 1), false);
        } else {
            restartSong();
        }
    }

    public void pauseSong() {
        if (mSongs.isEmpty())
            return;

        initMediaPlayerIfNeeded();

        mPlayer.pause();
        songStateChanged(false);
    }

    public void resumeSong() {
        if (mSongs.isEmpty()) {
            fillPlaylist();
        }

        if (mSongs.isEmpty())
            return;

        initMediaPlayerIfNeeded();

        if (mCurrSong == null) {
            playNextSong();
        } else {
            mPlayer.start();
        }

        songStateChanged(true);
    }

    public void playNextSong() {
        setSong(getNewSongId(), true);
    }

    private void restartSong() {
        mPlayer.seekTo(0);
    }

    private void playSong(Intent intent) {
        final int pos = intent.getIntExtra(Constants.SONG_POS, 0);
        setSong(pos, true);
    }

    public void setSong(int songIndex, boolean addNewSong) {
        if (mSongs.isEmpty())
            return;

        final boolean wasPlaying = isPlaying();
        initMediaPlayerIfNeeded();

        mPlayer.reset();
        if (addNewSong) {
            mPlayedSongIndexes.add(songIndex);
            if (mPlayedSongIndexes.size() >= mSongs.size()) {
                mPlayedSongIndexes.clear();
            }
        }

        mCurrSong = mSongs.get(songIndex);

        try {
            final Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCurrSong.getId());
            mPlayer.setDataSource(getApplicationContext(), trackUri);
            mPlayer.prepareAsync();

            mBus.post(new Events.SongChanged(mCurrSong));

            if (!wasPlaying) {
                songStateChanged(true);
            }
        } catch (IOException e) {
            Log.e(TAG, "setSong IOException " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mPlayer.getCurrentPosition() > 0) {
            mPlayer.reset();
            playNextSong();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mPlayer.reset();
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
        setupNotification();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyPlayer();
        mAudioManager.unregisterMediaButtonEventReceiver(mRemoteControlComponent);
    }

    private void destroyPlayer() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }

        if (mBus != null) {
            songStateChanged(false);
            mBus.post(new Events.SongChanged(null));
            mBus.unregister(this);
        }

        final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mIncomingCallReceiver, PhoneStateListener.LISTEN_NONE);

        stopForeground(true);
        stopSelf();
    }

    public void incomingCallStart() {
        if (isPlaying()) {
            mWasPlayingAtCall = true;
            pauseSong();
        } else {
            mWasPlayingAtCall = false;
        }
    }

    public void incomingCallStop() {
        if (mWasPlayingAtCall)
            resumeSong();

        mWasPlayingAtCall = false;
    }

    private void songStateChanged(boolean isPlaying) {
        setupNotification();
        mBus.post(new Events.SongStateChanged(isPlaying));

        if (isPlaying) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
            registerReceiver(mHeadsetPlugReceiver, filter);

            final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            telephonyManager.listen(mIncomingCallReceiver, PhoneStateListener.LISTEN_CALL_STATE);
        } else {
            try {
                unregisterReceiver(mHeadsetPlugReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "IllegalArgumentException " + e.getMessage());
            }
        }
    }
}
