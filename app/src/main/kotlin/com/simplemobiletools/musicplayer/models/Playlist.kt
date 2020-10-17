package com.simplemobiletools.musicplayer.models

import androidx.room.*

@Entity(tableName = "playlists", indices = [(Index(value = ["id"], unique = true))])
data class Playlist(
    @PrimaryKey(autoGenerate = true) var id: Int,
    @ColumnInfo(name = "title") var title: String,

    @Ignore var trackCnt: Int = 0
) {
    constructor() : this(0, "", 0)
}
