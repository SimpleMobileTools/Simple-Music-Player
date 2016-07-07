package com.simplemobiletools.musicplayer.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;

import com.simplemobiletools.musicplayer.Config;
import com.simplemobiletools.musicplayer.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class SettingsActivity extends AppCompatActivity {
    @BindView(R.id.settings_shuffle) SwitchCompat mShuffleSwitch;
    @BindView(R.id.settings_numeric_progress) SwitchCompat mNumericProgressSwitch;

    private static Config mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mConfig = Config.newInstance(getApplicationContext());
        ButterKnife.bind(this);

        setupShuffle();
        setupNumericProgress();
    }

    private void setupShuffle() {
        mShuffleSwitch.setChecked(mConfig.getIsShuffleEnabled());
    }

    private void setupNumericProgress() {
        mNumericProgressSwitch.setChecked(mConfig.getIsNumericProgressEnabled());
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
}
