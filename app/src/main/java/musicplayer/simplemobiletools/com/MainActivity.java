package musicplayer.simplemobiletools.com;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private static final int MIN_DURATION_SECS = 20;
    private ArrayList<Song> songs;
    @Bind(R.id.playPauseBtn) ImageView playPauseBtn;
    @Bind(R.id.songs) ListView songsList;
    @Bind(R.id.songTitle) TextView titleTV;
    @Bind(R.id.songArtist) TextView artistTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        songs = new ArrayList<>();

        getSortedSongs();

        final SongAdapter adapter = new SongAdapter(this, songs);
        songsList.setAdapter(adapter);
        songsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                songPicked(position);
            }
        });
    }

    private void songPicked(int pos) {
        updateSongInfo(songs.get(pos));
    }

    private void updateSongInfo(Song song) {
        titleTV.setText(song.getTitle());
        artistTV.setText(song.getArtist());
    }

    private void getSortedSongs() {
        getSongs();
        Collections.sort(songs, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return a.getTitle().compareTo(b.getTitle());
            }
        });
    }

    private void getSongs() {
        final ContentResolver musicResolver = getContentResolver();
        final Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        final Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if (musicCursor != null && musicCursor.moveToFirst()) {
            final int idIndex = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            final int titleIndex = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            final int artistIndex = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            final int durationIndex = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            do {
                if (musicCursor.getInt(durationIndex) > MIN_DURATION_SECS) {
                    final long id = musicCursor.getLong(idIndex);
                    final String title = musicCursor.getString(titleIndex);
                    final String artist = musicCursor.getString(artistIndex);
                    songs.add(new Song(id, title, artist));
                }
            } while (musicCursor.moveToNext());
            musicCursor.close();
        }
    }

    @OnClick(R.id.previousBtn)
    public void previousClicked() {
        if (isPlaylistEmpty())
            return;
    }

    @OnClick(R.id.playPauseBtn)
    public void playPauseClicked() {
        if (isPlaylistEmpty())
            return;
    }

    @OnClick(R.id.nextBtn)
    public void nextClicked() {
        if (isPlaylistEmpty())
            return;
    }

    @OnClick(R.id.stopBtn)
    public void stopClicked() {
        if (isPlaylistEmpty())
            return;
    }

    private boolean isPlaylistEmpty() {
        if (songs == null || songs.isEmpty()) {
            Utils.showToast(this, R.string.playlist_empty);
            return true;
        }
        return false;
    }
}
