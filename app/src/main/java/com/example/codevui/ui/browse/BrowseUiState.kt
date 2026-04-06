package com.example.codevui.ui.browse

import com.example.codevui.data.FileRepository
import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.browse.columnview.ColumnData

data class BrowseUiState(
    val currentPath: String = "",
    val pathSegments: List<String> = emptyList(),
    val pathStack: List<String> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val files: List<RecentFile> = emptyList(),
    val sortBy: FileRepository.SortBy = FileRepository.SortBy.NAME,
    val ascending: Boolean = true,
    val isLoading: Boolean = false,
    // Column View state (landscape)
    val columns: List<ColumnData> = emptyList(),
    // Filter: true = "Cần thiết" (essential folders only), false = "Tất cả"
    val showEssentialOnly: Boolean = false
)
