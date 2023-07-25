package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplemobiletools.commons.helpers.AlphanumericComparator
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE

@Entity("genres", indices = [(Index(value = ["id"], unique = true))])
data class Genre(
    @PrimaryKey(autoGenerate = true) var id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "track_cnt") var trackCnt: Int,
) : Comparable<Genre> {
    companion object {
        var sorting = 0
    }

    override fun compareTo(other: Genre): Int {
        var result = when {
            sorting and PLAYER_SORT_BY_TITLE != 0 -> AlphanumericComparator().compare(title.lowercase(), other.title.lowercase())
            else -> trackCnt.compareTo(other.trackCnt)
        }

        if (sorting and SORT_DESCENDING != 0) {
            result *= -1
        }

        return result
    }

    fun getBubbleText() = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        else -> trackCnt.toString()
    }
}
