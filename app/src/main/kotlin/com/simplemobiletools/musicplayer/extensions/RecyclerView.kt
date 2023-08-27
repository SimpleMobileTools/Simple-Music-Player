package com.simplemobiletools.musicplayer.extensions

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.lazySmoothScroll(scrollToPosition: Int) {
    val layoutManager = layoutManager
    if (layoutManager is LinearLayoutManager) {
        if (scrollToPosition in layoutManager.findFirstCompletelyVisibleItemPosition()..layoutManager.findLastCompletelyVisibleItemPosition()) {
            return
        }
    }

    if (scrollToPosition > 100) {
        post {
            scrollToPosition(scrollToPosition - 25)
            smoothScrollToPosition(scrollToPosition)
        }
    } else {
        smoothScrollToPosition(scrollToPosition)
    }
}
