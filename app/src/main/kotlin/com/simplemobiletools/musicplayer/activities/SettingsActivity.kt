package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.TaskStackBuilder
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.simplemobiletools.musicplayer.Constants
import com.simplemobiletools.musicplayer.MusicService
import com.simplemobiletools.musicplayer.R
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupDarkTheme()
        setupShuffle()
        setupNumericProgress()
        setupSorting()
        setupEqualizer()
    }

    private fun setupDarkTheme() {
        settings_dark_theme.isChecked = mConfig.isDarkTheme
        settings_dark_theme_holder.setOnClickListener {
            settings_dark_theme.toggle()
            mConfig.isDarkTheme = settings_dark_theme.isChecked
            restartActivity()
        }
    }

    private fun setupShuffle() {
        settings_shuffle.isChecked = mConfig.isShuffleEnabled
        settings_shuffle_holder.setOnClickListener {
            settings_shuffle.toggle()
            mConfig.isShuffleEnabled = settings_shuffle.isChecked
        }
    }

    private fun setupNumericProgress() {
        settings_numeric_progress.isChecked = mConfig.isNumericProgressEnabled
        settings_numeric_progress_holder.setOnClickListener {
            settings_numeric_progress.toggle()
            mConfig.isNumericProgressEnabled = settings_numeric_progress.isChecked
        }
    }

    private fun setupSorting() {
        settings_sorting.setSelection(mConfig.sorting)
        settings_sorting.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mConfig.sorting = settings_sorting.selectedItemPosition
                updatePlaylist()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    private fun setupEqualizer() {
        val equalizer = MusicService.mEqualizer
        val cnt = equalizer.numberOfPresets.toInt()
        val presets = arrayOfNulls<String>(cnt)
        for (i in 0..cnt - 1) {
            presets[i] = equalizer.getPresetName(i.toShort())
        }
        val arrayAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, presets)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        settings_equalizer.apply {
            adapter = arrayAdapter
            setSelection(mConfig.equalizer)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    mConfig.equalizer = selectedItemPosition

                    Intent(applicationContext, MusicService::class.java).apply {
                        putExtra(Constants.EQUALIZER, selectedItemPosition)
                        action = Constants.SET_EQUALIZER
                        startService(this)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
        }
    }

    private fun restartActivity() {
        TaskStackBuilder.create(applicationContext).addNextIntentWithParentStack(intent).startActivities()
    }

    private fun updatePlaylist() {
        Intent(this, MusicService::class.java).apply {
            putExtra(Constants.UPDATE_ACTIVITY, true)
            action = Constants.REFRESH_LIST
            startService(this)
        }
    }
}
