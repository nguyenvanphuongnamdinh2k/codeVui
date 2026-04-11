package com.example.codevui.ui.duplicates

import com.example.codevui.model.DuplicateGroup

/**
 * groups dùng List<DuplicateGroup>.
 *
 * Để LazyColumn chỉ recompose item thay đổi (không toàn bộ list):
 * - Key ổn định: key = { group -> group.hash }
 * - Group equality: data class equals() so sánh content → Compose skip nếu giống
 * - Diff trong ViewModel: giữ nguyên object reference cho groups không đổi
 */
data class DuplicatesUiState(
    val groups: List<DuplicateGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanProgress: String = "",
    val error: String? = null,
    val totalWasted: Long = 0L,
    val totalGroups: Int = 0
)
