package com.example.codevui.ui.unusedapps

import android.graphics.drawable.Drawable

/**
 * Thông tin 1 ứng dụng không dùng
 */
data class UnusedAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val sizeBytes: Long,
    val lastUsedTimestamp: Long   // millis, 0 nếu không biết
)

/**
 * Tab filter
 */
enum class UnusedAppsTab(val label: String) {
    ALL("Tất cả"),
    ALLOW_ARCHIVE("Cho phép lưu trữ")
}

/**
 * Sort type
 */
enum class UnusedAppsSortBy(val label: String) {
    SIZE("Kích thước"),
    NAME("Tên"),
    LAST_USED("Gần đây nhất")
}

data class UnusedAppsUiState(
    val apps: List<UnusedAppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalApps: Int = 0,
    val totalSize: Long = 0L,
    val tab: UnusedAppsTab = UnusedAppsTab.ALL,
    val sortBy: UnusedAppsSortBy = UnusedAppsSortBy.SIZE,
    val sortAscending: Boolean = false
)
