package com.example.codevui.ui.archive

import android.app.Application
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.ArchiveReader
import com.example.codevui.data.FileOperations
import com.example.codevui.data.FileRepository
import com.example.codevui.data.MediaStoreScanner
import com.example.codevui.model.ArchiveEntry
import com.example.codevui.ui.common.viewmodel.OperationResultManager
import com.example.codevui.ui.selection.SelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ArchiveViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    val selection = SelectionState(savedStateHandle)

    private var allEntries: List<ArchiveEntry> = emptyList()
    private var archivePath: String = ""

    // Lưu actual archive path cho mỗi segment (giống BrowseViewModel)
    // pathStack[0] = "" (root), pathStack[1] = "folder1/", pathStack[2] = "folder1/sub/"
    private val pathStack = mutableListOf<String>()

    // Operation result manager (shared component)
    val resultManager = OperationResultManager()
    val extractResult = resultManager.operationResult // For backward compatibility

    fun clearExtractResult() {
        resultManager.clearResult()
    }

    fun onSortChanged(sortBy: FileRepository.SortBy) {
        _uiState.update {
            val (folders, files) = sortEntries(it.folders, it.files, sortBy, it.ascending)
            it.copy(sortBy = sortBy, folders = folders, files = files)
        }
    }

    fun toggleSortDirection() {
        _uiState.update {
            val ascending = !it.ascending
            val (folders, files) = sortEntries(it.folders, it.files, it.sortBy, ascending)
            it.copy(ascending = ascending, folders = folders, files = files)
        }
    }

    private fun sortEntries(
        folders: List<ArchiveEntry>,
        files: List<ArchiveEntry>,
        sortBy: FileRepository.SortBy,
        ascending: Boolean
    ): Pair<List<ArchiveEntry>, List<ArchiveEntry>> {
        val sortedFolders = when (sortBy) {
            FileRepository.SortBy.NAME -> if (ascending) folders.sortedBy { it.name.lowercase() } else folders.sortedByDescending { it.name.lowercase() }
            FileRepository.SortBy.DATE -> if (ascending) folders.sortedBy { it.lastModified } else folders.sortedByDescending { it.lastModified }
            FileRepository.SortBy.SIZE -> if (ascending) folders.sortedBy { it.size } else folders.sortedByDescending { it.size }
        }
        val sortedFiles = when (sortBy) {
            FileRepository.SortBy.NAME -> if (ascending) files.sortedBy { it.name.lowercase() } else files.sortedByDescending { it.name.lowercase() }
            FileRepository.SortBy.DATE -> if (ascending) files.sortedBy { it.lastModified } else files.sortedByDescending { it.lastModified }
            FileRepository.SortBy.SIZE -> if (ascending) files.sortedBy { it.size } else files.sortedByDescending { it.size }
        }
        return sortedFolders to sortedFiles
    }

    // Password callback for opening archive
    var onPasswordRequired: ((archivePath: String) -> Unit)? = null

    fun openArchive(
        filePath: String,
        fileName: String,
        fullPath: String = filePath,
        password: String? = null,
        isPreviewMode: Boolean = false
    ) {
        archivePath = filePath
        val parentPath = java.io.File(fullPath).parent ?: ""
        _uiState.update {
            it.copy(
                isLoading = true,
                isReady = false,  // Not ready until async load completes
                archivePath = filePath,
                archiveName = fileName,
                archiveParentPath = parentPath,
                isPreviewMode = isPreviewMode,
                // Reset current path and content when reopening the same archive
                currentPath = "",
                pathSegments = emptyList(),
                folders = emptyList(),
                files = emptyList()
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = ArchiveReader.readEntries(filePath, password)
                allEntries = entries

                Log.d("ArchiveVM", "Read ${entries.size} entries from $fileName")
                entries.take(20).forEach { Log.d("ArchiveVM", "  ${it.path} (dir=${it.isDirectory})") }

                pathStack.clear()
                pathStack.add("") // root

                val (folders, files) = ArchiveReader.listLevel(entries, "")

                // Build pathSegments from full file path
                val pathSegments = buildPathSegments(fullPath, fileName)
                val archiveFileIndex = pathSegments.lastIndex // Archive file là segment cuối cùng

                _uiState.update {
                    it.copy(
                        currentPath = "",
                        pathSegments = pathSegments,
                        archiveFileIndex = archiveFileIndex,
                        folders = folders,
                        files = files,
                        totalEntries = entries.count { e -> !e.isDirectory },
                        isLoading = false,
                        isReady = true,  // Signal that async load is complete
                        error = if (entries.isEmpty()) "Không thể đọc file nén hoặc file trống" else null
                    )
                }
            } catch (e: ArchiveReader.PasswordRequiredException) {
                // Notify UI to show password dialog
                _uiState.update { it.copy(isLoading = false) }
                withContext(Dispatchers.Main) {
                    onPasswordRequired?.invoke(filePath)
                }
            } catch (e: ArchiveReader.InvalidPasswordException) {
                // Show error and ask for password again
                _uiState.update { it.copy(isLoading = false, error = "Sai mật khẩu") }
                withContext(Dispatchers.Main) {
                    onPasswordRequired?.invoke(filePath)
                }
            } catch (e: Exception) {
                Log.e("ArchiveVM", "Failed to open archive", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Không thể đọc file nén: ${e.message}")
                }
            }
        }
    }

    /**
     * Build path segments từ full file path
     * Example: /storage/emulated/0/Download/test.zip
     * → ["Bộ nhớ trong", "Download", "test.zip"]
     */
    private fun buildPathSegments(fullPath: String, fileName: String): List<String> {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val file = File(fullPath)
        val segments = mutableListOf<String>()

        // Build từ parent folders
        var current = file.parentFile
        while (current != null && current.absolutePath != rootPath) {
            segments.add(0, current.name)
            current = current.parentFile
        }

        // Add root
        segments.add(0, "Bộ nhớ trong")

        // Add file name
        segments.add(fileName)

        return segments
    }

    fun openFolder(folderPath: String, folderName: String) {
        pathStack.add(folderPath)

        val (folders, files) = ArchiveReader.listLevel(allEntries, folderPath)
        Log.d("ArchiveVM", "openFolder: '$folderPath' → ${folders.size} folders, ${files.size} files")

        _uiState.update {
            it.copy(
                currentPath = folderPath,
                pathSegments = it.pathSegments + folderName,
                folders = folders,
                files = files
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun navigateToSegment(index: Int) {
        if (index < 0 || index >= pathStack.size) return

        val path = pathStack[index]
        // Trim stack
        while (pathStack.size > index + 1) {
            pathStack.removeLast()
        }

        val (folders, files) = ArchiveReader.listLevel(allEntries, path)

        _uiState.update {
            // Calculate correct pathSegments index
            // pathStack index 0 = root (archiveFileIndex in pathSegments)
            // pathStack index 1 = first folder inside archive (archiveFileIndex + 1 in pathSegments)
            val pathSegmentsEndIndex = it.archiveFileIndex + index + 1
            val newPathSegments = if (pathSegmentsEndIndex <= it.pathSegments.size) {
                it.pathSegments.subList(0, pathSegmentsEndIndex)
            } else {
                it.pathSegments
            }

            it.copy(
                currentPath = path,
                pathSegments = newPathSegments,
                folders = folders,
                files = files,
                isReady = true
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun goBack(): Boolean {
        if (pathStack.size <= 1) return false
        navigateToSegment(pathStack.size - 2)
        return true
    }

    // Password for extraction (stored if archive was opened with password)
    private var archivePassword: String? = null

    /**
     * Get current archive password for thumbnail extraction
     */
    fun getPassword(): String? = archivePassword

    /**
     * Giải nén các entries đã chọn vào destination folder
     * Giữ nguyên cấu trúc folder như trong archive
     */
    fun extractSelected(destPath: String) {
        val selectedIds = selection.selectedIds.toList()
        if (selectedIds.isEmpty()) return
        val isPreview = _uiState.value.isPreviewMode

        Log.d("van.phuong.ArchiveVM", "extractSelected: selectedIds count = ${selectedIds.size}")
        selectedIds.take(10).forEach { Log.d("van.phuong.ArchiveVM", "  Selected: $it") }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Set loading state
                _uiState.update {
                    it.copy(
                        isOperating = true,
                        operationType = "Giải nén",
                        operationProgress = 0,
                        operationTotal = selectedIds.size
                    )
                }

                // Convert selected IDs to entry paths
                val selectedPaths = selectedIds.mapNotNull { id ->
                    when {
                        id.startsWith("dir:") -> id.removePrefix("dir:")
                        id.startsWith("file:") -> id.removePrefix("file:")
                        else -> null
                    }
                }

                Log.d("van.phuong.ArchiveVM", "extractSelected: selectedPaths count = ${selectedPaths.size}")
                selectedPaths.take(10).forEach { Log.d("van.phuong.ArchiveVM", "  Path: $it") }

                // Extract entries with password if available
                val (success, failed) = ArchiveReader.extractEntries(
                    archivePath = archivePath,
                    entryPaths = selectedPaths,
                    destPath = destPath,
                    allEntries = allEntries,
                    password = archivePassword
                )

                Log.d("van.phuong.ArchiveVM", "extractSelected: success=$success, failed=$failed")

                // Clear loading state
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        operationType = "",
                        operationProgress = 0,
                        operationTotal = 0
                    )
                }

                // Set result using shared component
                resultManager.setResult(destPath, success, failed, "Giải nén")

                // Scan MediaStore để các app khác nhận biết file mới được giải nén
                if (success > 0) {
                    MediaStoreScanner.scanNewFile(getApplication(), parentPath = destPath, newFilePath = null)
                }

                // Don't exit selection mode in preview mode
                if (!isPreview) {
                    selection.exit()
                }
            } catch (e: Exception) {
                Log.e("van.phuong.ArchiveVM", "Extract failed", e)

                // Clear loading state
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        operationType = "",
                        operationProgress = 0,
                        operationTotal = 0
                    )
                }

                // Set error result using shared component
                resultManager.setResult(destPath, 0, selectedIds.size, "Giải nén")
            }
        }
    }

    /**
     * Set password để sử dụng cho extraction
     */
    fun setPassword(password: String?) {
        archivePassword = password
    }

    // ─────────────────────────────────────────────────────────────────────
    // Preview Mode Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Extract selected items tại parent folder của archive (cho preview mode)
     * @param folderName tên folder đích
     */
    fun extractAtParent(folderName: String) {
        val parentPath = _uiState.value.archiveParentPath
        if (parentPath.isEmpty()) {
            Log.e("ArchiveVM", "Parent path is empty")
            return
        }

        val destPath = File(parentPath, folderName).absolutePath
        extractSelected(destPath)
    }

    /**
     * Move selected items từ archive sang folder khác
     * Extract rồi delete khỏi archive
     */
    fun moveSelected(destPath: String) {
        val selectedIds = selection.selectedIds.toList()
        if (selectedIds.isEmpty()) return
        val isPreview = _uiState.value.isPreviewMode

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Set loading state
                _uiState.update {
                    it.copy(
                        isOperating = true,
                        operationType = "Di chuyển",
                        operationProgress = 0,
                        operationTotal = selectedIds.size
                    )
                }

                // Extract to destination first
                val selectedPaths = selectedIds.mapNotNull { id ->
                    when {
                        id.startsWith("dir:") -> id.removePrefix("dir:")
                        id.startsWith("file:") -> id.removePrefix("file:")
                        else -> null
                    }
                }

                val (extractSuccess, extractFailed) = ArchiveReader.extractEntries(
                    archivePath = archivePath,
                    entryPaths = selectedPaths,
                    destPath = destPath,
                    allEntries = allEntries,
                    password = archivePassword
                )

                // Remove from archive after successful extraction
                if (extractSuccess > 0) {
                    val (removeSuccess, removeFailed) = ArchiveReader.removeEntries(
                        archivePath = archivePath,
                        entryPaths = selectedPaths,
                        allEntries = allEntries,
                        password = archivePassword
                    )

                    Log.d("ArchiveVM", "Move: extracted $extractSuccess, removed $removeSuccess from archive")

                    // Reload archive to reflect changes
                    if (removeSuccess > 0) {
                        val entries = ArchiveReader.readEntries(archivePath, archivePassword)
                        allEntries = entries
                        val (folders, files) = ArchiveReader.listLevel(entries, _uiState.value.currentPath)
                        _uiState.update {
                            it.copy(
                                folders = folders,
                                files = files,
                                totalEntries = entries.count { e -> !e.isDirectory }
                            )
                        }
                    }
                }

                // Clear loading state
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        operationType = "",
                        operationProgress = 0,
                        operationTotal = 0
                    )
                }

                resultManager.setResult(destPath, extractSuccess, extractFailed, "Di chuyển")

                // Scan MediaStore để các app khác nhận biết file mới được extract
                if (extractSuccess > 0) {
                    MediaStoreScanner.scanNewFile(getApplication(), parentPath = destPath, newFilePath = null)
                }

                // Don't exit selection mode in preview mode
                if (!isPreview) {
                    selection.exit()
                }
            } catch (e: Exception) {
                Log.e("ArchiveVM", "Move failed", e)

                // Clear loading state
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        operationType = "",
                        operationProgress = 0,
                        operationTotal = 0
                    )
                }

                resultManager.setResult(destPath, 0, selectedIds.size, "Di chuyển")
            }
        }
    }

    /**
     * Delete selected items from archive
     */
    fun deleteSelected() {
        val selectedIds = selection.selectedIds.toList()
        if (selectedIds.isEmpty()) return
        val isPreview = _uiState.value.isPreviewMode

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Set loading state
                _uiState.update {
                    it.copy(
                        isOperating = true,
                        operationType = "Xóa",
                        operationProgress = 0,
                        operationTotal = selectedIds.size
                    )
                }

                val selectedPaths = selectedIds.mapNotNull { id ->
                    when {
                        id.startsWith("dir:") -> id.removePrefix("dir:")
                        id.startsWith("file:") -> id.removePrefix("file:")
                        else -> null
                    }
                }

                val (success, failed) = ArchiveReader.removeEntries(
                    archivePath = archivePath,
                    entryPaths = selectedPaths,
                    allEntries = allEntries,
                    password = archivePassword
                )

                Log.d("ArchiveVM", "Delete: removed $success from archive")

                // Reload archive to reflect changes
                if (success > 0) {
                    val entries = ArchiveReader.readEntries(archivePath, archivePassword)
                    allEntries = entries
                    val (folders, files) = ArchiveReader.listLevel(entries, _uiState.value.currentPath)
                    _uiState.update {
                        it.copy(
                            folders = folders,
                            files = files,
                            totalEntries = entries.count { e -> !e.isDirectory }
                        )
                    }
                }

                // Clear loading state
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        operationType = "",
                        operationProgress = 0,
                        operationTotal = 0
                    )
                }

                resultManager.setResult("", success, failed, "Xóa")

                // Don't exit selection mode in preview mode
                if (!isPreview) {
                    selection.exit()
                }
            } catch (e: Exception) {
                Log.e("ArchiveVM", "Delete failed", e)

                // Clear loading state
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        operationType = "",
                        operationProgress = 0,
                        operationTotal = 0
                    )
                }

                resultManager.setResult("", 0, selectedIds.size, "Xóa")
            }
        }
    }

    /**
     * Get all child entry IDs for a folder path
     * Dùng để auto-select children khi select folder
     */
    fun getChildrenIds(folderPath: String): List<String> {
        val prefix = if (folderPath.endsWith("/")) folderPath else "$folderPath/"

        return allEntries
            .filter { it.path.startsWith(prefix) && it.path != prefix }
            .map { entry ->
                if (entry.isDirectory) "dir:${entry.path}"
                else "file:${entry.path}"
            }
    }

    /**
     * Get the parent folder entry ID for a given entry path
     * Returns null if the entry is at the archive root (currentPath == "")
     */
    fun getParentId(entryPath: String): String? {
        val currentPath = _uiState.value.currentPath
        // Nếu đang ở root, không có parent segment
        if (currentPath.isEmpty()) return null
        return "dir:$currentPath"
    }

    /**
     * Get direct children IDs (1 level deep, only descendants that are direct children)
     */
    fun getDirectChildrenIds(folderPath: String): List<String> {
        val prefix = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
        val depth = prefix.count { it == '/' }

        return allEntries
            .filter { entry ->
                val entryPrefix = if (entry.path.endsWith("/")) entry.path.dropLast(1) else entry.path
                val entryDepth = entryPrefix.count { it == '/' }
                entry.path.startsWith(prefix) && entry.path != prefix && entryDepth == depth + 1
            }
            .map { entry ->
                if (entry.isDirectory) "dir:${entry.path}"
                else "file:${entry.path}"
            }
    }

    /**
     * Get only the visible direct children IDs (from current page's folders + files)
     * Used for cascade toggle that respects the current navigation level
     */
    fun getVisibleChildrenIds(folderPath: String): List<String> {
        val prefix = if (folderPath.endsWith("/")) folderPath else "$folderPath/"
        val depth = prefix.count { it == '/' }

        val visibleIds = mutableListOf<String>()

        // Add visible folder children
        for (folder in _uiState.value.folders) {
            val folderPrefix = if (folder.path.endsWith("/")) folder.path.dropLast(1) else folder.path
            if (folder.path.startsWith(prefix) && folder.path != prefix && folderPrefix.count { it == '/' } == depth + 1) {
                visibleIds.add("dir:${folder.path}")
            }
        }

        // Add visible file children
        for (file in _uiState.value.files) {
            val filePrefix = if (file.path.endsWith("/")) file.path.dropLast(1) else file.path
            if (file.path.startsWith(prefix) && filePrefix.count { it == '/' } == depth + 1) {
                visibleIds.add("file:${file.path}")
            }
        }

        return visibleIds
    }

    /**
     * Check if a folder is effectively selected (either direct selection OR all visible children selected)
     * Used by auto-selection LaunchedEffect to determine if children should be auto-selected
     * when navigating into a folder
     */
    fun isParentEffectivelySelected(folderPath: String): Boolean {
        val folderId = "dir:$folderPath"
        val directSelected = selection.isSelected(folderId)
        Log.d("van.phuong.ArchiveVM", "isParentEffectivelySelected('$folderPath')")
        Log.d("van.phuong.ArchiveVM", "  folderId = '$folderId', directSelected = $directSelected")
        if (directSelected) {
            Log.d("van.phuong.ArchiveVM", "  → return TRUE (direct selection)")
            return true
        }

        val visibleChildren = getVisibleChildrenIds(folderPath)
        Log.d("van.phuong.ArchiveVM", "  visibleChildren = $visibleChildren (count=${visibleChildren.size})")
        if (visibleChildren.isEmpty()) {
            Log.d("van.phuong.ArchiveVM", "  → return FALSE (no children)")
            return false
        }

        val selectedChildren = visibleChildren.filter { selection.isSelected(it) }
        val allChildrenSelected = selectedChildren.size == visibleChildren.size
        Log.d("van.phuong.ArchiveVM", "  selectedChildren = $selectedChildren (count=${selectedChildren.size})")
        Log.d("van.phuong.ArchiveVM", "  allChildrenSelected = $allChildrenSelected")
        Log.d("van.phuong.ArchiveVM", "  → return $allChildrenSelected")
        return allChildrenSelected
    }

    /**
     * Get tri-state for folder checkbox display based on visible descendants
     */
    fun getFolderTriState(folderPath: String): com.example.codevui.ui.selection.TriState {
        val visibleChildren = getVisibleChildrenIds(folderPath)
        if (visibleChildren.isEmpty()) {
            return if (selection.isSelected("dir:$folderPath")) {
                com.example.codevui.ui.selection.TriState.ALL
            } else {
                com.example.codevui.ui.selection.TriState.NONE
            }
        }
        val selectedCount = visibleChildren.count { selection.isSelected(it) }
        return when {
            selectedCount == visibleChildren.size -> com.example.codevui.ui.selection.TriState.ALL
            selectedCount == 0 -> com.example.codevui.ui.selection.TriState.NONE
            else -> com.example.codevui.ui.selection.TriState.PARTIAL
        }
    }
}
