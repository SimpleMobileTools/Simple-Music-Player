package com.simplemobiletools.musicplayer.extensions

fun <T> ArrayList<T>.sortSafely(comparator: Comparator<T>) {
    try {
        sortWith(comparator)
    } catch (ignored: Exception) {
    }
}
