package com.example.codevui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Format dung lượng lưu trữ theo đơn vị thập phân (1 KB = 1000 bytes),
 * giống cách MyFiles / nhà sản xuất quảng cáo (256 GB, 512 GB, 1 TB...).
 *
 * Tự động chọn đơn vị nhỏ nhất phù hợp (B / KB / MB / GB / TB) để tránh
 * hiển thị "0 GB" cho item chỉ vài MB. Với dung lượng tổng của ổ đĩa,
 * giá trị tròn (VD: 256_000_000_000) vẫn hiển thị "256 GB" như mong đợi.
 *
 * KHÔNG dùng 1024 ở đây — nếu dùng cơ số nhị phân, một thiết bị 256 GB
 * sẽ bị hiển thị thành ~238 GB và lệch với correctionStorageSize().
 */
fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val locale = Locale.getDefault()
    return when {
        bytes < 1_000L -> "$bytes B"
        bytes < 1_000_000L ->
            String.format(locale, "%.1f KB", bytes / 1_000.0)
        bytes < 1_000_000_000L ->
            String.format(locale, "%.1f MB", bytes / 1_000_000.0)
        bytes < 1_000_000_000_000L -> {
            val gb = bytes / 1_000_000_000.0
            // Số tròn (VD: 256.0) → bỏ phần thập phân để hiển thị "256 GB"
            if (gb == gb.toLong().toDouble()) String.format(locale, "%d GB", gb.toLong())
            else String.format(locale, "%.1f GB", gb)
        }
        else -> String.format(locale, "%.2f TB", bytes / 1_000_000_000_000.0)
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
