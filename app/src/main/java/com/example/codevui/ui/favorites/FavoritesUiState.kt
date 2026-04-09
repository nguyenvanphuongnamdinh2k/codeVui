package com.example.codevui.ui.favorites

import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.model.FavoriteItem

/**
 * UiState cho màn hình Favorites
 */
data class FavoritesUiState(
    val favorites: List<FavoriteItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val deletedCount: Int = 0,
    // Operation state (for copy/move/compress progress)
    val isOperationRunning: Boolean = false,
    val operationState: ProgressState? = null,
    val operationTitle: String = "",
    val operationCurrent: Int = 0,
    val operationTotal: Int = 0,
    val operationPercent: Int = 0
) {
    fun clearOperation(): FavoritesUiState = copy(
        isOperationRunning = false,
        operationState = null,
        operationTitle = "",
        operationCurrent = 0,
        operationTotal = 0,
        operationPercent = 0
    )
}
