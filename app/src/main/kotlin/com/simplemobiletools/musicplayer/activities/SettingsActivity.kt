package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.extensions.useEnglishToggled
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupCustomizeColors()
        setupManagePlaylists()
        setupUseEnglish()
        setupShowInfoBubble()
        setupEqualizer()
        updateTextColors(settings_scrollview)
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_holder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf(config.wasUseEnglishToggled || Locale.getDefault().language != "en")
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            useEnglishToggled()
        }
    }

    private fun setupManagePlaylists() {
        settings_manage_playlists_holder.setOnClickListener {
            startActivity(Intent(this, PlaylistsActivity::class.java))
        }
    }

    private fun setupShowInfoBubble() {
        settings_show_info_bubble.isChecked = config.showInfoBubble
        settings_show_info_bubble_holder.setOnClickListener {
            settings_show_info_bubble.toggle()
            config.showInfoBubble = settings_show_info_bubble.isChecked
        }
    }

    private fun setupEqualizer() {
        val equalizer = MusicService.mEqualizer ?: return
        val items = arrayListOf<RadioItem>()
        (0 until equalizer.numberOfPresets).mapTo(items) { RadioItem(it, equalizer.getPresetName(it.toShort())) }

        settings_equalizer.text = items[config.equalizer].title
        settings_equalizer_holder.setOnClickListener {
            RadioGroupDialog(this@SettingsActivity, items, config.equalizer) {
                config.equalizer = it as Int
                settings_equalizer.text = items[it].title
            }
        }
    }
}
