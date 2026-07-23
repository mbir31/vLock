package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class vLockRepository(private val database: vLockDatabase) {
    private val buttonConfigDao = database.buttonConfigDao()
    private val sentSmsLogDao = database.sentSmsLogDao()
    private val appSettingDao = database.appSettingDao()
    private val commandScheduleDao = database.commandScheduleDao()

    val allButtonConfigs: Flow<List<ButtonConfig>> = buttonConfigDao.getAll()
    val allLogs: Flow<List<SentSmsLog>> = sentSmsLogDao.getAll()
    val allSettings: Flow<List<AppSetting>> = appSettingDao.getAllFlow()
    val allSchedules: Flow<List<CommandSchedule>> = commandScheduleDao.getAllFlow()

    suspend fun initializeDefaultsIfNecessary() = withContext(Dispatchers.IO) {
        val existingButtons = buttonConfigDao.getAllList()
        if (existingButtons.isEmpty()) {
            populateDefaultButtons()
        }

        val existingSettings = appSettingDao.getAll()
        if (existingSettings.isEmpty()) {
            populateDefaultSettings()
        }
    }

    private suspend fun populateDefaultButtons() {
        val defaultButtons = listOf(
            // Group 1 (Top Grid)
            ButtonConfig("status", "STATUS", "STATUS", "Status", "status", null, true, 1, 0),
            ButtonConfig("location", "LOCATION", "LOCATION", "Find", "location", null, true, 1, 1),
            ButtonConfig("lock", "LOCK", "LOCK", "Lock", "lock", null, true, 1, 2),
            ButtonConfig("unlock", "UNLOCK", "UNLOCK", "Unlock", "unlock", null, true, 1, 3),

            // Group 2 (Bottom Grid)
            ButtonConfig("alarm_on", "ALARM ON", "ALARM ON", "Alarm", "alarm_on", null, true, 2, 4),
            ButtonConfig("alarm_off", "ALARM OFF", "ALARM OFF", "Silent", "alarm_off", null, true, 2, 5),
            ButtonConfig("vs_call", "VS CALL", "VS CALL", "Vclon", "vs_call", null, true, 2, 6),
            ButtonConfig("theft", "THEFT", "THEFT", "OnTheft", "theft", null, true, 2, 7),
            ButtonConfig("low_power", "LOW POWER", "LOW POWER", "OnUlpwr", "low_power", null, true, 2, 8),
            ButtonConfig("two_m_lock", "2M LOCK", "2M LOCK", "2lon", "two_m_lock", null, true, 2, 9),
            ButtonConfig("sensitivity", "SENSITIVITY", "SENSITIVITY", "Vs1", "sensitivity", null, true, 2, 10),
            ButtonConfig("rf_remote", "RF REMOTE", "RF REMOTE", "RFon", "rf_remote", null, true, 2, 11),
            ButtonConfig("apn", "APN", "APN", "APN,\"gpinternet\"", "apn", null, true, 2, 12),
            ButtonConfig("forgot_pin", "FORGOT PIN", "FORGOT PIN", "Forget", "forgot_pin", null, true, 2, 13),
            ButtonConfig("pin_reset", "PIN RESET", "PIN RESET", "Pset,1234,5678", "pin_reset", null, true, 2, 14),
            ButtonConfig("sub_admin", "SUB ADMIN", "SUB ADMIN", "Sbadd1,01700000000", "sub_admin", null, true, 2, 15)
        )
        buttonConfigDao.insertAll(defaultButtons)
    }

    private suspend fun populateDefaultSettings() {
        val defaultSettings = listOf(
            AppSetting("sending_mode", "Background"),
            AppSetting("receiver_number", ""),
            AppSetting("font_family", "Default"),
            AppSetting("text_color", ""),
            AppSetting("button_color", ""),
            AppSetting("button_size", "Medium"),
            AppSetting("header_background_color", ""),
            AppSetting("logo_uri", ""),
            AppSetting("title_text", "vLock SMS Controller"),
            AppSetting("title_bold", "true"),
            AppSetting("theme_mode", "System"),
            AppSetting("accent_color", ""),
            AppSetting("corner_radius", "16"),
            AppSetting("grid_spacing", "12"),
            AppSetting("pin_lock_enabled", "false"),
            AppSetting("pin_lock_code", ""),
            AppSetting("confirm_before_send", "false"),
            AppSetting("vibration_on_send", "true"),
            AppSetting("haptic_feedback", "true"),
            AppSetting("ui_theme_style", "Glassmorphism"),
            AppSetting("auto_simulate_reply", "true")
        )
        appSettingDao.insertAll(defaultSettings)
    }

    suspend fun updateButtonConfig(config: ButtonConfig) = withContext(Dispatchers.IO) {
        buttonConfigDao.update(config)
    }

    suspend fun saveButtonConfigs(configs: List<ButtonConfig>) = withContext(Dispatchers.IO) {
        buttonConfigDao.insertAll(configs)
    }

    suspend fun resetToDefault() = withContext(Dispatchers.IO) {
        buttonConfigDao.clear()
        appSettingDao.clear()
        populateDefaultButtons()
        populateDefaultSettings()
    }

    suspend fun insertLog(log: SentSmsLog): Long = withContext(Dispatchers.IO) {
        sentSmsLogDao.insert(log)
    }

    suspend fun updateLog(log: SentSmsLog) = withContext(Dispatchers.IO) {
        sentSmsLogDao.update(log)
    }

    private fun isSamePhoneNumber(num1: String?, num2: String?): Boolean {
        if (num1.isNullOrBlank() || num2.isNullOrBlank()) return true
        val raw1 = num1.trim()
        val raw2 = num2.trim()
        if (raw1.equals(raw2, ignoreCase = true)) return true

        val clean1 = raw1.filter { it.isDigit() }
        val clean2 = raw2.filter { it.isDigit() }

        if (clean1.isEmpty() || clean2.isEmpty()) {
            return raw1.equals(raw2, ignoreCase = true)
        }

        if (clean1 == clean2) return true
        if (clean1.endsWith(clean2) || clean2.endsWith(clean1)) return true

        val len1 = clean1.length
        val len2 = clean2.length
        if (len1 >= 6 && len2 >= 6) {
            val compareLen = minOf(len1, len2, 10)
            return clean1.takeLast(compareLen) == clean2.takeLast(compareLen)
        }
        return false
    }

    suspend fun recordSmsReply(
        replyMessage: String,
        senderNumber: String? = null,
        smsTimestamp: Long = System.currentTimeMillis()
    ): SentSmsLog? = withContext(Dispatchers.IO) {
        val recentLogs = sentSmsLogDao.getRecentLogs()
        if (recentLogs.isEmpty()) return@withContext null

        // STRICT POLICY:
        // 1. Must match the specific receiver phone number to which the command SMS was sent.
        // 2. Command must have been sent before or around the reply timestamp (allowing normal carrier delay).
        // 3. Command must currently be awaiting a reply (log.replyMessage == null).
        val matchedLog = recentLogs.firstOrNull { log ->
            val numberMatches = senderNumber.isNullOrBlank() || isSamePhoneNumber(log.receiverNumber, senderNumber)
            val sentBeforeReply = log.timestamp <= (smsTimestamp + 120_000L) // Command sent before or around reply
            val isAwaitingReply = log.replyMessage == null

            numberMatches && sentBeforeReply && isAwaitingReply
        }

        if (matchedLog != null) {
            val updatedLog = matchedLog.copy(
                replyMessage = replyMessage,
                replyTimestamp = maxOf(smsTimestamp, System.currentTimeMillis())
            )
            sentSmsLogDao.update(updatedLog)
            updatedLog
        } else {
            null
        }
    }

    suspend fun clearAllLogs() = withContext(Dispatchers.IO) {
        sentSmsLogDao.clear()
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        appSettingDao.insert(AppSetting(key, value))
    }

    suspend fun saveSettings(settings: Map<String, String>) = withContext(Dispatchers.IO) {
        val entities = settings.map { AppSetting(it.key, it.value) }
        appSettingDao.insertAll(entities)
    }

    suspend fun getSettingValue(key: String): String? = withContext(Dispatchers.IO) {
        appSettingDao.getByKey(key)?.value
    }

    suspend fun getSettingsMap(): Map<String, String> = withContext(Dispatchers.IO) {
        appSettingDao.getAll().associate { it.key to it.value }
    }

    suspend fun getAllButtonsList(): List<ButtonConfig> = withContext(Dispatchers.IO) {
        buttonConfigDao.getAllList()
    }

    suspend fun insertSchedule(schedule: CommandSchedule): Long = withContext(Dispatchers.IO) {
        commandScheduleDao.insert(schedule)
    }

    suspend fun updateSchedule(schedule: CommandSchedule) = withContext(Dispatchers.IO) {
        commandScheduleDao.update(schedule)
    }

    suspend fun deleteSchedule(id: Long) = withContext(Dispatchers.IO) {
        commandScheduleDao.deleteById(id)
    }

    suspend fun getAllSchedules(): List<CommandSchedule> = withContext(Dispatchers.IO) {
        commandScheduleDao.getAll()
    }

    suspend fun getActiveSchedules(): List<CommandSchedule> = withContext(Dispatchers.IO) {
        commandScheduleDao.getActiveSchedules()
    }
}
