package com.simplemobiletools.musicplayer.activities

import android.annotation.SuppressLint
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.musicplayer.R
import kotlinx.android.synthetic.main.activity_equalizer.*

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
        equalizer_label_bottom.text = (equalizer.bandLevelRange[0] / 100).toString()
        equalizer_label_top.text = "+${equalizer.bandLevelRange[1] / 100}"
    }
}
