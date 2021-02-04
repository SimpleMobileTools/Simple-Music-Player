package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.onSeekBarChangeListener
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.EQUALIZER_PRESET_CUSTOM
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_equalizer.*
import kotlinx.android.synthetic.main.equalizer_band.view.*
import java.text.DecimalFormat

class EqualizerActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)
        updateTextColors(equalizer_holder)
        initMediaPlayer()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("SetTextI18n")
    private fun initMediaPlayer() {
        val player = MusicService.mPlayer ?: MediaPlayer()
        val equalizer = Equalizer(0, player.audioSessionId)
        equalizer.enabled = true
        val minValue = equalizer.bandLevelRange[0]
        val maxValue = equalizer.bandLevelRange[1]
        equalizer_label_top.text = "+${maxValue / 100}"
        equalizer_label_bottom.text = "${minValue / 100}"
        equalizer_label_0.text = (minValue + maxValue).toString()

        equalizer_bands_holder.removeAllViews()

        val bands = equalizer.numberOfBands
        for (band in 0 until bands) {
            val frequency = equalizer.getCenterFreq(band.toShort()) / 1000
            val formatted = formatFrequency(frequency)

            layoutInflater.inflate(R.layout.equalizer_band, equalizer_bands_holder, false).apply {
                equalizer_bands_holder.addView(this)
                this.equalizer_band_label.text = formatted
                this.equalizer_band_label.setTextColor(config.textColor)
                this.equalizer_band_seek_bar.max = maxValue - minValue
                this.equalizer_band_seek_bar.onSeekBarChangeListener {
                    val newValue = it + minValue
                    equalizer.setBandLevel(band.toShort(), newValue.toShort())
                }
            }
        }

        setupPresets(equalizer)
    }

    private fun setupPresets(equalizer: Equalizer) {
        val storedPreset = config.equalizerPreset
        if (storedPreset == EQUALIZER_PRESET_CUSTOM) {
            equalizer_preset.text = getString(R.string.custom)
        } else {
            val presetName = equalizer.getPresetName(storedPreset.toShort())
            if (presetName.isEmpty()) {
                config.equalizerPreset = EQUALIZER_PRESET_CUSTOM
                equalizer_preset.text = getString(R.string.custom)
            } else {
                equalizer_preset.text = presetName
            }
        }

        equalizer_preset.setOnClickListener {
            val items = arrayListOf<RadioItem>()
            (0 until equalizer.numberOfPresets).mapTo(items) {
                RadioItem(it, equalizer.getPresetName(it.toShort()))
            }

            items.add(RadioItem(EQUALIZER_PRESET_CUSTOM, getString(R.string.custom)))

            RadioGroupDialog(this, items, config.equalizerPreset) { presetId ->
                config.equalizerPreset = presetId as Int
                equalizer_preset.text = items.firstOrNull { it.value == presetId }?.title
            }
        }
    }

    // copypasted  from the file size formatter, should be simplified
    private fun formatFrequency(value: Int): String {
        if (value <= 0) {
            return "0 Hz"
        }

        val units = arrayOf("Hz", "kHz", "gHz")
        val digitGroups = (Math.log10(value.toDouble()) / Math.log10(1000.0)).toInt()
        return "${DecimalFormat("#,##0.#").format(value / Math.pow(1000.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}
