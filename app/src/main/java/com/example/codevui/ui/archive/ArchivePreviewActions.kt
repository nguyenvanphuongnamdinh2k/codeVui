package com.example.codevui.ui.archive

import androidx.compose.runtime.*
import com.example.codevui.data.FileOperations
import com.example.codevui.ui.common.dialogs.DialogManager
import com.example.codevui.ui.selection.FolderPickerSheet
import com.example.codevui.ui.selection.PreviewBottomBar
import com.example.codevui.ui.selection.SelectionState
import java.io.File

/**
 * Preview Mode Handler
 * Handles actions and UI for preview mode trong ArchiveScreen
 */
@Composable
fun ArchivePreviewHandler(
    viewModel: ArchiveViewModel,
    selectionState: SelectionState,
    dialogManager: DialogManager,
    onExit: () -> Unit
) {
    var showMovePicker by remember { mutableStateOf(false) }
    var moveFolderName by remember { mutableStateOf("") }

    // ── FolderPickerSheet cho Move ───────────────────────────────────────
    if (showMovePicker) {
        FolderPickerSheet(
            operationType = FileOperations.OperationType.MOVE,
            selectionState = selectionState,
            onDismiss = {
                showMovePicker = false
                moveFolderName = ""
            },
            onConfirm = { destPath ->
                showMovePicker = false

                // Check conflict before moving
                val finalDestPath = File(destPath, moveFolderName).absolutePath
                val destFolder = File(finalDestPath)

                if (destFolder.exists()) {
                    // Generate unique name
                    var counter = 1
                    var uniqueName = "$moveFolderName ($counter)"
                    while (File(destPath, uniqueName).exists()) {
                        counter++
                        uniqueName = "$moveFolderName ($counter)"
                    }
                    viewModel.moveSelected(File(destPath, uniqueName).absolutePath)
                } else {
                    // No conflict, move directly
                    viewModel.moveSelected(finalDestPath)
                }

                moveFolderName = ""
            }
        )
    }

    // ── Preview Bottom Bar ─────────────────────────────────────────────
    PreviewBottomBar(
        onMove = {
            val selectedCount = selectionState.selectedIds.size
            if (selectedCount == 0) return@PreviewBottomBar

            val defaultName = if (selectedCount == 1) {
                val firstId = selectionState.selectedIds.first()
                val path = firstId.removePrefix("dir:").removePrefix("file:")
                File(path).nameWithoutExtension
            } else {
                "Moved Items"
            }

            dialogManager.showExtract(defaultName, selectedCount) { folderName ->
                moveFolderName = folderName
                showMovePicker = true
            }
        },

        onDelete = {
            val selectedIds = selectionState.selectedIds.toList()
            if (selectedIds.isEmpty()) return@PreviewBottomBar

            val selectedNames = selectedIds.map { id ->
                val path = id.removePrefix("dir:").removePrefix("file:")
                File(path).name
            }

            dialogManager.showDeleteConfirm(
                itemCount = selectedIds.size,
                itemNames = selectedNames
            ) {
                viewModel.deleteSelected()
            }
        },

        onExtract = {
            val selectedCount = selectionState.selectedIds.size
            if (selectedCount == 0) return@PreviewBottomBar

            // Default name = tên file zip (không có extension)
            val archiveName = viewModel.uiState.value.archiveName
            val defaultName = File(archiveName).nameWithoutExtension
            val parentPath = viewModel.uiState.value.archiveParentPath

            dialogManager.showExtract(
                defaultName = defaultName,
                itemCount = selectedCount,
                parentPath = parentPath
            ) { folderName ->
                // Dialog đã tự handle conflict, chỉ cần extract trực tiếp
                viewModel.extractAtParent(folderName)
            }
        },

        onExit = onExit
    )
}
