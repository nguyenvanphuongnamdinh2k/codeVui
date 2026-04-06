package com.example.codevui.ui.recommend

import com.example.codevui.model.RecommendCard
import com.example.codevui.model.RecommendFile
import com.example.codevui.model.RecommendType

/**
 * UiState cho màn hình Recommend Files
 */
data class RecommendUiState(
    val isLoading: Boolean = true,
    val cards: List<RecommendCard> = emptyList(),
    val selectedCardType: RecommendType? = null,
    val files: List<RecommendFile> = emptyList(),
    val selectedFilePaths: Set<String> = emptySet(),
    val isLoadingFiles: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val deleteSuccessCount: Int = 0,
    val deleteErrorCount: Int = 0
)

/**
 * Kiểu thao tác xóa đang thực hiện
 */
sealed class DeleteMode {
    data object Idle : DeleteMode()
    data object Deleting : DeleteMode()
    data class Done(val success: Int, val failed: Int) : DeleteMode()
}
