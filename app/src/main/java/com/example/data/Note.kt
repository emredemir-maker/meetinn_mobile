package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val isAudio: Boolean = false,
    val audioPath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val meetingId: String? = null,
    val meetingTitle: String? = null
)
