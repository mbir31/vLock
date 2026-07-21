package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sent_sms_logs")
data class SentSmsLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val buttonName: String,
    val smsCode: String,
    val receiverNumber: String,
    val sendingMode: String, // "Background" or "Default App"
    val status: String, // "SUCCESS", "FAILURE", "OPENED"
    val errorMessage: String? = null
)
