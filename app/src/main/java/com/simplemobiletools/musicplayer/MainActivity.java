package com.simplemobiletools.musicplayer;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity
        implements ListView.MultiChoiceModeListener, AdapterView.OnItemClickListener, ListView.OnTouchListener,
        MediaScannerConnection.OnScanCompletedListener {
    private final int STORAGE_PERMISSION = 1;
    private Bus bus;
    private int selectedItemsCnt;
    private Song currentSong;
    private List<Song> songs;
    private Snackbar snackbar;
    private boolean isSnackbarShown;
    private List<String> toBeDeleted;

    @Bind(R.id.playPauseBtn) ImageView playPauseBtn;
    @Bind(R.id.songs) ListView songsList;
    @Bind(R.id.songTitle) TextView titleTV;
    @Bind(R.id.songArtist) TextView artistTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        bus = BusProvider.getInstance();
        bus.register(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initializePlayer();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializePlayer();
            } else {
                Toast.makeText(this, getResources().getString(R.string.no_permissions), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializePlayer() {
        toBeDeleted = new ArrayList<>();
        songsList.setMultiChoiceModeListener(this);
        songsList.setOnTouchListener(this);
        songsList.setOnItemClickListener(this);
        Utils.sendIntent(this, Constants.INIT);
    }

    private void songPicked(int pos) {
        final Intent intent = new Intent(this, MusicService.class);
        intent.putExtra(Constants.SONG_POS, pos);
        intent.setAction(Constants.PLAYPOS);
        startService(intent);
    }

    private void updateSongInfo(Song song) {
        if (song != null) {
            titleTV.setText(song.getTitle());
            artistTV.setText(song.getArtist());
        }
    }

    private void fillSongsListView(ArrayList<Song> songs) {
        this.songs = songs;
        final SongAdapter adapter = new SongAdapter(this, songs);
        songsList.setAdapter(adapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        deleteSongs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bus.unregister(this);
    }

    @OnClick(R.id.previousBtn)
    public void previousClicked() {
        Utils.sendIntent(this, Constants.PREVIOUS);
    }

    @OnClick(R.id.playPauseBtn)
    public void playPauseClicked() {
        Utils.sendIntent(this, Constants.PLAYPAUSE);
    }

    @OnClick(R.id.nextBtn)
    public void nextClicked() {
        Utils.sendIntent(this, Constants.NEXT);
    }

    @Subscribe
    public void songChangedEvent(Events.SongChanged event) {
        currentSong = event.getSong();
        updateSongInfo(currentSong);
    }

    @Subscribe
    public void songStateChanged(Events.SongStateChanged event) {
        int id = R.mipmap.play;
        if (event.getIsPlaying())
            id = R.mipmap.pause;

        playPauseBtn.setImageDrawable(getResources().getDrawable(id));
    }

    @Subscribe
    public void playlistUpdated(Events.PlaylistUpdated event) {
        fillSongsListView(event.getSongs());
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        if (checked) {
            selectedItemsCnt++;
        } else {
            selectedItemsCnt--;
        }

        if (selectedItemsCnt > 0) {
            mode.setTitle(String.valueOf(selectedItemsCnt));
        }

        mode.invalidate();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        final MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.cab, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        final MenuItem menuItem = menu.findItem(R.id.cab_edit);
        menuItem.setVisible(selectedItemsCnt == 1);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.cab_edit:
                displayEditDialog();
                mode.finish();
                return true;
            case R.id.cab_remove:
                prepareForDeleting();
                mode.finish();
                return true;
            default:
                return false;
        }
    }

    private void displayEditDialog() {
        final int songIndex = getSelectedSongIndex();
        if (songIndex == -1)
            return;

        final Song selectedSong = songs.get(songIndex);
        if (selectedSong == null)
            return;

        final String title = selectedSong.getTitle();
        final String artist = selectedSong.getArtist();

        final View renameSongView = getLayoutInflater().inflate(R.layout.rename_song, null);
        final EditText titleET = (EditText) renameSongView.findViewById(R.id.title);
        titleET.setText(title);

        final EditText artistET = (EditText) renameSongView.findViewById(R.id.artist);
        artistET.setText(artist);

        final String fullName = Utils.getFilename(selectedSong.getPath());
        final int dotAt = fullName.lastIndexOf(".");
        if (dotAt <= 0)
            return;

        final String fileName = fullName.substring(0, dotAt);
        final EditText fileNameET = (EditText) renameSongView.findViewById(R.id.file_name);
        fileNameET.setText(fileName);

        final String fileExtension = fullName.substring(dotAt + 1, fullName.length());
        final EditText fileExtensionET = (EditText) renameSongView.findViewById(R.id.extension);
        fileExtensionET.setText(fileExtension);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.rename_song));
        builder.setView(renameSongView);

        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Cancel", null);

        final AlertDialog alertDialog = builder.create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String newTitle = Utils.getViewText(titleET);
                final String newArtist = Utils.getViewText(artistET);
                final String newFileName = Utils.getViewText(fileNameET);
                final String newFileExtension = Utils.getViewText(fileExtensionET);

                if (newTitle.isEmpty() || newArtist.isEmpty() || newFileName.isEmpty() || newFileExtension.isEmpty()) {
                    Utils.showToast(getApplicationContext(), R.string.rename_song_empty);
                    return;
                }

                final Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                if (updateContentResolver(uri, selectedSong.getId(), newTitle, newArtist)) {
                    getContentResolver().notifyChange(uri, null);
                    boolean currSongChanged = false;
                    if (currentSong != null && currentSong.equals(selectedSong)) {
                        currSongChanged = true;
                    }

                    final Song songInList = songs.get(songIndex);
                    songInList.setTitle(newTitle);
                    songInList.setArtist(newArtist);

                    if (currSongChanged) {
                        notifyCurrentSongChanged(songInList);
                    }

                    final File file = new File(selectedSong.getPath());
                    final File newFile = new File(file.getParent(), newFileName + "." + newFileExtension);
                    if (file.equals(newFile)) {
                        alertDialog.dismiss();
                        return;
                    }

                    if (file.renameTo(newFile)) {
                        songInList.setPath(newFile.getAbsolutePath());
                        final String[] changedFiles = {file.getAbsolutePath(), newFile.getAbsolutePath()};
                        MediaScannerConnection.scanFile(getApplicationContext(), changedFiles, null, MainActivity.this);

                        alertDialog.dismiss();
                        return;
                    }

                    Utils.showToast(getApplicationContext(), R.string.rename_song_error);
                }
            }
        });
    }

    private boolean updateContentResolver(Uri uri, long songID, String newSongTitle, String newSongArtist) {
        final String where = MediaStore.Images.Media._ID + " = ? ";
        final String[] args = {String.valueOf(songID)};

        final ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.TITLE, newSongTitle);
        values.put(MediaStore.Audio.Media.ARTIST, newSongArtist);

        return getContentResolver().update(uri, values, where, args) == 1;
    }

    private int getSelectedSongIndex() {
        final SparseBooleanArray items = songsList.getCheckedItemPositions();
        int cnt = items.size();
        for (int i = 0; i < cnt; i++) {
            if (items.valueAt(i)) {
                return items.keyAt(i);
            }
        }
        return -1;
    }

    private void notifyCurrentSongChanged(Song newSong) {
        final Intent intent = new Intent(this, MusicService.class);
        intent.putExtra(Constants.EDITED_SONG, newSong);
        intent.setAction(Constants.EDIT);
        startService(intent);
        currentSong = newSong;
        ((SongAdapter) songsList.getAdapter()).notifyDataSetChanged();
    }

    private void prepareForDeleting() {
        toBeDeleted.clear();
        final SparseBooleanArray items = songsList.getCheckedItemPositions();
        int cnt = items.size();
        int deletedCnt = 0;
        for (int i = 0; i < cnt; i++) {
            if (items.valueAt(i)) {
                final int id = items.keyAt(i);
                final String path = songs.get(id).getPath();
                toBeDeleted.add(path);
                deletedCnt++;
            }
        }

        notifyDeletion(deletedCnt);
    }

    private void notifyDeletion(int cnt) {
        final CoordinatorLayout coordinator = (CoordinatorLayout) findViewById(R.id.coordinator_layout);
        final Resources res = getResources();
        final String msg = res.getQuantityString(R.plurals.songs_deleted, cnt, cnt);
        snackbar = Snackbar.make(coordinator, msg, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(res.getString(R.string.undo), undoDeletion);
        snackbar.setActionTextColor(Color.WHITE);
        snackbar.show();
        isSnackbarShown = true;
        updateSongsList();
    }

    private void updateSongsList() {
        final Intent intent = new Intent(this, MusicService.class);
        final String[] deletedSongs = new String[toBeDeleted.size()];
        toBeDeleted.toArray(deletedSongs);
        intent.putExtra(Constants.DELETED_SONGS, deletedSongs);
        intent.putExtra(Constants.UPDATE_ACTIVITY, true);
        intent.setAction(Constants.REFRESH_LIST);
        startService(intent);
    }

    private void deleteSongs() {
        if (toBeDeleted.isEmpty())
            return;

        if (snackbar != null) {
            snackbar.dismiss();
        }

        isSnackbarShown = false;

        final List<String> updatedFiles = new ArrayList<>();
        for (String delPath : toBeDeleted) {
            final File file = new File(delPath);
            if (file.exists()) {
                if (file.delete()) {
                    updatedFiles.add(delPath);
                }
            }
        }

        final String[] deletedPaths = updatedFiles.toArray(new String[updatedFiles.size()]);
        MediaScannerConnection.scanFile(this, deletedPaths, null, null);
        toBeDeleted.clear();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        songPicked(position);
    }

    private View.OnClickListener undoDeletion = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            toBeDeleted.clear();
            snackbar.dismiss();
            isSnackbarShown = false;
            updateSongsList();
        }
    };

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        selectedItemsCnt = 0;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (isSnackbarShown) {
            deleteSongs();
        }

        return false;
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        Utils.sendIntent(this, Constants.REFRESH_LIST);
    }
}
