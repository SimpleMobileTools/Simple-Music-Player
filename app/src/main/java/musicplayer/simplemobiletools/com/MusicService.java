package musicplayer.simplemobiletools.com;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

public class MusicService extends Service
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    private static final String TAG = MusicService.class.getSimpleName();
    private static final int MIN_DURATION_MS = 20000;
    private final IBinder musicBind = new MyBinder();
    private HeadsetPlugReceiver headsetPlugReceiver;
    private IncomingCallReceiver incomingCallReceiver;
    private ArrayList<Song> songs;
    private MediaPlayer player;
    private ArrayList<Integer> playedSongIDs;
    private Song currSong;
    private Bus bus;
    private boolean wasPlayingAtCall;

    @Override
    public void onCreate() {
        super.onCreate();
        songs = new ArrayList<>();
        playedSongIDs = new ArrayList<>();

        if (bus == null) {
            bus = BusProvider.getInstance();
            bus.register(this);
        }

        getSortedSongs();
        headsetPlugReceiver = new HeadsetPlugReceiver();
        incomingCallReceiver = new IncomingCallReceiver();
        wasPlayingAtCall = false;
        initMediaPlayer();
    }

    public void initMediaPlayer() {
        player = new MediaPlayer();
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

    private void fillPlaylist() {
        songs.clear();
        final ContentResolver musicResolver = getContentResolver();
        final Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        final Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if (musicCursor != null && musicCursor.moveToFirst()) {
            final int idIndex = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            final int titleIndex = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            final int artistIndex = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            final int durationIndex = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            do {
                if (musicCursor.getInt(durationIndex) > MIN_DURATION_MS) {
                    final long id = musicCursor.getLong(idIndex);
                    final String title = musicCursor.getString(titleIndex);
                    final String artist = musicCursor.getString(artistIndex);
                    songs.add(new Song(id, title, artist));
                }
            } while (musicCursor.moveToNext());
            musicCursor.close();
        }
    }

    private void getSortedSongs() {
        fillPlaylist();
        Collections.sort(songs, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return a.getTitle().compareTo(b.getTitle());
            }
        });
    }

    private int getNewSongId() {
        final int cnt = songs.size();
        if (cnt == 0) {
            return -1;
        } else if (cnt == 1) {
            return 0;
        } else {
            final Random random = new Random();
            int newSongIndex = playedSongIDs.size() - 1;
            // make sure we do not repeat the same song
            while (newSongIndex == playedSongIDs.size() - 1) {
                newSongIndex = random.nextInt(cnt - 1);
            }
            return newSongIndex;
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void playPreviousSong() {
        if (songs.isEmpty())
            return;

        if (player == null)
            initMediaPlayer();

        // play the previous song if we are less than 5 secs into the song, else restart
        // remove the latest song from the list
        if (playedSongIDs.size() > 1 && player.getCurrentPosition() < 5000) {
            playedSongIDs.remove(playedSongIDs.size() - 1);
            setSong(playedSongIDs.get(playedSongIDs.size() - 1), false);
        } else {
            restartSong();
        }
    }

    public void pauseSong() {
        if (songs.isEmpty())
            return;

        if (player == null)
            initMediaPlayer();

        player.pause();
        songStateChanged(false);
    }

    public void resumeSong() {
        if (songs.isEmpty())
            return;

        if (player == null)
            initMediaPlayer();

        if (currSong == null)
            playNextSong();
        else
            player.start();

        songStateChanged(true);
    }

    public void playNextSong() {
        setSong(getNewSongId(), true);
    }

    public void stopSong() {
        if (player != null) {
            // .stop() seems to misbehave weirdly
            player.pause();
            player.seekTo(0);
            songStateChanged(false);
        }
    }

    private void restartSong() {
        stopSong();
        resumeSong();
    }

    public void setSong(int songId, boolean addNewSong) {
        if (songs.isEmpty())
            return;

        if (player == null)
            initMediaPlayer();

        player.reset();
        if (addNewSong)
            playedSongIDs.add(songId);

        currSong = songs.get(songId);

        try {
            final Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currSong.getId());
            player.setDataSource(getApplicationContext(), trackUri);
        } catch (IOException e) {
            Log.e(TAG, "setSong IOException " + e.getMessage());
        }

        player.prepareAsync();
        bus.post(new Events.SongChanged(currSong));
        songStateChanged(true);
    }

    public Song getCurrSong() {
        return currSong;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (player != null && !player.isPlaying()) {
            destroyPlayer();
        }
        return false;
    }

    public ArrayList<Song> getSongs() {
        return songs;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (player.getCurrentPosition() > 0) {
            player.reset();
            playNextSong();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        player.reset();
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyPlayer();
        if (bus != null)
            bus.unregister(this);
    }

    private void destroyPlayer() {
        player.stop();
        player.release();
        player = null;

        final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(incomingCallReceiver, PhoneStateListener.LISTEN_NONE);
    }

    public class MyBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    private void songStateChanged(boolean isPlaying) {
        bus.post(new Events.SongStateChanged(isPlaying));

        if (isPlaying) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
            registerReceiver(headsetPlugReceiver, filter);

            final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            telephonyManager.listen(incomingCallReceiver, PhoneStateListener.LISTEN_CALL_STATE);
        } else {
            try {
                unregisterReceiver(headsetPlugReceiver);
            } catch (IllegalArgumentException e) {
            }
        }
    }

    @Subscribe
    public void previousSongEvent(Events.PreviousSong event) {
        playPreviousSong();
    }

    @Subscribe
    public void playPauseSongEvent(Events.PlayPauseSong event) {
        if (isPlaying())
            pauseSong();
        else
            resumeSong();
    }

    @Subscribe
    public void nextSongEvent(Events.NextSong event) {
        playNextSong();
    }

    @Subscribe
    public void stopSongEvent(Events.StopSong event) {
        stopSong();
    }

    @Subscribe
    public void pauseSongEvent(Events.PauseSong event) {
        // if the headset is unplugged, pause the song
        pauseSong();
    }

    @Subscribe
    public void incomingCallStart(Events.IncomingCallStart event) {
        if (isPlaying()) {
            wasPlayingAtCall = true;
            pauseSong();
        } else {
            wasPlayingAtCall = false;
        }
    }

    @Subscribe
    public void incomingCallStop(Events.IncomingCallStop event) {
        if (wasPlayingAtCall)
            resumeSong();

        wasPlayingAtCall = false;
    }
}
