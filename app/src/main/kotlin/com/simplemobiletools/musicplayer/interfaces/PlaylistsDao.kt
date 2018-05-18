package com.simplemobiletools.musicplayer.interfaces

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import com.simplemobiletools.musicplayer.models.Playlist

@Dao
interface PlaylistsDao {
    @Insert
    fun insert(playlist: Playlist)
}
