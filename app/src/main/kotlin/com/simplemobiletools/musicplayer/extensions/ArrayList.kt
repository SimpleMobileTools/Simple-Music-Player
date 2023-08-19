package com.simplemobiletools.musicplayer.extensions

fun <T> ArrayList<T>.sortSafely(comparator: Comparator<T>) {
    try {
        sortWith(comparator)
    } catch (ignored: Exception) {
    }
}

fun <T> ArrayList<T>.swap(index1: Int, index2: Int) {
    this[index1] = this[index2].also {
        this[index2] = this[index1]
    }
}
