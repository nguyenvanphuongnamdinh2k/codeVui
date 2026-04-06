package com.example.codevui.ui.browse

import android.app.Application
import android.os.Environment
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.ArchiveReader
import com.example.codevui.data.FileRepository
import com.example.codevui.data.MediaStoreScanner
import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.browse.columnview.ColumnData
import com.example.codevui.ui.clipboard.ClipboardManager
import com.example.codevui.ui.common.BaseMediaStoreViewModel
import com.example.codevui.ui.common.Navigable
import com.example.codevui.ui.common.Reloadable
import com.example.codevui.ui.common.Sortable
import com.example.codevui.ui.common.viewmodel.OperationResultManager
import com.example.codevui.ui.selection.FileActionState
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val log = Logger("BrowseVM")

class BrowseViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : BaseMediaStoreViewModel(application), Sortable, Navigable, Reloadable {

    private val repository = FileRepository(application)
    val selection = SelectionState(savedStateHandle)
    val fileAction = FileActionState(savedStateHandle)
    val clipboard = ClipboardManager(savedStateHandle)

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    // Operation result manager (shared component)
    val resultManager = OperationResultManager()
    val operationResult = resultManager.operationResult // For backward compatibility

    private val rootPath = Environment.getExternalStorageDirectory().absolutePath

    // Cache directory listings để tránh query lại nhiều lần
    // Key: "path|sortBy|ascending"
    private val directoryCache = LruCache<String, Pair<List<FolderItem>, List<RecentFile>>>(50)

    // Loading flag để tránh race condition khi nhiều loadDirectory gọi đè lên nhau
    // Khi một coroutine đang load, các coroutine khác sẽ chờ và bỏ qua
    private val isLoadingLock = java.util.concurrent.atomic.AtomicBoolean(false)
    // Lưu params của loadDirectory đang chạy để coroutine pending biết phải chờ đúng path
    private var pendingLoadParams: Triple<String, List<String>, List<String>>? = null

    // Store destination path for snackbar navigation after paste with conflict
    private var pasteDestinationPath: String? = null

    override fun onOperationDone(success: Int, failed: Int, actionName: String) {
        directoryCache.evictAll() // Clear cache khi có thao tác file

        // Use paste destination if set, otherwise use lastOperationDestPath
        val finalDestPath = pasteDestinationPath ?: lastOperationDestPath
        log.d("=== onOperationDone ===")
        log.d("Final destination (for snackbar): $finalDestPath")

        // Clear paste destination
        pasteDestinationPath = null

        // Set snackbar state với thông tin kết quả (using shared component)
        finalDestPath?.let { destPath ->
            resultManager.setResult(destPath, success, failed, actionName)
        }

        reload()
    }

    fun clearOperationResult() {
        resultManager.clearResult()
    }

    fun openRoot() { navigateTo(rootPath, "Bộ nhớ trong", isRoot = true) }

    fun openFolder(folderPath: String, folderName: String) {
        val segments = _uiState.value.pathSegments.toMutableList().also { it.add(folderName) }
        val stack = _uiState.value.pathStack.toMutableList().also { it.add(folderPath) }
        loadDirectory(folderPath, segments, stack)
    }

    /**
     * Navigate đến folder path cụ thể (từ notification deep link)
     */
    fun navigateToPath(targetPath: String) {
        val folder = java.io.File(targetPath)
        if (!folder.exists() || !folder.isDirectory) {
            openRoot() // Fallback nếu path không tồn tại
            return
        }

        // Build path từ root đến target
        val segments = mutableListOf<String>()
        val stack = mutableListOf<String>()

        var current = folder
        while (current.absolutePath != rootPath && current.parent != null) {
            segments.add(0, current.name)
            stack.add(0, current.absolutePath)
            current = current.parentFile ?: break
        }

        // Add root
        segments.add(0, "Bộ nhớ trong")
        stack.add(0, rootPath)

        loadDirectory(targetPath, segments, stack)
    }

