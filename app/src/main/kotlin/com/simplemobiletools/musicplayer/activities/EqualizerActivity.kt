package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.musicplayer.R
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
        val player = MediaPlayer()
        val equalizer = Equalizer(0, player.audioSessionId)
        val minValue = equalizer.bandLevelRange[0] / 100
        val maxValue = equalizer.bandLevelRange[1] / 100
        equalizer_label_top.text = "+$maxValue"
        equalizer_label_bottom.text = minValue.toString()
        equalizer_label_0.text = (minValue + maxValue).toString()

        equalizer_bands_holder.removeAllViews()

        val bands = equalizer.numberOfBands
        for (i in 0 until bands) {
            val frequency = equalizer.getCenterFreq(i.toShort()) / 1000
            val formatted = formatFrequency(frequency)
            val range = equalizer.getBandFreqRange(i.toShort())

            layoutInflater.inflate(R.layout.equalizer_band, equalizer_bands_holder, false).apply {
                equalizer_bands_holder.addView(this)
                this.equalizer_band_label.text = formatted
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
