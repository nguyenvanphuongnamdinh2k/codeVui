package com.example.codevui.ui.duplicates

import com.example.codevui.model.DuplicateGroup

data class DuplicatesUiState(
    val groups: List<DuplicateGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanProgress: String = "",
    val error: String? = null,
    val totalWasted: Long = 0L,
    val totalGroups: Int = 0
)