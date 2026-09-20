
package com.guruvision.bot.db
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signals")
data class SignalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val symbol: String,
    val signal: String,
    val confidence: Double,
    val price: Double?,
    val candlePattern: String,
    val reason: String
)
