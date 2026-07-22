package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ButtonConfigDao {
    @Query("SELECT * FROM button_configs ORDER BY position ASC")
    fun getAll(): Flow<List<ButtonConfig>>

    @Query("SELECT * FROM button_configs ORDER BY position ASC")
    suspend fun getAllList(): List<ButtonConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<ButtonConfig>)

    @Update
    suspend fun update(config: ButtonConfig)

    @Query("DELETE FROM button_configs")
    suspend fun clear()
}

@Dao
interface SentSmsLogDao {
    @Query("SELECT * FROM sent_sms_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SentSmsLog>>

    @Query("SELECT * FROM sent_sms_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLog(): SentSmsLog?

    @Query("SELECT * FROM sent_sms_logs WHERE receiverNumber = :number ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLogForNumber(number: String): SentSmsLog?

    @Query("SELECT * FROM sent_sms_logs ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentLogs(): List<SentSmsLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SentSmsLog): Long

    @Update
    suspend fun update(log: SentSmsLog)

    @Query("DELETE FROM sent_sms_logs")
    suspend fun clear()
}

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings")
    fun getAllFlow(): Flow<List<AppSetting>>

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSetting>

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getByKey(key: String): AppSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: AppSetting)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<AppSetting>)

    @Query("DELETE FROM app_settings")
    suspend fun clear()
}

@Dao
interface CommandScheduleDao {
    @Query("SELECT * FROM command_schedules ORDER BY id ASC")
    fun getAllFlow(): Flow<List<CommandSchedule>>

    @Query("SELECT * FROM command_schedules ORDER BY id ASC")
    suspend fun getAll(): List<CommandSchedule>

    @Query("SELECT * FROM command_schedules WHERE isEnabled = 1")
    suspend fun getActiveSchedules(): List<CommandSchedule>

    @Query("SELECT * FROM command_schedules WHERE id = :id")
    suspend fun getById(id: Long): CommandSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: CommandSchedule): Long

    @Update
    suspend fun update(schedule: CommandSchedule)

    @Query("DELETE FROM command_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM command_schedules")
    suspend fun clear()
}

@Database(entities = [ButtonConfig::class, SentSmsLog::class, AppSetting::class, CommandSchedule::class], version = 3, exportSchema = false)
abstract class vLockDatabase : RoomDatabase() {
    abstract fun buttonConfigDao(): ButtonConfigDao
    abstract fun sentSmsLogDao(): SentSmsLogDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun commandScheduleDao(): CommandScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: vLockDatabase? = null

        fun getDatabase(context: Context): vLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    vLockDatabase::class.java,
                    "vlock_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
