package com.github.okwrtdsh.idobatter.room

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query


@Dao
interface MessageDao {

    @Query("SELECT * from message_table ORDER BY created ASC")
    fun getAll(): LiveData<List<Message>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: Message)

    @Query("DELETE FROM message_table")
    suspend fun deleteAll()

    @Query("""
        SELECT *, 1 AS priority FROM message_table
            WHERE is_auther = 1
                AND ((limit_time == 0) OR :current_time < limit_time * 60 * 60 * 10000 + created)
                AND ((limit_hops == 0) OR hops + 1 < limit_hops)
                AND ((limit_dist == 0 ) OR (
                        6371 * ACOS(
                            COS(RADIANS(:current_lat)) * COS(RADIANS(lat))
                            * COS(RADIANS(lng) - RADIANS(:current_lng))
                            + SIN(RADIANS(:current_lat)) * SIN(RADIANS(lat))
                        )
                    ) < limit_dist)
        UNION
        SELECT *, 2 AS priority FROM message_table
            WHERE is_fab = 1
                AND ((limit_time == 0) OR :current_time < limit_time * 60 * 60 * 10000 + created)
                AND ((limit_hops == 0) OR hops + 1 < limit_hops)
                AND ((limit_dist == 0 ) OR (
                        6371 * ACOS(
                            COS(RADIANS(:current_lat)) * COS(RADIANS(lat))
                            * COS(RADIANS(lng) - RADIANS(:current_lng))
                            + SIN(RADIANS(:current_lat)) * SIN(RADIANS(lat))
                        )
                    ) < limit_dist)
        UNION
        SELECT *, 3 AS priority FROM message_table
            WHERE is_auther = 0 AND is_fab = 0
                AND ((limit_time == 0) OR :current_time < limit_time * 60 * 60 * 10000 + created)
                AND ((limit_hops == 0) OR hops + 1 < limit_hops)
                AND ((limit_dist == 0 ) OR (
                        6371 * ACOS(
                            COS(RADIANS(:current_lat)) * COS(RADIANS(lat))
                            * COS(RADIANS(lng) - RADIANS(:current_lng))
                            + SIN(RADIANS(:current_lat)) * SIN(RADIANS(lat))
                        )
                    ) < limit_dist)
        ORDER BY priority, created DESC
        LIMIT :limit
        """)
    fun enabled(current_time: Long, current_lat: Double, current_lng: Double, limit: Int): List<Message>
}