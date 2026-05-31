package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_logs")
data class MediaLogItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
