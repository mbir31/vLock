package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ButtonConfig
import com.example.data.CommandSchedule
import com.example.data.SentSmsLog
import com.example.data.vLockDatabase
import com.example.data.vLockRepository
import com.example.utils.BackupRestoreUtils
import com.example.utils.ScheduleManager
import com.example.utils.SmsReplyHandler
import com.example.utils.SmsSender
import com.example.utils.vLockWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReplySmsData(
    val logId: Long,
    val buttonName: String,
    val smsCode: String,
    val receiverNumber: String,
    val replyMessage: String,
    val replyTimestamp: Long = System.currentTimeMillis()
)

data class SettingsState(
    val sendingMode: String = "Background",
    val receiverNumber: String = "",
    val fontFamily: String = "Default",
    val textColor: String = "",
    val buttonColor: String = "",
    val buttonSize: String = "Medium",
    val headerBackgroundColor: String = "",
    val logoUri: String = "",
    val titleText: String = "vLock SMS Controller",
    val titleBold: Boolean = true,
    val themeMode: String = "System",
    val accentColor: String = "",
    val cornerRadius: Int = 16,
    val gridSpacing: Int = 12,
    val pinLockEnabled: Boolean = false,
    val pinLockCode: String = "",
    val confirmBeforeSend: Boolean = false,
    val vibrationOnSend: Boolean = true,
    val hapticFeedback: Boolean = true,
    val toastSuccessFail: Boolean = true,
    val uiThemeStyle: String = "Glassmorphism",
    val autoSimulateReply: Boolean = true,
    val appIconStyle: String = "Default",
    val showHeaderCard: Boolean = false,
    val widgetButtonIds: String = ""
)

class vLockViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: vLockRepository

    val buttonConfigs: StateFlow<List<ButtonConfig>>
    val logs: StateFlow<List<SentSmsLog>>
    val schedules: StateFlow<List<CommandSchedule>>
    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _activeReplyPopup = MutableStateFlow<ReplySmsData?>(null)
    val activeReplyPopup: StateFlow<ReplySmsData?> = _activeReplyPopup.asStateFlow()

    init {
        val database = vLockDatabase.getDatabase(application)
        repository = vLockRepository(database)

        SmsReplyHandler.registerViewModel(this)

        buttonConfigs = repository.allButtonConfigs
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        logs = repository.allLogs
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        schedules = repository.allSchedules
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        viewModelScope.launch {
            repository.allSettings.collect { list ->
                if (list.isNotEmpty()) {
                    val map = list.associate { it.key to it.value }
                    val current = _settingsState.value
                    _settingsState.value = SettingsState(
                        sendingMode = map["sending_mode"] ?: current.sendingMode,
                        receiverNumber = map["receiver_number"] ?: current.receiverNumber,
                        fontFamily = map["font_family"] ?: current.fontFamily,
                        textColor = map["text_color"] ?: current.textColor,
                        buttonColor = map["button_color"] ?: current.buttonColor,
                        buttonSize = map["button_size"] ?: current.buttonSize,
                        headerBackgroundColor = map["header_background_color"] ?: current.headerBackgroundColor,
                        logoUri = map["logo_uri"] ?: current.logoUri,
                        titleText = map["title_text"] ?: current.titleText,
                        titleBold = map["title_bold"]?.toBoolean() ?: current.titleBold,
                        themeMode = map["theme_mode"] ?: current.themeMode,
                        accentColor = map["accent_color"] ?: current.accentColor,
                        cornerRadius = map["corner_radius"]?.toIntOrNull() ?: current.cornerRadius,
                        gridSpacing = map["grid_spacing"]?.toIntOrNull() ?: current.gridSpacing,
                        pinLockEnabled = map["pin_lock_enabled"]?.toBoolean() ?: current.pinLockEnabled,
                        pinLockCode = map["pin_lock_code"] ?: current.pinLockCode,
                        confirmBeforeSend = map["confirm_before_send"]?.toBoolean() ?: current.confirmBeforeSend,
                        vibrationOnSend = map["vibration_on_send"]?.toBoolean() ?: current.vibrationOnSend,
                        hapticFeedback = map["haptic_feedback"]?.toBoolean() ?: current.hapticFeedback,
                        toastSuccessFail = map["toast_success_fail"]?.toBoolean() ?: current.toastSuccessFail,
                        uiThemeStyle = map["ui_theme_style"] ?: current.uiThemeStyle,
                        autoSimulateReply = map["auto_simulate_reply"]?.toBoolean() ?: current.autoSimulateReply,
                        appIconStyle = map["app_icon_style"] ?: current.appIconStyle,
                        showHeaderCard = map["show_header_card"]?.toBoolean() ?: current.showHeaderCard,
                        widgetButtonIds = map["widget_button_ids"] ?: current.widgetButtonIds
                    )
                }
            }
        }

        // Initialize defaults if they don't exist
        viewModelScope.launch {
            repository.initializeDefaultsIfNecessary()
        }
    }

    override fun onCleared() {
        super.onCleared()
        SmsReplyHandler.unregisterViewModel()
    }

    fun dismissReplyPopup() {
        _activeReplyPopup.value = null
    }

    fun showReplyPopup(log: SentSmsLog) {
        _activeReplyPopup.value = ReplySmsData(
            logId = log.id,
            buttonName = log.buttonName,
            smsCode = log.smsCode,
            receiverNumber = log.receiverNumber,
            replyMessage = log.replyMessage ?: "",
            replyTimestamp = log.replyTimestamp ?: System.currentTimeMillis()
        )
    }

    fun triggerReplyReceived(senderNumber: String, replyText: String, smsTimestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val updatedLog = repository.recordSmsReply(replyText, senderNumber, smsTimestamp)
            if (updatedLog != null) {
                showReplyPopup(updatedLog)
            }
        }
    }

    private fun generateSimulatedReply(buttonName: String, smsCode: String): String {
        val codeUpper = smsCode.uppercase()
        val nameUpper = buttonName.uppercase()
        return when {
            codeUpper.contains("STATUS") || nameUpper.contains("STATUS") ->
                "ACK: STATUS -> System Armed & Active. Door: Locked. Battery: 94%. GSM Signal: Excellent (28°C)"
            codeUpper.contains("LOCATION") || nameUpper.contains("LOCATION") || nameUpper.contains("FIND") ->
                "ACK: LOCATION -> Lat: 23.8103, Lon: 90.4125, GPS Lock: Active. Map: https://maps.google.com/?q=23.8103,90.4125"
            codeUpper.contains("LOCK") || nameUpper.contains("LOCK") ->
                "ACK: LOCK -> System Armed & Door Lock Secured Successfully."
            codeUpper.contains("UNLOCK") || nameUpper.contains("UNLOCK") ->
                "ACK: UNLOCK -> System Disarmed & Door Unlocked."
            codeUpper.contains("ALARM") || nameUpper.contains("ALARM") ->
                "ACK: ALARM -> Security Alarm Alert Toggled OK."
            codeUpper.contains("THEFT") || nameUpper.contains("THEFT") ->
                "ACK: THEFT -> Motion Sensor Anti-Theft Guard ENABLED."
            codeUpper.contains("VS") || nameUpper.contains("SENSITIVITY") ->
                "ACK: SENSITIVITY -> Vibration Sensor Sensitivity updated."
            codeUpper.contains("POWER") ->
                "ACK: POWER -> Ultra Low Power Mode Activated."
            else ->
                "ACK: [$smsCode] Command Received & Executed OK by GSM Controller."
        }
    }

    fun updateSetting(key: String, value: String) {
        val current = _settingsState.value
        val updated = when (key) {
            "receiver_number" -> current.copy(receiverNumber = value)
            "title_text" -> current.copy(titleText = value)
            "pin_lock_code" -> current.copy(pinLockCode = value)
            "sending_mode" -> current.copy(sendingMode = value)
            "font_family" -> current.copy(fontFamily = value)
            "text_color" -> current.copy(textColor = value)
            "button_color" -> current.copy(buttonColor = value)
            "button_size" -> current.copy(buttonSize = value)
            "header_background_color" -> current.copy(headerBackgroundColor = value)
            "logo_uri" -> current.copy(logoUri = value)
            "title_bold" -> current.copy(titleBold = value.toBoolean())
            "theme_mode" -> current.copy(themeMode = value)
            "accent_color" -> current.copy(accentColor = value)
            "corner_radius" -> current.copy(cornerRadius = value.toIntOrNull() ?: current.cornerRadius)
            "grid_spacing" -> current.copy(gridSpacing = value.toIntOrNull() ?: current.gridSpacing)
            "pin_lock_enabled" -> current.copy(pinLockEnabled = value.toBoolean())
            "confirm_before_send" -> current.copy(confirmBeforeSend = value.toBoolean())
            "vibration_on_send" -> current.copy(vibrationOnSend = value.toBoolean())
            "haptic_feedback" -> current.copy(hapticFeedback = value.toBoolean())
            "toast_success_fail" -> current.copy(toastSuccessFail = value.toBoolean())
            "ui_theme_style" -> current.copy(uiThemeStyle = value)
            "auto_simulate_reply" -> current.copy(autoSimulateReply = value.toBoolean())
            "app_icon_style" -> current.copy(appIconStyle = value)
            "show_header_card" -> current.copy(showHeaderCard = value.toBoolean())
            "widget_button_ids" -> current.copy(widgetButtonIds = value)
            else -> current
        }
        _settingsState.value = updated

        viewModelScope.launch {
            repository.saveSetting(key, value)
            if (key == "widget_button_ids") {
                vLockWidgetProvider.updateAllWidgets(getApplication())
            }
        }
    }

    fun saveSchedule(schedule: CommandSchedule) {
        viewModelScope.launch {
            if (schedule.id == 0L) {
                val newId = repository.insertSchedule(schedule)
                val savedSchedule = schedule.copy(id = newId)
                ScheduleManager.scheduleAlarm(getApplication(), savedSchedule)
            } else {
                repository.updateSchedule(schedule)
                ScheduleManager.scheduleAlarm(getApplication(), schedule)
            }
        }
    }

    fun toggleSchedule(schedule: CommandSchedule) {
        viewModelScope.launch {
            val updated = schedule.copy(isEnabled = !schedule.isEnabled)
            repository.updateSchedule(updated)
            if (updated.isEnabled) {
                ScheduleManager.scheduleAlarm(getApplication(), updated)
            } else {
                ScheduleManager.cancelAlarm(getApplication(), updated.id)
            }
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
            ScheduleManager.cancelAlarm(getApplication(), id)
        }
    }

    fun changeLauncherIcon(context: Context, styleKey: String) {
        updateSetting("app_icon_style", styleKey)
        val pm = context.packageManager
        val pkg = context.packageName
        val iconMap = mapOf(
            "Default" to "$pkg.MainActivity",
            "Blue" to "$pkg.MainActivityBlue",
            "Cyber" to "$pkg.MainActivityCyber",
            "Gold" to "$pkg.MainActivityGold",
            "Dark" to "$pkg.MainActivityDark"
        )

        iconMap.forEach { (key, component) ->
            val newState = if (key == styleKey) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, component),
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateButtonConfig(config: ButtonConfig) {
        viewModelScope.launch {
            repository.updateButtonConfig(config)
        }
    }

    fun swapButtonPositions(btn1: ButtonConfig, btn2: ButtonConfig) {
        viewModelScope.launch {
            val updatedBtn1 = btn1.copy(position = btn2.position)
            val updatedBtn2 = btn2.copy(position = btn1.position)
            repository.updateButtonConfig(updatedBtn1)
            repository.updateButtonConfig(updatedBtn2)
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            repository.resetToDefault()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAllLogs()
        }
    }

    fun sendCommand(context: Context, button: ButtonConfig, customCode: String? = null) {
        viewModelScope.launch {
            val settings = settingsState.value
            val codeToSend = customCode ?: button.smsCode
            val logId = SmsSender.sendSms(
                context = context,
                repository = repository,
                buttonName = button.name,
                smsCode = codeToSend,
                receiverNumber = settings.receiverNumber,
                sendingMode = settings.sendingMode,
                showToast = settings.toastSuccessFail,
                vibrate = settings.vibrationOnSend
            )

            if (settings.autoSimulateReply) {
                launch {
                    delay(2000)
                    val replyText = generateSimulatedReply(button.name, codeToSend)
                    val simTime = System.currentTimeMillis()
                    val updatedLog = repository.recordSmsReply(replyText, settings.receiverNumber, simTime)
                    if (updatedLog != null) {
                        _activeReplyPopup.value = ReplySmsData(
                            logId = updatedLog.id,
                            buttonName = updatedLog.buttonName,
                            smsCode = updatedLog.smsCode,
                            receiverNumber = updatedLog.receiverNumber,
                            replyMessage = replyText,
                            replyTimestamp = updatedLog.replyTimestamp ?: simTime
                        )
                    }
                }
            }
        }
    }

    suspend fun exportConfigJson(): String {
        val settings = repository.getSettingsMap()
        val buttons = repository.getAllButtonsList()
        return BackupRestoreUtils.exportConfig(settings, buttons)
    }

    fun importConfigJson(jsonString: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val (settings, buttons) = BackupRestoreUtils.importConfig(jsonString)
                if (buttons.isNotEmpty()) {
                    repository.saveSettings(settings)
                    repository.saveButtonConfigs(buttons)
                    onSuccess()
                } else {
                    onFailure("Invalid configuration JSON: no button configurations found.")
                }
            } catch (e: Exception) {
                onFailure("Error parsing backup JSON: ${e.localizedMessage}")
            }
        }
    }
}
