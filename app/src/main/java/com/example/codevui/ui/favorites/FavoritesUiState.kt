package com.example.codevui.ui.favorites

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
    val deletedCount: Int = 0
)
