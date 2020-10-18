package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "queue_items", indices = [(Index(value = ["id"], unique = true))])
data class QueueItem(
    @PrimaryKey(autoGenerate = true) var id: Int,
    @ColumnInfo(name = "track_id") var trackId: Long,
    @ColumnInfo(name = "playlist_id") var playlistId: Int,
    @ColumnInfo(name = "track_order") var trackOrder: Int,
    @ColumnInfo(name = "is_playing") var isPlaying: Boolean,
    @ColumnInfo(name = "last_position") var lastPosition: Int,
    @ColumnInfo(name = "was_played") var wasPlayed: Boolean
)
