package com.example.codevui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatStorageSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    return if (gb >= 1024) {
        String.format(Locale.getDefault(), "%.2f TB", gb / 1024)
    } else {
        String.format(Locale.getDefault(), "%.1f GB", gb)
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

fun formatDateFull(epochSeconds: Long): String {
    val sdf = SimpleDateFormat("dd 'Th'M, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(epochSeconds * 1000))
}
