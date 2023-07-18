package com.simplemobiletools.musicplayer.extensions

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.musicplayer.R

fun Resources.getSmallPlaceholder(color: Int): Drawable {
    val placeholder = getDrawable(R.drawable.ic_headset_padded)
    val resized = resizeDrawable(placeholder, getDimension(R.dimen.song_image_size).toInt())
    resized.applyColorFilter(color)
    return resized
}

fun Resources.getBiggerPlaceholder(color: Int): Drawable {
    val placeholder = getDrawable(R.drawable.ic_headset)
    val resized = resizeDrawable(placeholder, getDimension(R.dimen.artist_image_size).toInt())
    resized.applyColorFilter(color)
    return resized
}

fun Resources.resizeDrawable(drawable: Drawable, size: Int): Drawable {
    val bitmap = (drawable as BitmapDrawable).bitmap
    val bitmapResized = Bitmap.createScaledBitmap(bitmap, size, size, false)
    return BitmapDrawable(this, bitmapResized)
}

private var coverArtHeight: Int = 0
fun Resources.getCoverArtHeight(): Int {
    return if (coverArtHeight == 0) {
        getDimension(R.dimen.top_art_height).toInt()
    } else {
        coverArtHeight
    }
}
