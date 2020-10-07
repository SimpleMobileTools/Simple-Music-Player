package com.simplemobiletools.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.models.QueueItem

@Dao
interface QueueItemsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(queueItems: List<QueueItem>)

    @Query("SELECT * FROM queue_items")
    fun getQueueItems(): List<QueueItem>

    @Query("DELETE FROM queue_items")
    fun deleteAllItems()
}
