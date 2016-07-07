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

    private static Config mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mConfig = Config.newInstance(getApplicationContext());
        ButterKnife.bind(this);

        setupShuffle();
    }

    private void setupShuffle() {
        mShuffleSwitch.setChecked(mConfig.getIsShuffleEnabled());
    }

    @OnClick(R.id.settings_shuffle_holder)
    public void handleLongTapToTrigger() {
        mShuffleSwitch.setChecked(!mShuffleSwitch.isChecked());
        mConfig.setShuffle(mShuffleSwitch.isChecked());
    }
}
