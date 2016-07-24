package com.simplemobiletools.musicplayer.activities;

import android.content.Intent;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.SwitchCompat;
import android.widget.ArrayAdapter;

import com.simplemobiletools.musicplayer.Config;
import com.simplemobiletools.musicplayer.Constants;
import com.simplemobiletools.musicplayer.MusicService;
import com.simplemobiletools.musicplayer.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemSelected;

public class SettingsActivity extends SimpleActivity {
    @BindView(R.id.settings_dark_theme) SwitchCompat mDarkThemeSwitch;
    @BindView(R.id.settings_shuffle) SwitchCompat mShuffleSwitch;
    @BindView(R.id.settings_numeric_progress) SwitchCompat mNumericProgressSwitch;
    @BindView(R.id.settings_sorting) AppCompatSpinner mSortingSpinner;
    @BindView(R.id.settings_equalizer) AppCompatSpinner mEqualizerSpinner;

    private static Config mConfig;
    private static Equalizer mEqualizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mConfig = Config.newInstance(getApplicationContext());
        mEqualizer = MusicService.mEqualizer;
        ButterKnife.bind(this);

        setupDarkTheme();
        setupShuffle();
        setupNumericProgress();
        setupSorting();
        setupEqualizer();
    }

    private void setupDarkTheme() {
        mDarkThemeSwitch.setChecked(mConfig.getIsDarkTheme());
    }

    private void setupShuffle() {
        mShuffleSwitch.setChecked(mConfig.getIsShuffleEnabled());
    }

    private void setupNumericProgress() {
        mNumericProgressSwitch.setChecked(mConfig.getIsNumericProgressEnabled());
    }

    private void setupSorting() {
        mSortingSpinner.setSelection(mConfig.getSorting());
    }

    @OnClick(R.id.settings_dark_theme_holder)
    public void handleDarkTheme() {
        mDarkThemeSwitch.setChecked(!mDarkThemeSwitch.isChecked());
        mConfig.setIsDarkTheme(mDarkThemeSwitch.isChecked());
        restartActivity();
    }

    @OnClick(R.id.settings_shuffle_holder)
    public void handleShuffle() {
        mShuffleSwitch.setChecked(!mShuffleSwitch.isChecked());
        mConfig.setShuffle(mShuffleSwitch.isChecked());
    }

    @OnClick(R.id.settings_numeric_progress_holder)
    public void handleNumericProgress() {
        mNumericProgressSwitch.setChecked(!mNumericProgressSwitch.isChecked());
        mConfig.setIsNumericProgressEnabled(mNumericProgressSwitch.isChecked());
    }

    @OnItemSelected(R.id.settings_sorting)
    public void handleSorting() {
        mConfig.setSorting(mSortingSpinner.getSelectedItemPosition());
        updatePlaylist();
    }

    @OnItemSelected(R.id.settings_equalizer)
    public void handleEqualizer() {
        final int pos = mEqualizerSpinner.getSelectedItemPosition();
        mConfig.setEqualizer(pos);

        final Intent intent = new Intent(this, MusicService.class);
        intent.putExtra(Constants.EQUALIZER, pos);
        intent.setAction(Constants.SET_EQUALIZER);
        startService(intent);
    }

    private void restartActivity() {
        TaskStackBuilder.create(getApplicationContext()).addNextIntentWithParentStack(getIntent()).startActivities();
    }

    private void setupEqualizer() {
        final int cnt = mEqualizer.getNumberOfPresets();
        final String[] presets = new String[cnt];
        for (short i = 0; i < cnt; i++) {
            presets[i] = mEqualizer.getPresetName(i);
        }
        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presets);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mEqualizerSpinner.setAdapter(arrayAdapter);
        mEqualizerSpinner.setSelection(mConfig.getEqualizer());
    }

    private void updatePlaylist() {
        final Intent intent = new Intent(this, MusicService.class);
        intent.putExtra(Constants.UPDATE_ACTIVITY, true);
        intent.setAction(Constants.REFRESH_LIST);
        startService(intent);
    }
}
