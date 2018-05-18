package com.simplemobiletools.musicplayer.models

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
        @PrimaryKey(autoGenerate = true) var id: Int,
        @ColumnInfo(name = "title") var title: String
)
