package musicplayer.simplemobiletools.com;

import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.Log;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class MusicService extends Service
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    private static final String TAG = MusicService.class.getSimpleName();
    private final IBinder musicBind = new MyBinder();
    private ArrayList<Song> songs;
    private MediaPlayer player;
    private ArrayList<Integer> playedSongIDs;
    private Song currSong;
    private Bus bus;
    private HeadsetPlugReceiver headsetPlugReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        songs = new ArrayList<>();
        playedSongIDs = new ArrayList<>();

        if (bus == null) {
            bus = BusProvider.getInstance();
            bus.register(this);
        }

        headsetPlugReceiver = new HeadsetPlugReceiver();
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
        if (player == null)
            initMediaPlayer();

        // remove the latest song from the list
        if (playedSongIDs.size() > 1) {
            playedSongIDs.remove(playedSongIDs.size() - 1);
            setSong(playedSongIDs.get(playedSongIDs.size() - 1), false);
        } else {
            restartSong();
        }
    }

    public void pauseSong() {
        if (player == null)
            initMediaPlayer();

        player.pause();
        songStateChanged(false);

        final IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(headsetPlugReceiver, filter);
    }

    public void resumeSong() {
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

    public void setSongs(ArrayList<Song> songs) {
        this.songs = songs;
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
    }

    public class MyBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    private void songStateChanged(boolean isPlaying) {
        bus.post(new Events.SongStateChanged(isPlaying));

        if (isPlaying) {
            final IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
            registerReceiver(headsetPlugReceiver, filter);
        } else {
            unregisterReceiver(headsetPlugReceiver);
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
}
