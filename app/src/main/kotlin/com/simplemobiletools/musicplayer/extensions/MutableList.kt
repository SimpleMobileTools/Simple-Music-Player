package com.simplemobiletools.musicplayer.extensions

fun <T> MutableList<T>.sortSafely(comparator: Comparator<T>) {
    try {
        sortWith(comparator)
    } catch (ignored: Exception) {
    }
}

fun <T> MutableList<T>.swap(index1: Int, index2: Int) {
    this[index1] = this[index2].also {
        this[index2] = this[index1]
    }
}

fun <T> MutableList<T>.move(currentIndex: Int, newIndex: Int) {
    val itemToMove = removeAt(currentIndex)
    if (currentIndex > newIndex) {
        add(newIndex, itemToMove)
    } else {
        add(newIndex - 1, itemToMove)
    }
}
