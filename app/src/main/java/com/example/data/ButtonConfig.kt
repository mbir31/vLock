package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "button_configs")
data class ButtonConfig(
    @PrimaryKey val id: String,
    val originalName: String,
    val name: String,
    val smsCode: String,
    val iconName: String,
    val colorHex: String? = null,
    val isEnabled: Boolean = true,
    val groupId: Int, // 1 for Group 1 (Top 4), 2 for Group 2 (Bottom 12)
    val position: Int
)
