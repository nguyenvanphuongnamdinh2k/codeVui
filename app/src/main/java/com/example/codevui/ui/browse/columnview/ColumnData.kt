package com.example.codevui.ui.browse.columnview

import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile

/**
 * ColumnData — 1 column trong Column View (Miller Columns)
 * Mỗi column đại diện cho nội dung 1 folder
 */
data class ColumnData(
    val path: String,
    val name: String,
    val folders: List<FolderItem> = emptyList(),
    val files: List<RecentFile> = emptyList(),
    val selectedItemPath: String? = null  // folder đang được highlight (đã click mở column tiếp)
)