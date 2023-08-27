package com.simplemobiletools.musicplayer.extensions

import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.lazySmoothScroll(scrollToPosition: Int) {
    if (scrollToPosition > 100) {
        post {
            scrollToPosition(scrollToPosition - 25)
            smoothScrollToPosition(scrollToPosition)
        }
    } else {
        smoothScrollToPosition(scrollToPosition)
    }
}
