package com.simplemobiletools.musicplayer.inlines

inline fun <T> Collection<T>.indexOfFirstOrNull(predicate: (T) -> Boolean): Int? {
    for ((index, item) in this.withIndex()) {
        if (predicate(item))
            return index
    }
    return null
}
