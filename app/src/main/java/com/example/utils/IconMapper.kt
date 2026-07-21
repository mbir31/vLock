package com.example.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object IconMapper {
    val icons = mapOf(
        "status" to Icons.Default.Info,
        "location" to Icons.Default.MyLocation,
        "lock" to Icons.Default.Lock,
        "unlock" to Icons.Default.LockOpen,
        "alarm_on" to Icons.Default.NotificationsActive,
        "alarm_off" to Icons.Default.NotificationsOff,
        "vs_call" to Icons.Default.Call,
        "theft" to Icons.Default.Security,
        "low_power" to Icons.Default.BatterySaver,
        "two_m_lock" to Icons.Default.Timer,
        "sensitivity" to Icons.Default.Tune,
        "rf_remote" to Icons.Default.SettingsRemote,
        "apn" to Icons.Default.SettingsInputAntenna,
        "forgot_pin" to Icons.Default.Password,
        "pin_reset" to Icons.Default.VpnKey,
        "sub_admin" to Icons.Default.SupervisorAccount,
        "home" to Icons.Default.Home,
        "star" to Icons.Default.Star,
        "favorite" to Icons.Default.Favorite,
        "warning" to Icons.Default.Warning,
        "power" to Icons.Default.Power,
        "wifi" to Icons.Default.Wifi,
        "sms" to Icons.Default.Sms,
        "shield" to Icons.Default.Shield,
        "history" to Icons.Default.History,
        "settings" to Icons.Default.Settings
    )

    fun getIcon(name: String): ImageVector {
        return icons[name] ?: Icons.Default.Info
    }
}
