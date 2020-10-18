package com.simplemobiletools.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.models.QueueItem

@Dao
interface QueueItemsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(queueItems: List<QueueItem>)

    @Query("SELECT * FROM queue_items")
    fun getAll(): List<QueueItem>

    @Query("UPDATE queue_items SET is_current = 0")
    fun removeIsCurrent()

    @Query("UPDATE queue_items SET is_current = 1 WHERE track_id = :trackId AND playlist_id = :playlistId")
    fun setIsCurrent(trackId: Long, playlistId: Int)

    @Query("DELETE FROM queue_items")
    fun deleteAllItems()
}
