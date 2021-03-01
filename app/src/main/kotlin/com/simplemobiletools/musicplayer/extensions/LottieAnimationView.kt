package com.simplemobiletools.musicplayer.extensions

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath

fun LottieAnimationView.updatePlayPauseIcon(isPlaying: Boolean, color: Int) {
    val wasNull = tag == null
    if (tag != isPlaying) {
        speed = if (isPlaying) 2.5f else -2.5f

        if (wasNull) {
            progress = if (isPlaying) 1f else 0f
        } else {
            playAnimation()
        }

        addValueCallback(
            KeyPath("**"),
            LottieProperty.COLOR_FILTER,
            { PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }
        )
        tag = isPlaying
    }
}
