package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.EQUALIZER
import com.simplemobiletools.musicplayer.helpers.SET_EQUALIZER
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupCustomizeColors()
        setupNumericProgress()
        setupEqualizer()
        updateTextColors(settings_scrollview)
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_holder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupNumericProgress() {
        settings_numeric_progress.isChecked = config.isNumericProgressEnabled
        settings_numeric_progress_holder.setOnClickListener {
            settings_numeric_progress.toggle()
            config.isNumericProgressEnabled = settings_numeric_progress.isChecked
        }
    }

    private fun setupEqualizer() {
        settings_equalizer.apply {
            adapter = getPresetsAdapter()
            setSelection(config.equalizer)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    config.equalizer = selectedItemPosition

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

    private fun getPresetsAdapter(): ArrayAdapter<String> {
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item)
        val equalizer = MusicService.mEqualizer ?: return adapter
        val cnt = equalizer.numberOfPresets.toInt()
        val presets = arrayOfNulls<String>(cnt)
        for (i in 0..cnt - 1) {
            presets[i] = equalizer.getPresetName(i.toShort())
        }
        for (preset in presets)
            adapter.add(preset)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }
}
