package com.simplemobiletools.musicplayer.models

import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE

data class Folder(val title: String, val trackCount: Int) : Comparable<Folder> {
    companion object {
        var sorting = 0
    }

    override fun compareTo(other: Folder): Int {
        var result = when {
            sorting and PLAYER_SORT_BY_TITLE != 0 -> AlphanumericComparator().compare(title.toLowerCase(), other.title.toLowerCase())
            else -> trackCount.compareTo(other.trackCount)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }

    fun getBubbleText() = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        else -> trackCount.toString()
    }
}
