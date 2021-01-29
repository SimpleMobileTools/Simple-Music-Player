package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.musicplayer.R

class EqualizerActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }
}
