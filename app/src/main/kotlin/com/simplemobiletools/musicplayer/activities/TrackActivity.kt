package com.simplemobiletools.musicplayer.activities

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.portrait
import com.simplemobiletools.commons.extensions.realScreenSize
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
        val track = Gson().fromJson<Song>(intent.getStringExtra(TRACK), trackType)
        setupTopArt(track.coverArt)
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

    private fun setupTopArt(coverArt: String) {
        val placeholder = getResizedDrawable(resources.getColoredDrawableWithColor(R.drawable.ic_headset, config.textColor))
        val wantedWidth = realScreenSize.x
        val wantedHeight = if (portrait) realScreenSize.x else resources.getDimension(R.dimen.top_art_height).toInt()

        val options = RequestOptions()
            .error(placeholder)
            .centerCrop()

        Glide.with(this)
            .load(coverArt)
            .apply(options)
            .override(wantedWidth, wantedHeight)
            .into(findViewById(R.id.activity_track_image))
    }

    private fun getResizedDrawable(drawable: Drawable): Drawable {
        val size = resources.getDimension(R.dimen.top_art_height).toInt()
        val bitmap = (drawable as BitmapDrawable).bitmap
        val bitmapResized = Bitmap.createScaledBitmap(bitmap, size, size, false)
        return BitmapDrawable(resources, bitmapResized)
    }
}
