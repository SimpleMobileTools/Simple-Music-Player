package musicplayer.simplemobiletools.com;

import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class MusicService extends Service
        implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    private static final String TAG = MusicService.class.getSimpleName();
    private ArrayList<Song> songs;
    private final IBinder musicBind = new MyBinder();
    private MediaPlayer player;
    private ArrayList<Integer> playedSongIDs;
    private Song currSong;

    @Override
    public void onCreate() {
        super.onCreate();
        songs = new ArrayList<>();
        playedSongIDs = new ArrayList<>();

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

    public void playNext() {
        setSong(getNewSongId(), true);
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
            playNext();
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
}
