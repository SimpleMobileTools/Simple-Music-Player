package com.simplemobiletools.musicplayer.extensions

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.musicplayer.R

fun Resources.getSmallPlaceholder(color: Int): Drawable {
    val placeholder = getColoredDrawableWithColor(R.drawable.ic_headset_padded, color)
    return resizeDrawable(placeholder, getDimension(R.dimen.song_image_size).toInt())
}

fun Resources.getBiggerPlaceholder(color: Int): Drawable {
    val placeholder = getColoredDrawableWithColor(R.drawable.ic_headset, color)
    return resizeDrawable(placeholder, getDimension(R.dimen.artist_image_size).toInt())
}

fun Resources.resizeDrawable(drawable: Drawable, size: Int): Drawable {
    val bitmap = (drawable as BitmapDrawable).bitmap
    val bitmapResized = Bitmap.createScaledBitmap(bitmap, size, size, false)
    return BitmapDrawable(this, bitmapResized)
}
