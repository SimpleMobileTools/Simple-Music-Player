package com.simplemobiletools.musicplayer.activities

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.statusBarHeight
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.TRACK
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.activity_track.*

class TrackActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track)

        (activity_track_appbar.layoutParams as ConstraintLayout.LayoutParams).topMargin = statusBarHeight
        activity_track_holder.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        activity_track_toolbar.setNavigationOnClickListener {
            finish()
        }

        val trackType = object : TypeToken<Song>() {}.type
        val song = Gson().fromJson<Song>(intent.getStringExtra(TRACK), trackType)
        title = song.title
    }

    override fun onResume() {
        super.onResume()
        window.statusBarColor = Color.TRANSPARENT
        activity_track_holder.setBackgroundColor(config.backgroundColor)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }
}
