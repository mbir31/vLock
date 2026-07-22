package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_schedules")
data class CommandSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val buttonId: String,          // ID of target action button (e.g. "status", "lock", etc.)
    val timeFormatted: String,     // e.g. "08:30 AM" or "18:00"
    val hour: Int,                 // 0-23
    val minute: Int,               // 0-59
    val daysOfWeek: String,        // Comma separated e.g. "Mon,Tue,Wed,Thu,Fri" or "Everyday"
    val isEnabled: Boolean = true,
    val note: String = ""          // Optional label/note
)
