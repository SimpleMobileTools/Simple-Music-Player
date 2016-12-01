package com.simplemobiletools.musicplayer.extensions

import java.util.*

fun Int.getTimeString(): String {
    val sb = StringBuilder(8)
    val hours = this / (60 * 60)
    val minutes = this % (60 * 60) / 60
    val seconds = this % (60 * 60) % 60

    if (this > 3600) {
        sb.append(String.format(Locale.getDefault(), "%02d", hours)).append(":")
    }

    sb.append(String.format(Locale.getDefault(), "%02d", minutes))
    sb.append(":").append(String.format(Locale.getDefault(), "%02d", seconds))

    return sb.toString()
}
