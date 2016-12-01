package com.simplemobiletools.musicplayer.activities;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.simplemobiletools.fileproperties.dialogs.PropertiesDialog;
import com.simplemobiletools.musicplayer.Constants;
import com.simplemobiletools.musicplayer.MusicService;
import com.simplemobiletools.musicplayer.R;
import com.simplemobiletools.musicplayer.models.Song;
import com.simplemobiletools.musicplayer.adapters.SongAdapter;
import com.simplemobiletools.musicplayer.Utils;
import com.simplemobiletools.musicplayer.helpers.BusProvider;
import com.simplemobiletools.musicplayer.models.Events;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends SimpleActivity
        implements ListView.MultiChoiceModeListener, AdapterView.OnItemClickListener, ListView.OnTouchListener,
        MediaScannerConnection.OnScanCompletedListener, SeekBar.OnSeekBarChangeListener {
    private static final int STORAGE_PERMISSION = 1;

    @BindView(R.id.playPauseBtn) ImageView mPlayPauseBtn;
    @BindView(R.id.songs) ListView mSongsList;
    @BindView(R.id.songTitle) TextView mTitleTV;
    @BindView(R.id.songArtist) TextView mArtistTV;
    @BindView(R.id.progressbar) SeekBar mProgressBar;
    @BindView(R.id.song_progress) TextView mProgress;
    @BindView(R.id.previousBtn) ImageView mPreviousBtn;
    @BindView(R.id.nextBtn) ImageView mNextBtn;

    private static Bus mBus;
    private static Song mCurrentSong;
    private static List<Song> mSongs;
    private static Snackbar mSnackbar;
    private static List<String> mToBeDeleted;
    private static Bitmap mPlayBitmap;
    private static Bitmap mPauseBitmap;

    private static boolean mIsSnackbarShown;
    private static boolean mIsNumericProgressShown;
    private static int mSelectedItemsCnt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mBus = BusProvider.Companion.getInstance();
        mBus.register(this);
        mProgressBar.setOnSeekBarChangeListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initializePlayer();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsNumericProgressShown = mConfig.isNumericProgressEnabled();
        setupIconColors();
        if (mIsNumericProgressShown) {
            mProgress.setVisibility(View.VISIBLE);
        } else {
            mProgress.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        menu.findItem(R.id.enable_song_repetition).setVisible(!getMConfig().getRepeatSong());
        menu.findItem(R.id.disable_song_repetition).setVisible(getMConfig().getRepeatSong());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                return true;
            case R.id.about:
                startActivity(new Intent(getApplicationContext(), AboutActivity.class));
                return true;
            case R.id.enable_song_repetition:
                toggleSongRepetition(true);
                return true;
            case R.id.disable_song_repetition:
                toggleSongRepetition(false);
                return true;
            default:
                return super.onOptionsItemSelected(item);
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

    private void toggleSongRepetition(boolean enable) {
        getMConfig().setRepeatSong(enable);
        invalidateOptionsMenu();
    }

    private void initializePlayer() {
        mToBeDeleted = new ArrayList<>();
        mSongsList.setMultiChoiceModeListener(this);
        mSongsList.setOnTouchListener(this);
        mSongsList.setOnItemClickListener(this);
        Utils.sendIntent(this, Constants.INIT);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private void setupIconColors() {
        final Resources res = getResources();
        final int color = mTitleTV.getCurrentTextColor();
        mPreviousBtn.setImageBitmap(Utils.getColoredIcon(res, color, R.mipmap.previous));
        mNextBtn.setImageBitmap(Utils.getColoredIcon(res, color, R.mipmap.next));
        mPlayBitmap = Utils.getColoredIcon(res, color, R.mipmap.play);
        mPauseBitmap = Utils.getColoredIcon(res, color, R.mipmap.pause);
    }

    private void songPicked(int pos) {
        final Intent intent = new Intent(this, MusicService.class);
        intent.putExtra(Constants.SONG_POS, pos);
        intent.setAction(Constants.PLAYPOS);
        startService(intent);
    }

    private void updateSongInfo(Song song) {
        if (song != null) {
            mTitleTV.setText(song.getTitle());
            mArtistTV.setText(song.getArtist());
            mProgressBar.setMax(song.getDuration());
            mProgressBar.setProgress(0);
        } else {
            mTitleTV.setText("");
            mArtistTV.setText("");
        }
    }

    private void fillSongsListView(ArrayList<Song> songs) {
        mSongs = songs;
        final SongAdapter adapter = new SongAdapter(this, songs);
        mSongsList.setAdapter(adapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        deleteSongs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mConfig.setFirstRun(false);
        mBus.unregister(this);
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
        mCurrentSong = event.getSong();
        Log.e("DEBUG", "cur " + mCurrentSong);
        updateSongInfo(mCurrentSong);
    }

    @Subscribe
    public void songStateChanged(Events.SongStateChanged event) {
        if (event.isPlaying()) {
            mPlayPauseBtn.setImageBitmap(mPauseBitmap);
        } else {
            mPlayPauseBtn.setImageBitmap(mPlayBitmap);
        }
    }

    @Subscribe
    public void playlistUpdated(Events.PlaylistUpdated event) {
        fillSongsListView(event.getSongs());
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        if (checked) {
            mSelectedItemsCnt++;
        } else {
            mSelectedItemsCnt--;
        }

        if (mSelectedItemsCnt > 0) {
            mode.setTitle(String.valueOf(mSelectedItemsCnt));
        }

        mode.invalidate();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.cab, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        final MenuItem menuItem = menu.findItem(R.id.cab_edit);
        menuItem.setVisible(mSelectedItemsCnt == 1);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.cab_edit:
                displayEditDialog();
                mode.finish();
                return true;
            case R.id.cab_delete:
                prepareForDeleting();
                mode.finish();
                return true;
            case R.id.cab_properties:
                showProperties();
                return true;
            default:
                return false;
        }
    }

    private void displayEditDialog() {
        final int songIndex = getSelectedSongIndex();
        if (songIndex == -1)
            return;

        final Song selectedSong = mSongs.get(songIndex);
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

        builder.setPositiveButton(R.string.ok, null);
        builder.setNegativeButton(R.string.cancel, null);

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
                    if (mCurrentSong != null && mCurrentSong.equals(selectedSong)) {
                        currSongChanged = true;
                    }

                    final Song songInList = mSongs.get(songIndex);
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

    private void showProperties() {
        final SparseBooleanArray items = mSongsList.getCheckedItemPositions();
        if (items.size() == 1) {
            final Song selectedSong = mSongs.get(getSelectedSongIndex());
            new PropertiesDialog(this, selectedSong.getPath(), false);
        } else {
            final List<String> paths = new ArrayList<>(items.size());
            final int cnt = items.size();
            for (int i = 0; i < cnt; i++) {
                if (items.valueAt(i)) {
                    final int id = items.keyAt(i);
                    paths.add(mSongs.get(id).getPath());
                }
            }

            new PropertiesDialog(this, paths, false);
        }
    }

    private int getSelectedSongIndex() {
        final SparseBooleanArray items = mSongsList.getCheckedItemPositions();
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
        mCurrentSong = newSong;
        ((SongAdapter) mSongsList.getAdapter()).notifyDataSetChanged();
    }

    private void prepareForDeleting() {
        mToBeDeleted.clear();
        final SparseBooleanArray items = mSongsList.getCheckedItemPositions();
        final int cnt = items.size();
        int deletedCnt = 0;
        for (int i = 0; i < cnt; i++) {
            if (items.valueAt(i)) {
                final int id = items.keyAt(i);
                final String path = mSongs.get(id).getPath();
                mToBeDeleted.add(path);
                deletedCnt++;
            }
        }

        notifyDeletion(deletedCnt);
    }

    private void notifyDeletion(int cnt) {
        final CoordinatorLayout coordinator = (CoordinatorLayout) findViewById(R.id.coordinator_layout);
        final Resources res = getResources();
        final String msg = res.getQuantityString(R.plurals.songs_deleted, cnt, cnt);
        mSnackbar = Snackbar.make(coordinator, msg, Snackbar.LENGTH_INDEFINITE);
        mSnackbar.setAction(res.getString(R.string.undo), undoDeletion);
        mSnackbar.setActionTextColor(Color.WHITE);
        mSnackbar.show();
        mIsSnackbarShown = true;
        updateSongsList();
    }

    private void updateSongsList() {
        final Intent intent = new Intent(this, MusicService.class);
        final String[] deletedSongs = new String[mToBeDeleted.size()];
        mToBeDeleted.toArray(deletedSongs);
        intent.putExtra(Constants.DELETED_SONGS, deletedSongs);
        intent.putExtra(Constants.UPDATE_ACTIVITY, true);
        intent.setAction(Constants.REFRESH_LIST);
        startService(intent);
    }

    private void deleteSongs() {
        if (mToBeDeleted == null || mToBeDeleted.isEmpty())
            return;

        if (mSnackbar != null) {
            mSnackbar.dismiss();
        }

        mIsSnackbarShown = false;

        final List<String> updatedFiles = new ArrayList<>();
        for (String delPath : mToBeDeleted) {
            final File file = new File(delPath);
            if (file.exists()) {
                if (file.delete()) {
                    updatedFiles.add(delPath);
                }
            }
        }

        final String[] deletedPaths = updatedFiles.toArray(new String[updatedFiles.size()]);
        MediaScannerConnection.scanFile(this, deletedPaths, null, null);
        mToBeDeleted.clear();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        songPicked(position);
    }

    private View.OnClickListener undoDeletion = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mToBeDeleted.clear();
            mSnackbar.dismiss();
            mIsSnackbarShown = false;
            updateSongsList();
        }
    };

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mSelectedItemsCnt = 0;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mIsSnackbarShown) {
            deleteSongs();
        }

        return false;
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        Utils.sendIntent(this, Constants.REFRESH_LIST);
    }

    @Subscribe
    public void songChangedEvent(Events.ProgressUpdated event) {
        mProgressBar.setProgress(event.getProgress());
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (mIsNumericProgressShown) {
            final String duration = Utils.getTimeString(mProgressBar.getMax());
            final String formattedProgress = Utils.getTimeString(progress);

            final String progressText = String.format(getResources().getString(R.string.progress), formattedProgress, duration);
            mProgress.setText(progressText);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        final Intent intent = new Intent(this, MusicService.class);
        intent.putExtra(Constants.PROGRESS, seekBar.getProgress());
        intent.setAction(Constants.SET_PROGRESS);
        startService(intent);
    }
}
