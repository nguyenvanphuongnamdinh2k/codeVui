package com.example.codevui.ui.storage

import com.example.codevui.model.RecommendCard
import com.example.codevui.model.StorageInfo

data class StorageManagerUiState(
    val storageInfo: StorageInfo? = null,
    val usedPercent: Int = 0,
    val usedFormatted: String = "",
    val totalFormatted: String = "",

    // Chi tiết từng loại (hiển thị trong storage bar)
    val videoBytes: Long = 0L,
    val imageBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val archiveBytes: Long = 0L,
    val apkBytes: Long = 0L,
    val docBytes: Long = 0L,
    val appsBytes: Long = 0L,       // Ứng dụng (APK + data + cache)
    val systemBytes: Long = 0L,    // Hệ thống (residual)
    val otherBytes: Long = 0L,     // File khác (residual)

    // Đề xuất (recommend cards — MyFiles style)
    val recommendCards: List<RecommendCard> = emptyList(),

    // Legacy đề xuất
    val trashBytes: Long = 0L,
    val unusedAppsBytes: Long = 0L,
    val duplicateBytes: Long = 0L,
    val largeFilesBytes: Long = 0L,
    val oldScreenshotsBytes: Long = 0L,

    val isLoading: Boolean = false,
    val isLoadingRecommend: Boolean = false,
    val errorMessage: String? = null
) {
    /** Tổng dung lượng có thể giải phóng từ các đề xuất */
    val totalCleanableBytes: Long
        get() = unusedAppsBytes + duplicateBytes + largeFilesBytes + oldScreenshotsBytes + trashBytes

    /** Tổng các loại file chính (video + image + audio + archive + apk + doc + apps) */
    val totalCategorizedBytes: Long
        get() = videoBytes + imageBytes + audioBytes + archiveBytes + apkBytes + docBytes + appsBytes

    /** Tổng user-visible categories (dùng để tính residual "other") */
    val totalUserVisibleBytes: Long
        get() = videoBytes + imageBytes + audioBytes + archiveBytes + apkBytes + docBytes + appsBytes + otherBytes

    /** Tổng tất cả bytes trong MediaStore categories (không tính apps/system) */
    val totalMediaStoreBytes: Long
        get() = videoBytes + imageBytes + audioBytes + archiveBytes + apkBytes + docBytes
}
