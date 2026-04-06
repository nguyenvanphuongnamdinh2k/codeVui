package com.example.codevui.ui.largefiles

import com.example.codevui.model.RecentFile

/**
 * Size threshold presets cho filter
 */
enum class SizeThreshold(val bytes: Long, val label: String) {
    MB_25(25L * 1024 * 1024, "25 MB"),
    MB_100(100L * 1024 * 1024, "100 MB"),
    MB_500(500L * 1024 * 1024, "500 MB"),
    CUSTOM(0L, "Kích thước tùy chỉnh")
}

/**
 * File type filter
 */
enum class LargeFileTypeFilter(val label: String) {
    ALL("Tất cả"),
    IMAGE("Ảnh"),
    VIDEO("Video"),
    OTHER("File khác")
}

/**
 * Nhóm file theo khoảng kích thước
 */
data class SizeGroup(
    val label: String,        // "Hơn 400 MB", "Hơn 100 MB", ...
    val minBytes: Long,       // threshold tối thiểu
    val files: List<RecentFile>
)

data class LargeFilesUiState(
    val groups: List<SizeGroup> = emptyList(),
    val allFiles: List<RecentFile> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalFiles: Int = 0,
    val totalSize: Long = 0L,
    val sizeThreshold: SizeThreshold = SizeThreshold.MB_25,
    val customThresholdBytes: Long = 25L * 1024 * 1024,
    val typeFilter: LargeFileTypeFilter = LargeFileTypeFilter.ALL
)
