package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ButtonConfig
import com.example.data.SentSmsLog
import com.example.data.vLockDatabase
import com.example.data.vLockRepository
import com.example.utils.BackupRestoreUtils
import com.example.utils.SmsReplyHandler
import com.example.utils.SmsSender
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
    val autoSimulateReply: Boolean = true
)

class vLockViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: vLockRepository

    val buttonConfigs: StateFlow<List<ButtonConfig>>
    val logs: StateFlow<List<SentSmsLog>>
    val settingsState: StateFlow<SettingsState>

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

        settingsState = repository.allSettings
            .map { list ->
                val map = list.associate { it.key to it.value }
                SettingsState(
                    sendingMode = map["sending_mode"] ?: "Background",
                    receiverNumber = map["receiver_number"] ?: "",
                    fontFamily = map["font_family"] ?: "Default",
                    textColor = map["text_color"] ?: "",
                    buttonColor = map["button_color"] ?: "",
                    buttonSize = map["button_size"] ?: "Medium",
                    headerBackgroundColor = map["header_background_color"] ?: "",
                    logoUri = map["logo_uri"] ?: "",
                    titleText = map["title_text"] ?: "vLock SMS Controller",
                    titleBold = map["title_bold"]?.toBoolean() ?: true,
                    themeMode = map["theme_mode"] ?: "System",
                    accentColor = map["accent_color"] ?: "",
                    cornerRadius = map["corner_radius"]?.toIntOrNull() ?: 16,
                    gridSpacing = map["grid_spacing"]?.toIntOrNull() ?: 12,
                    pinLockEnabled = map["pin_lock_enabled"]?.toBoolean() ?: false,
                    pinLockCode = map["pin_lock_code"] ?: "",
                    confirmBeforeSend = map["confirm_before_send"]?.toBoolean() ?: false,
                    vibrationOnSend = map["vibration_on_send"]?.toBoolean() ?: true,
                    hapticFeedback = map["haptic_feedback"]?.toBoolean() ?: true,
                    toastSuccessFail = map["toast_success_fail"]?.toBoolean() ?: true,
                    uiThemeStyle = map["ui_theme_style"] ?: "Default",
                    autoSimulateReply = map["auto_simulate_reply"]?.toBoolean() ?: true
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SettingsState()
            )

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

    fun triggerReplyReceived(senderNumber: String, replyText: String) {
        viewModelScope.launch {
            val updatedLog = repository.recordSmsReply(replyText, senderNumber)
            if (updatedLog != null) {
                _activeReplyPopup.value = ReplySmsData(
                    logId = updatedLog.id,
                    buttonName = updatedLog.buttonName,
                    smsCode = updatedLog.smsCode,
                    receiverNumber = updatedLog.receiverNumber,
                    replyMessage = replyText,
                    replyTimestamp = updatedLog.replyTimestamp ?: System.currentTimeMillis()
                )
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
        viewModelScope.launch {
            repository.saveSetting(key, value)
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
                    val updatedLog = repository.recordSmsReply(replyText, settings.receiverNumber)
                    if (updatedLog != null) {
                        _activeReplyPopup.value = ReplySmsData(
                            logId = updatedLog.id,
                            buttonName = updatedLog.buttonName,
                            smsCode = updatedLog.smsCode,
                            receiverNumber = updatedLog.receiverNumber,
                            replyMessage = replyText,
                            replyTimestamp = updatedLog.replyTimestamp ?: System.currentTimeMillis()
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
