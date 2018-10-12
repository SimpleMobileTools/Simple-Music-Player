package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
        @PrimaryKey(autoGenerate = true) var id: Int,
        @ColumnInfo(name = "title") var title: String
)
