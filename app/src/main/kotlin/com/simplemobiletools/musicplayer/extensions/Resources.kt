package com.simplemobiletools.musicplayer.extensions

import android.content.res.Resources
import android.graphics.*

fun Resources.getColoredIcon(newColor: Int, id: Int): Bitmap {
    val options = BitmapFactory.Options()
    options.inMutable = true
    val bmp = BitmapFactory.decodeResource(this, id, options)
    val paint = Paint()
    val filter = PorterDuffColorFilter(newColor, PorterDuff.Mode.SRC_IN)
    paint.colorFilter = filter
    val canvas = Canvas(bmp)
    canvas.drawBitmap(bmp, 0f, 0f, paint)
    return bmp
}
