package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "queue_items", primaryKeys = ["track_id"])
data class QueueItem(
    @ColumnInfo(name = "track_id") var trackId: Long,
    @ColumnInfo(name = "track_order") var trackOrder: Int,
    @ColumnInfo(name = "is_current") var isCurrent: Boolean,
    @ColumnInfo(name = "last_position") var lastPosition: Int
)
