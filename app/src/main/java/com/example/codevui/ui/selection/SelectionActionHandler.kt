package com.example.codevui.ui.selection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.codevui.data.FavoriteManager
import com.example.codevui.data.FileOperations
import com.example.codevui.data.TrashManager
import com.example.codevui.ui.common.dialogs.RenameItem
import com.example.codevui.ui.clipboard.ClipboardManager as FileClipboardManager
import com.example.codevui.ui.common.dialogs.ConflictDialog
import com.example.codevui.ui.common.dialogs.DialogHandler
import com.example.codevui.ui.common.dialogs.MoveToTrashDialog
import com.example.codevui.ui.common.dialogs.rememberDialogManager
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val log = Logger("SelectionActionHandler")

@Composable
fun selectionActionHandler(
    selectionState: SelectionState,
    fileActionState: FileActionState,
    fileClipboard: FileClipboardManager? = null,
    currentPath: String = "",
    onOperationComplete: () -> Unit = {},
    onRenameComplete: (renamedPath: String, newName: String) -> Unit = { _, _ -> },
    onCopyFiles: (
        sourcePaths: List<String>,
        destDir: String,
        resolvedDestPath: String?
    ) -> Unit = { _, _, _ -> },
    onMoveFiles: (
        sourcePaths: List<String>,
        destDir: String,
        resolvedDestPath: String?
    ) -> Unit = { _, _, _ -> },
    onCompressFiles: (sourcePaths: List<String>, zipName: String?) -> Unit = { _, _ -> },
    onExtractArchive: (archivePath: String, destDir: String) -> Unit = { _, _ -> }
): SelectionActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dialogManager = rememberDialogManager()
    val trashManager = remember(context) { TrashManager(context) }

    fun selectedPaths(): List<String> =
        selectionState.selectedIds.map { it.removePrefix("file:").removePrefix("folder:") }

    var conflictData by remember { mutableStateOf<ConflictData?>(null) }
    var showExtractPicker by remember { mutableStateOf(false) }
    var extractFolderName by remember { mutableStateOf("") }

    var showMoveToTrashDialog by remember { mutableStateOf(false) }
    var pendingDeletePaths by remember { mutableStateOf<List<String>>(emptyList()) }

    val hasArchiveFiles = remember(selectionState.selectedIds) {
        selectedPaths().filter { File(it).isFile }.any {
            File(it).extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz")
        }
    }

    // ── Favorites visibility (MyFiles pattern) ──────────────────────────────────
    // Observe toàn bộ favorites từ Room thành Flow<Set<String>> → lookup O(1)
    // Đổi từ LaunchedEffect+async-loop sang Flow observer để realtime + tránh
    // race condition giữa toggle favorite và hiển thị menu.
    val allFavoritePaths by remember(context) {
        FavoriteManager.observeFavoritePaths(context)
    }.collectAsStateWithLifecycle(initialValue = emptySet())

    val selectedCount = selectionState.selectedCount
    val favoriteCount = remember(selectionState.selectedIds, allFavoritePaths) {
        selectedPaths().count { it in allFavoritePaths }
    }
    val canAddToFavorites = selectedCount > 0 && favoriteCount < selectedCount
    val canRemoveFromFavorites = favoriteCount > 0

    // Copy/Move destination picker
    if (fileActionState.showPicker && fileActionState.pendingOperation != null) {
        FolderPickerSheet(
            operationType = fileActionState.pendingOperation!!,
            selectionState = selectionState,
            onDismiss = { fileActionState.dismiss() },
            onConfirm = { destPath ->
                val paths = selectedPaths()
                val op = fileActionState.consumeOperation()!!
                val conflicts = countConflicts(paths, destPath)

                log.d("Picker confirmed: op=$op, items=${paths.size}, dest=$destPath, conflicts=$conflicts")

                if (conflicts > 0) {
                    conflictData = ConflictData(paths, destPath, op, conflicts)
                } else {
                    if (op == FileOperations.OperationType.MOVE) {
                        onMoveFiles(paths, destPath, null)
                    } else {
                        onCopyFiles(paths, destPath, null)
                    }
                    selectionState.exit()
                }
            }
        )
    }

    // Extract destination picker
    if (showExtractPicker) {
        FolderPickerSheet(
            operationType = FileOperations.OperationType.COPY,
            selectionState = selectionState,
            onDismiss = {
                showExtractPicker = false
                extractFolderName = ""
            },
            onConfirm = { destPath ->
                showExtractPicker = false
                val archives = selectedPaths().filter {
                    File(it).extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz")
                }
                val finalDest = File(destPath, extractFolderName).absolutePath
                val conflicts = archives.count { File(finalDest, File(it).name).exists() }

                log.d("Extract picker confirmed: items=${archives.size}, dest=$finalDest, conflicts=$conflicts")

                if (conflicts > 0) {
                    conflictData = ConflictData(
                        sourcePaths = archives,
                        destPath = finalDest,
                        operationType = FileOperations.OperationType.COPY,
                        conflictCount = conflicts,
                        isExtract = true
                    )
                } else {
                    scope.launch {
                        archives.forEach { onExtractArchive(it, finalDest) }
                    }
                    selectionState.exit()
                    extractFolderName = ""
                }
            }
        )
    }

    // Conflict dialog for Copy/Move/Extract
    conflictData?.let { data ->
        ConflictDialog(
            conflictCount = data.conflictCount,
            operationName = data.operationType.displayName,
            onDismiss = {
                log.d("ConflictDialog: cancel")
                conflictData = null
            },
            onReplace = {
                log.d("ConflictDialog: replace")
                conflictData = null

                if (data.isExtract) {
                    scope.launch {
                        data.sourcePaths.forEach { src ->
                            File(data.destPath, File(src).name).delete()
                            onExtractArchive(src, data.destPath)
                        }
                    }
                    selectionState.exit()
                } else {
                    if (data.operationType == FileOperations.OperationType.MOVE) {
                        onMoveFiles(data.sourcePaths, data.destPath, null)
                    } else {
                        onCopyFiles(data.sourcePaths, data.destPath, null)
                    }
                    selectionState.exit()
                }
            },
            onRename = {
                log.d("ConflictDialog: auto-rename")
                conflictData = null

                if (data.isExtract) {
                    scope.launch {
                        data.sourcePaths.forEach { src ->
                            val resolved = resolveConflictRename(File(data.destPath, File(src).name))
                            log.d("Extract resolve: ${File(src).name} -> ${resolved.name}")
                            onExtractArchive(src, resolved.parentFile?.absolutePath ?: data.destPath)
                        }
                    }
                    selectionState.exit()
                } else {
                    val resolvedDestPath = if (data.sourcePaths.size == 1) {
                        val srcName = File(data.sourcePaths.first()).name
                        val resolved = resolveConflictRename(File(data.destPath, srcName))
                        log.d("Resolve conflict: $srcName -> ${resolved.name}")
                        resolved.absolutePath
                    } else data.destPath

                    log.d("Snackbar destination after rename: $resolvedDestPath")

                    if (data.operationType == FileOperations.OperationType.MOVE) {
                        onMoveFiles(data.sourcePaths, data.destPath, resolvedDestPath)
                    } else {
                        onCopyFiles(data.sourcePaths, data.destPath, resolvedDestPath)
                    }
                    selectionState.exit()
                }
            }
        )
    }

    // Move-to-trash confirm dialog
    if (showMoveToTrashDialog) {
        MoveToTrashDialog(
            itemCount = pendingDeletePaths.size,
            onDismiss = {
                showMoveToTrashDialog = false
                pendingDeletePaths = emptyList()
            },
            onConfirm = {
                val targets = pendingDeletePaths
                showMoveToTrashDialog = false
                pendingDeletePaths = emptyList()

                scope.launch {
                    val (success, failed) = withContext(Dispatchers.IO) {
                        trashManager.moveToTrash(targets)
                    }
                    log.d("MoveToTrash result: success=$success, failed=$failed")
                    val msg = if (failed == 0) {
                        "Đã chuyển $success mục vào Thùng rác"
                    } else {
                        "Chuyển $success mục, thất bại $failed"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    selectionState.exit()
                    onOperationComplete()
                }
            }
        )
    }

    val actions = remember(
        selectionState,
        fileActionState,
        fileClipboard,
        hasArchiveFiles,
        canAddToFavorites,
        canRemoveFromFavorites,
        selectedCount,
        currentPath
    ) {
        SelectionActions(
            onMove = {
                val paths = selectedPaths()
                log.d("onMove clicked: ${paths.size} items")
                fileActionState.requestMove()
            },
            onCopy = {
                val paths = selectedPaths()
                log.d("onCopy clicked: ${paths.size} items")
                fileActionState.requestCopy()
            },
            onCut = if (fileClipboard != null) {
                {
                    val paths = selectedPaths()
                    fileClipboard.cutToClipboard(paths)
                    Toast.makeText(context, "Đã cắt ${paths.size} mục", Toast.LENGTH_SHORT).show()
                    selectionState.exit()
                }
            } else { {} },
            onCopyToFileClipboard = if (fileClipboard != null) {
                {
                    val paths = selectedPaths()
                    fileClipboard.copyToClipboard(paths)
                    Toast.makeText(context, "Đã sao chép ${paths.size} mục", Toast.LENGTH_SHORT).show()
                    selectionState.exit()
                }
            } else { {} },
            onDelete = {
                val paths = selectedPaths()
                log.d("onDelete clicked: ${paths.size} items")
                pendingDeletePaths = paths
                showMoveToTrashDialog = true
            },
            onShare = {
                val files = selectedPaths().map { File(it) }.filter { it.isFile }
                if (files.isEmpty()) {
                    Toast.makeText(context, "Không có file nào để chia sẻ", Toast.LENGTH_SHORT).show()
                    return@SelectionActions
                }
                val authority = "${context.packageName}.fileprovider"
                val uris = files.map { FileProvider.getUriForFile(context, authority, it) }
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = getMimeType(files.first()) ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, uris.first())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                context.startActivity(Intent.createChooser(intent, null))
            },
            onCopyToClipboard = {
                val paths = selectedPaths()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("file_paths", paths.joinToString("\n"))
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Đã sao chép ${paths.size} đường dẫn", Toast.LENGTH_SHORT).show()
            },
            onDetails = {
                dialogManager.showDetails(selectedPaths())
            },
            onRename = {
                val paths = selectedPaths()
                val items = paths.map { path ->
                    RenameItem(path = path, originalName = File(path).name)
                }
                dialogManager.showBatchRename(items) { results ->
                    scope.launch {
                        var successCount = 0
                        var failCount = 0
                        results.forEach { (path, newName) ->
                            val file = File(path)
                            val dest = File(file.parent, newName)
                            val ok = withContext(Dispatchers.IO) {
                                if (!dest.exists()) file.renameTo(dest) else false
                            }
                            if (ok) successCount++ else failCount++
                        }
                        val msg = when {
                            failCount == 0 -> "Đã đổi tên $successCount mục"
                            successCount == 0 -> "Đổi tên thất bại"
                            else -> "Đổi tên $successCount mục, thất bại $failCount"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        selectionState.exit()
                        if (successCount > 0) {
                            // Notify first renamed item for scroll/highlight
                            results.entries.firstOrNull { it.value.isNotEmpty() }?.let { (path, _) ->
                                onRenameComplete(path, File(path).name)
                            }
                        } else {
                            onOperationComplete()
                        }
                    }
                }
            },
            onCompress = if (!hasArchiveFiles) {
                {
                    val paths = selectedPaths()
                    val defaultName = if (paths.size == 1) File(paths.first()).nameWithoutExtension else "Archive"
                    dialogManager.showCompress(defaultName, paths.size, currentPath) { zipName ->
                        onCompressFiles(paths, zipName)
                        selectionState.exit()
                    }
                }
            } else null,  // null = ẩn Nén (vì có archive files được chọn)
            onAddToFavorites = if (canAddToFavorites) {
                {
                    scope.launch {
                        val paths = selectedPaths()
                        var success = 0
                        for (path in paths) {
                            if (FavoriteManager.isFavorite(context, path)) continue
                            val file = File(path)
                            val mimeType = if (file.isFile) {
                                android.webkit.MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(file.extension.lowercase())
                                    ?: when (file.extension.lowercase()) {
                                        "apk" -> "application/vnd.android.package-archive"
                                        else -> null
                                    }
                            } else null
                            val added = FavoriteManager.addFavorite(
                                context = context,
                                path = path,
                                name = file.name,
                                size = if (file.isFile) file.length() else 0L,
                                mimeType = mimeType,
                                isDirectory = file.isDirectory,
                                dateModified = file.lastModified() / 1000
                            )
                            if (added) success++
                        }
                        if (success > 0) {
                            Toast.makeText(context, "Đã thêm $success mục vào yêu thích", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Mục đã có trong yêu thích", Toast.LENGTH_SHORT).show()
                        }
                        selectionState.exit()
                    }
                }
            } else null,  // null = ẩn (tất cả đã favorite)
            onRemoveFromFavorites = if (canRemoveFromFavorites) {
                {
                    scope.launch {
                        val paths = selectedPaths()
                        var removed = 0
                        for (path in paths) {
                            if (FavoriteManager.isFavorite(context, path)) {
                                if (FavoriteManager.removeFavorite(context, path)) removed++
                            }
                        }
                        if (removed > 0) {
                            Toast.makeText(context, "Đã xóa $removed mục khỏi yêu thích", Toast.LENGTH_SHORT).show()
                        }
                        selectionState.exit()
                    }
                }
            } else null,  // null = ẩn (không có favorite)
            onAddToHomeScreen = {
                val paths = selectedPaths()
                if (paths.size == 1) {
                    val file = File(paths.first())
                    try {
                        val shortcutIntent = Intent(context, context.javaClass).apply {
                            action = Intent.ACTION_VIEW
                            putExtra("shortcut_path", file.absolutePath)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                            putExtra(Intent.EXTRA_SHORTCUT_NAME, file.name)
                            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                            putExtra("duplicate", false)
                        }
                        context.sendBroadcast(addIntent)
                        Toast.makeText(context, "Đã thêm lối tắt: ${file.name}", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "Không thể tạo lối tắt", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Chỉ có thể tạo lối tắt cho 1 mục", Toast.LENGTH_SHORT).show()
                }
            },
            onExtract = if (hasArchiveFiles) {
                {
                    val paths = selectedPaths()
                    val defaultName = if (paths.size == 1) File(paths.first()).nameWithoutExtension else "Extract"
                    dialogManager.showExtract(defaultName, paths.size) { folderName ->
                        extractFolderName = folderName
                        showExtractPicker = true
                    }
                }
            } else null
        )
    }

    DialogHandler(dialogManager)
    return actions
}

private fun getMimeType(file: File): String? {
    val ext = file.extension.lowercase()
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
}

private fun countConflicts(sourcePaths: List<String>, destPath: String): Int {
    return sourcePaths.count { src -> File(destPath, File(src).name).exists() }
}

private fun resolveConflictRename(file: File): File {
    if (!file.exists()) return file
    val parent = file.parentFile ?: return file
    val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
    var counter = 1
    var newFile: File
    do {
        newFile = File(parent, "${file.nameWithoutExtension} ($counter)$ext")
        counter++
    } while (newFile.exists())
    return newFile
}

private data class ConflictData(
    val sourcePaths: List<String>,
    val destPath: String,
    val operationType: FileOperations.OperationType,
    val conflictCount: Int,
    val isExtract: Boolean = false
)

private val FileOperations.OperationType.displayName: String
    get() = when (this) {
        FileOperations.OperationType.COPY -> "sao chép"
        FileOperations.OperationType.MOVE -> "di chuyển"
    }

data class SelectionActions(
    val onMove: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onCut: () -> Unit = {},
    val onCopyToFileClipboard: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onCopyToClipboard: () -> Unit = {},
    val onDetails: () -> Unit = {},
    val onRename: () -> Unit = {},
    val onCompress: (() -> Unit)? = null,   // null = ẩn (khi có archive files)
    val onExtract: (() -> Unit)? = null,   // null = ẩn (khi không có archive)
    val onAddToFavorites: (() -> Unit)? = null,  // null = ẩn (khi đã favorite hết)
    val onRemoveFromFavorites: (() -> Unit)? = null,  // null = ẩn (khi không có favorite)
    val onAddToHomeScreen: () -> Unit = {}
)