    fun onColumnFolderClick(columnIndex: Int, folder: FolderItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val (subFolders, subFiles) = repository.listDirectory(folder.path, _uiState.value.sortBy, _uiState.value.ascending)
            _uiState.update { state ->
                val updatedColumns = state.columns.take(columnIndex + 1)
                    .mapIndexed { i, col -> if (i == columnIndex) col.copy(selectedItemPath = folder.path) else col }
                    .toMutableList()
                    .also { it.add(ColumnData(folder.path, folder.name, subFolders, subFiles)) }
                state.copy(
                    columns = updatedColumns, currentPath = folder.path,
                    pathSegments = updatedColumns.map { it.name },
                    pathStack = updatedColumns.map { it.path },
                    folders = subFolders, files = subFiles
                )
            }
        }
    }

    override fun navigateToSegment(index: Int) {
        val stack = _uiState.value.pathStack
        if (index < 0 || index >= stack.size) return
        loadDirectory(stack[index], _uiState.value.pathSegments.subList(0, index + 1).toList(), stack.subList(0, index + 1).toList())
    }

    override fun goBack(): Boolean {
        val stack = _uiState.value.pathStack
        if (stack.size <= 1) return false
        val newStack = stack.dropLast(1)
        loadDirectory(newStack.last(), _uiState.value.pathSegments.dropLast(1), newStack)
        return true
    }

    override fun onSortChanged(sortBy: FileRepository.SortBy) {
        val newAscending = if (_uiState.value.sortBy == sortBy) !_uiState.value.ascending else true
        _uiState.update { it.copy(sortBy = sortBy, ascending = newAscending) }
        directoryCache.evictAll() // Clear cache khi đổi sort
        reload()
    }

    override fun toggleSortDirection() {
        _uiState.update { it.copy(ascending = !it.ascending) }
        directoryCache.evictAll() // Clear cache khi đổi sort direction
        reload()
    }

    /** Whether current path is root (Bộ nhớ trong) */
    fun isAtRoot(): Boolean = _uiState.value.pathStack.size <= 1

    /**
     * Toggle filter: essential folders only vs all folders
     */
    fun onFilterChanged(showEssentialOnly: Boolean) {
        _uiState.update { it.copy(showEssentialOnly = showEssentialOnly) }
        directoryCache.evictAll()
        reload()
    }

    override fun reload() {
        directoryCache.evictAll() // Clear cache khi reload
        val s = _uiState.value
        loadDirectory(s.currentPath, s.pathSegments, s.pathStack)
    }

    private fun navigateTo(path: String, name: String, isRoot: Boolean) {
        val segments = if (isRoot) listOf(name) else _uiState.value.pathSegments + name
        val stack = if (isRoot) listOf(path) else _uiState.value.pathStack + path
        loadDirectory(path, segments, stack)
    }

    private fun loadDirectory(path: String, segments: List<String>, stack: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            log.d("loadDirectory: path='$path', segments=$segments")

            // ── Mutex: tránh race condition khi nhiều loadDirectory gọi đè lên nhau ──
            // Nếu đang có coroutine khác load, pending coroutine sẽ đợi rồi kiểm tra xem
            // path hiện tại có bị thay đổi không. Nếu path cùng → skip (dùng kết quả từ
            // coroutine trước đã cache). Nếu path khác → chạy lại.
            while (true) {
                val acquired = isLoadingLock.compareAndSet(false, true)
                if (acquired) break

                // Đợi coroutine kia xong rồi kiểm tra path
                pendingLoadParams = Triple(path, segments, stack)
                delay(50)
                pendingLoadParams = null

                // Sau khi coroutine trước xong, nếu path hiện tại đã bị đổi → skip
                if (_uiState.value.currentPath != path) {
                    log.d("loadDirectory: path changed to '${_uiState.value.currentPath}' — skipping")
                    return@launch
                }
                // Path cùng → dùng kết quả đã cache từ coroutine trước
                log.d("loadDirectory: path unchanged, reusing cached result")
                return@launch
            }

            try {
                _uiState.update { it.copy(isLoading = true) }

                // Load current directory với cache
                var (folders, files) = getDirectoryListing(path, _uiState.value.sortBy, _uiState.value.ascending)

                // Apply "Cần thiết" filter if active
                if (_uiState.value.showEssentialOnly) {
                    folders = repository.filterEssentialFolders(folders)
                }

                // Load columns với cache (tránh query lại nhiều lần)
                val columns = stack.mapIndexed { index, p ->
                    val (f, fi) = getDirectoryListing(p, _uiState.value.sortBy, _uiState.value.ascending)
                    val filteredF = if (_uiState.value.showEssentialOnly) repository.filterEssentialFolders(f) else f
                    ColumnData(p, segments.getOrElse(index) { "" }, filteredF, fi, stack.getOrNull(index + 1))
                }

                _uiState.update {
                    it.copy(currentPath = path, pathSegments = segments, pathStack = stack, folders = folders, files = files, columns = columns, isLoading = false)
                }
            } finally {
                isLoadingLock.set(false)
            }
        }
    }

    /**
     * Get directory listing với LruCache
     * Cache key: "path|sortBy|ascending"
     */
    private fun getDirectoryListing(
        path: String,
        sortBy: FileRepository.SortBy,
        ascending: Boolean
    ): Pair<List<FolderItem>, List<RecentFile>> {
        val cacheKey = "$path|$sortBy|$ascending"

        // Check cache
        directoryCache.get(cacheKey)?.let { return it }

        // Query repository nếu chưa có trong cache
        log.d("getDirectoryListing: querying filesystem for path='$path'")
        val result = repository.listDirectory(path, sortBy, ascending)
        log.d("getDirectoryListing: got ${result.first.size} folders, ${result.second.size} files")

        // Store in cache
        directoryCache.put(cacheKey, result)

        return result
    }

    // Password callback for extraction
    var onPasswordRequired: ((archivePath: String, destPath: String) -> Unit)? = null

    /**
     * Giải nén archive file (zip) — extract toàn bộ nội dung
     * Gọi khi user select file zip và chọn "Giải nén" từ More menu
     */
    fun extractArchive(archivePath: String, destPath: String, password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read all entries from archive
                val allEntries = ArchiveReader.readEntries(archivePath, password)

                // Extract all entries
                val (success, failed) = ArchiveReader.extractEntries(
                    archivePath = archivePath,
                    entryPaths = allEntries.map { it.path },
                    destPath = destPath,
                    allEntries = allEntries,
                    password = password
                )

                // Set result for snackbar (using shared component)
                lastOperationDestPath = destPath
                resultManager.setResult(destPath, success, failed, "Giải nén")

                // Clear cache and reload
                directoryCache.evictAll()
                reload()
            } catch (e: ArchiveReader.PasswordRequiredException) {
                // Notify UI to show password dialog
                withContext(Dispatchers.Main) {
                    onPasswordRequired?.invoke(archivePath, destPath)
                }
            } catch (e: ArchiveReader.InvalidPasswordException) {
                // Show error and ask for password again (using shared component)
                lastOperationDestPath = destPath
                resultManager.setResult(destPath, 0, 1, "Giải nén thất bại - Sai mật khẩu")
                withContext(Dispatchers.Main) {
                    onPasswordRequired?.invoke(archivePath, destPath)
                }
            }
        }
    }

    /**
     * Tạo thư mục mới trong thư mục hiện tại
     */
    fun createFolder(folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentPath = _uiState.value.currentPath
                val newFolder = java.io.File(currentPath, folderName)

                if (newFolder.exists()) {
                    // Folder đã tồn tại - không nên xảy ra vì dialog đã check
                    log.w("Folder already exists: ${newFolder.absolutePath}")
                    return@launch
                }

                val created = newFolder.mkdirs()
                if (created) {
                    log.d("Created folder: ${newFolder.absolutePath}")
                    // Scan để MediaStore nhận biết folder mới (cho các app khác)
                    MediaStoreScanner.scanNewFile(
                        getApplication(),
                        parentPath = currentPath,
                        newFilePath = newFolder.absolutePath
                    )
                    // Clear cache và reload để hiển thị folder mới
                    directoryCache.evictAll()
                    reload()
                } else {
                    log.e("Failed to create folder: ${newFolder.absolutePath}")
                }
            } catch (e: Exception) {
                log.e("Error creating folder", e)
            }
        }
    }

    fun createFile(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentPath = _uiState.value.currentPath
                log.d("createFile: currentPath='$currentPath', fileName='$fileName'")
                val newFile = java.io.File(currentPath, fileName)

                if (newFile.exists()) {
                    log.w("File already exists: ${newFile.absolutePath}")
                    return@launch
                }

                val created = newFile.createNewFile()
                if (created) {
                    log.d("Created file: ${newFile.absolutePath}")
                    // Scan để MediaStore nhận biết file mới (cho các app khác)
                    MediaStoreScanner.scanNewFile(
                        getApplication(),
                        parentPath = currentPath,
                        newFilePath = newFile.absolutePath
                    )
                    directoryCache.evictAll()
                    reload()
                } else {
                    log.e("Failed to create file: ${newFile.absolutePath}")
                }
            } catch (e: Exception) {
                log.e("Error creating file", e)
            }
        }
    }

    /**
     * Paste files từ clipboard vào thư mục hiện tại.
     * @return PasteData? — dữ liệu conflict để hiển thị dialog, hoặc null để proceed trực tiếp
     */
    fun pasteFromClipboard(): PasteData? {
        log.d("=== pasteFromClipboard ===")

        val clipboardData = clipboard.getClipboardData()
        if (clipboardData == null) {
            log.w("Clipboard is empty - nothing to paste")
            return null
        }

        val (sourcePaths, operation) = clipboardData
        val destPath = _uiState.value.currentPath

        log.d("Source paths count: ${sourcePaths.size}")
        sourcePaths.forEachIndexed { index, path ->
            log.d("  Source[$index]: $path")
        }
        log.d("DestPath: $destPath")
        log.d("Operation: $operation")

        // Detect conflicts
        val conflicts = sourcePaths.count { src ->
            java.io.File(destPath, java.io.File(src).name).exists()
        }
        log.d("Conflicts detected: $conflicts")

        if (conflicts > 0) {
            log.d("Returning PasteData with conflictCount=$conflicts for dialog")
            return PasteData(
                sourcePaths = sourcePaths,
                destPath = destPath,
                operation = operation,
                conflictCount = conflicts
            )
        }

        // No conflicts — proceed directly
        executePaste(sourcePaths, destPath, operation, replaceConflicts = false)
        return null
    }

    /**
     * Execute paste với conflict resolution.
     * @param replaceConflicts true = ghi đè, false = auto-rename
     */
    fun executePaste(
        sourcePaths: List<String>,
        destPath: String,
        operation: com.example.codevui.data.FileOperations.OperationType,
        replaceConflicts: Boolean
    ) {
        log.d("=== executePaste ===")
        log.d("DestPath: $destPath")
        log.d("ReplaceConflicts: $replaceConflicts")

        if (replaceConflicts) {
            // Ghi đè — xóa file trùng trước
            log.d("Replace mode: deleting conflicting files first...")
            sourcePaths.forEach { src ->
                val name = java.io.File(src).name
                val destFile = java.io.File(destPath, name)
                if (destFile.exists()) {
                    val deleted = if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
                    log.d("  Deleted '$name': $deleted")
                }
            }
            pasteDestinationPath = if (sourcePaths.size == 1) {
                java.io.File(destPath, java.io.File(sourcePaths.first()).name).absolutePath
            } else destPath
            log.d("Replace mode: snackbar will navigate to: $pasteDestinationPath")
        } else {
            // Auto-rename — pre-resolve để snackbar navigate đúng
            pasteDestinationPath = resolveAllConflicts(sourcePaths, destPath)
            log.d("Rename mode: snackbar will navigate to: $pasteDestinationPath")
        }

        when (operation) {
            com.example.codevui.data.FileOperations.OperationType.COPY -> {
                log.d("Calling copyFiles → destPath=$destPath")
                copyFiles(sourcePaths, destPath)
            }
            com.example.codevui.data.FileOperations.OperationType.MOVE -> {
                log.d("Calling moveFiles → destPath=$destPath")
                moveFiles(sourcePaths, destPath)
            }
        }

        log.d("Clearing clipboard after paste")
        clipboard.clear()
    }

    private fun resolveConflict(file: java.io.File): java.io.File {
        if (!file.exists()) return file
        val parent = file.parentFile ?: return file
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var counter = 1
        var newFile: java.io.File
        do {
            newFile = java.io.File(parent, "${file.nameWithoutExtension} ($counter)$ext")
            counter++
        } while (newFile.exists())
        return newFile
    }

    /**
     * Pre-resolve all conflicts and return the resolved destination path.
     * For single file → returns the exact renamed path (for snackbar navigation).
     * For multi file → returns the dest folder (same as destDir).
     */
    fun resolveAllConflicts(sourcePaths: List<String>, destDir: String): String {
        if (sourcePaths.size == 1) {
            val resolved = resolveConflict(java.io.File(destDir, java.io.File(sourcePaths.first()).name))
            log.d("resolveAllConflicts: single file → resolved to: ${resolved.absolutePath}")
            return resolved.absolutePath
        } else {
            log.d("resolveAllConflicts: multi file → destDir: $destDir")
            return destDir
        }
    }
}

/**
 * Data class chứa thông tin paste để hiển thị ConflictDialog.
 */
data class PasteData(
    val sourcePaths: List<String>,
    val destPath: String,
    val operation: com.example.codevui.data.FileOperations.OperationType,
    val conflictCount: Int
)