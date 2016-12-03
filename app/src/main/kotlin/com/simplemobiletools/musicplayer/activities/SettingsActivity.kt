package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.TaskStackBuilder
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.EQUALIZER
import com.simplemobiletools.musicplayer.helpers.SET_EQUALIZER
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupDarkTheme()
        setupNumericProgress()
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

    private fun setupNumericProgress() {
        settings_numeric_progress.isChecked = mConfig.isNumericProgressEnabled
        settings_numeric_progress_holder.setOnClickListener {
            settings_numeric_progress.toggle()
            mConfig.isNumericProgressEnabled = settings_numeric_progress.isChecked
        }
    }

    private fun setupEqualizer() {
        val equalizer = MusicService.mEqualizer ?: return
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
                        putExtra(EQUALIZER, selectedItemPosition)
                        action = SET_EQUALIZER
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
}
