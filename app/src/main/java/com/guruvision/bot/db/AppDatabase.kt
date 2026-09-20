
package com.guruvision.bot.db
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities=[SignalRecord::class], version=1, exportSchema=false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signalDao(): SignalDao
}
