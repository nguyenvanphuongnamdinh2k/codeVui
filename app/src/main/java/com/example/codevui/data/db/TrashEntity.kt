package com.example.codevui.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trash_items",
    indices = [
        Index(value = ["deleteTimeEpoch"]),
        Index(value = ["originalPath"])
    ]
)
data class TrashEntity(
    @PrimaryKey
    val id: String,                // same as trashName (unique)
    val originalName: String,
    val trashName: String,
    val originalPath: String,
    val size: Long,
    val deleteTimeEpoch: Long,
    val isDirectory: Boolean,
    val mimeType: String
)
