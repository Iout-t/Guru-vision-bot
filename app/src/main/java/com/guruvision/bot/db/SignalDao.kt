
package com.guruvision.bot.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Insert suspend fun insert(record: SignalRecord)
    @Query("SELECT * FROM signals ORDER BY timestamp DESC LIMIT 200")
    fun recent(): Flow<List<SignalRecord>>
}
