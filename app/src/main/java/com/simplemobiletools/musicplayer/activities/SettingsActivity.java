package com.simplemobiletools.musicplayer.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.simplemobiletools.musicplayer.Config;
import com.simplemobiletools.musicplayer.R;

import butterknife.ButterKnife;

public class SettingsActivity extends AppCompatActivity {
    private static Config mConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        mConfig = Config.newInstance(getApplicationContext());
        ButterKnife.bind(this);
    }
}
