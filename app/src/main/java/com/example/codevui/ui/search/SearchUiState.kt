package com.example.codevui.ui.search

import com.example.codevui.model.FileType
import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile

data class SearchUiState(
    val query: String = "",
    val files: List<RecentFile> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val isFilterExpanded: Boolean = true,
    val selectedTimeFilter: TimeFilter? = null,
    val selectedTypes: Set<FileType> = emptySet(),
    val searchInContent: Boolean = false
) {
    val totalResults: Int get() = files.size + folders.size
}

enum class TimeFilter(val label: String, val daysAgo: Int) {
    YESTERDAY("Hôm qua", 1),
    WEEK("7 ngày qua", 7),
    MONTH("30 ngày qua", 30)
}
