package com.example.codevui.ui.filelist

import com.example.codevui.data.FileRepository
import com.example.codevui.model.FileType
import com.example.codevui.model.RecentFile
import com.example.codevui.util.formatFileSize

data class FileListUiState(
    val title: String = "",
    val fileType: FileType = FileType.OTHER,
    val files: List<RecentFile> = emptyList(),
    val totalSize: String = "",
    val totalSizeBytes: Long = 0L,
    val sortBy: FileRepository.SortBy = FileRepository.SortBy.DATE,
    val ascending: Boolean = false,
    val isLoading: Boolean = false
) {
    /** Header title: "1203 ảnh (3,85 GB)" — tính 1 lần khi khởi tạo object */
    val headerTitle: String = "${files.size} ${title.lowercase()} (${formatFileSize(totalSizeBytes)})"
}
