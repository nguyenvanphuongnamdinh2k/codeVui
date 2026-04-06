package com.example.codevui.ui.home

import com.example.codevui.model.CategoryItem
import com.example.codevui.model.RecentFile
import com.example.codevui.model.StorageItem

data class HomeUiState(
    val categories: List<CategoryItem> = emptyList(),
    val recentFiles: List<RecentFile> = emptyList(),
    val storageItems: List<StorageItem> = emptyList(),
    val utilityItems: List<StorageItem> = emptyList(),
    val newFileCount: Int = 0,
    val isLoading: Boolean = false,
    val isPermissionDenied: Boolean = false
)
