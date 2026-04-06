package com.example.codevui.ui.recent

import com.example.codevui.model.RecentFile

data class RecentFilesUiState(
    val files: List<RecentFile> = emptyList(),
    val allFiles: List<RecentFile> = emptyList(), // unfiltered
    val tabs: List<String> = listOf("Tất cả"),
    val selectedTab: Int = 0,
    val isLoading: Boolean = false
)

