package com.example.codevui.ui.archive

import com.example.codevui.data.FileRepository
import com.example.codevui.model.ArchiveEntry

data class ArchiveUiState(
    val sortBy: FileRepository.SortBy = FileRepository.SortBy.NAME,
    val ascending: Boolean = true,
    val archiveName: String = "",
    val archivePath: String = "",
    val archiveParentPath: String = "",     // parent folder của archive file (để extract về đúng folder)
    val currentPath: String = "",           // path bên trong archive
    val pathSegments: List<String> = emptyList(),  // breadcrumb segments
    val archiveFileIndex: Int = 0,          // index của archive file trong pathSegments (để disable click vào parent folders)
    val folders: List<ArchiveEntry> = emptyList(),
    val files: List<ArchiveEntry> = emptyList(),
    val totalEntries: Int = 0,
    val isLoading: Boolean = false,
    val isReady: Boolean = false,            // true khi dữ liệu đã load xong, dùng để trigger LaunchedEffect
    val error: String? = null,
    val isPreviewMode: Boolean = false,     // Preview mode - auto selection mode
    // Operation progress
    val isOperating: Boolean = false,       // Extract/Move/Delete đang chạy
    val operationType: String = "",         // "Giải nén", "Di chuyển", "Xóa"
    val operationProgress: Int = 0,         // Số items đã xử lý
    val operationTotal: Int = 0             // Tổng số items cần xử lý
)
