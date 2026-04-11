
package com.example.codevui.ui.common.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * DialogManager - Central manager cho tất cả dialogs
 * Quản lý state và lifecycle của các dialogs trong app
 *
 * Usage:
 * ```
 * val dialogManager = rememberDialogManager()
 *
 * // Show dialog
 * dialogManager.showRename("oldName.txt") { newName ->
 *     // Handle rename
 * }
 *
 * // Render dialogs
 * DialogHandler(dialogManager)
 * ```
 */
class DialogManager {

    // Dialog states
    var dialogType by mutableStateOf<DialogType?>(null)
        private set

    // Dialog data
    private var dialogData by mutableStateOf<Any?>(null)
    private var dialogCallback by mutableStateOf<((Any?) -> Unit)?>(null)

    /**
     * Show rename dialog
     */
    fun showRename(currentName: String, onConfirm: (String) -> Unit) {
        dialogType = DialogType.RENAME
        dialogData = currentName
        dialogCallback = { result -> onConfirm(result as String) }
    }

    /**
     * Show batch rename dialog (nhiều file cùng lúc)
     */
    fun showBatchRename(items: List<RenameItem>, onConfirm: (Map<String, String>) -> Unit) {
        dialogType = DialogType.BATCH_RENAME
        dialogData = items
        dialogCallback = { result -> onConfirm(result as Map<String, String>) }
    }

    /**
     * Show details dialog
     */
    fun showDetails(paths: List<String>) {
        dialogType = DialogType.DETAILS
        dialogData = paths
        dialogCallback = null
    }

    /**
     * Show compress dialog
     */
    fun showCompress(
        defaultName: String,
        itemCount: Int = 1,
        parentPath: String? = null,
        onConfirm: (String) -> Unit
    ) {
        dialogType = DialogType.COMPRESS
        dialogData = CompressData(defaultName, itemCount, parentPath)
        dialogCallback = { result -> onConfirm(result as String) }
    }

    /**
     * Show extract dialog
     */
    fun showExtract(
        defaultName: String,
        itemCount: Int = 1,
        parentPath: String? = null,
        onConfirm: (String) -> Unit
    ) {
        dialogType = DialogType.EXTRACT
        dialogData = ExtractData(defaultName, itemCount, parentPath)
        dialogCallback = { result -> onConfirm(result as String) }
    }

    /**
     * Show password dialog
     */
    fun showPassword(
        title: String = "Nhập mật khẩu",
        message: String = "File nén này được bảo vệ bằng mật khẩu",
        onConfirm: (String) -> Unit
    ) {
        dialogType = DialogType.PASSWORD
        dialogData = PasswordData(title, message)
        dialogCallback = { result -> onConfirm(result as String) }
    }

    /**
     * Show delete confirmation dialog
     */
    fun showDeleteConfirm(
        itemCount: Int,
        itemNames: List<String> = emptyList(),
        onConfirm: () -> Unit
    ) {
        dialogType = DialogType.DELETE_CONFIRM
        dialogData = DeleteConfirmData(itemCount, itemNames)
        dialogCallback = { onConfirm() }
    }

    /**
     * Dismiss current dialog
     */
    fun dismiss() {
        dialogType = null
        dialogData = null
        dialogCallback = null
    }

    /**
     * Confirm current dialog
     */
    fun confirm(result: Any?) {
        dialogCallback?.invoke(result)
        dismiss()
    }

    // Helper to get typed data
    @Suppress("UNCHECKED_CAST")
    fun <T> getData(): T? = dialogData as? T

    // Data classes for complex dialogs
    data class CompressData(val defaultName: String, val itemCount: Int, val parentPath: String? = null)
    data class ExtractData(val defaultName: String, val itemCount: Int, val parentPath: String? = null)
    data class PasswordData(val title: String, val message: String)
    data class DeleteConfirmData(val itemCount: Int, val itemNames: List<String>)
}

/**
 * Dialog types
 */
enum class DialogType {
    RENAME,
    BATCH_RENAME,
    DETAILS,
    COMPRESS,
    EXTRACT,
    PASSWORD,
    DELETE_CONFIRM
}

/**
 * Composable helper to create DialogManager
 */
@Composable
fun rememberDialogManager(): DialogManager {
    return remember { DialogManager() }
}

/**
 * DialogHandler - Render dialog dựa vào DialogManager state
 * Đặt component này ở cuối screen để auto-render dialogs
 */
@Composable
fun DialogHandler(manager: DialogManager) {
    when (manager.dialogType) {
        DialogType.RENAME -> {
            val currentName = manager.getData<String>() ?: return
            RenameDialog(
                currentName = currentName,
                onDismiss = { manager.dismiss() },
                onConfirm = { newName -> manager.confirm(newName) }
            )
        }

        DialogType.BATCH_RENAME -> {
            @Suppress("UNCHECKED_CAST")
            val items = manager.getData<List<RenameItem>>() ?: return
            BatchRenameDialog(
                items = items,
                onDismiss = { manager.dismiss() },
                onConfirm = { result -> manager.confirm(result) }
            )
        }

        DialogType.DETAILS -> {
            val paths = manager.getData<List<String>>() ?: return
            DetailsDialog(
                paths = paths,
                onDismiss = { manager.dismiss() }
            )
        }

        DialogType.COMPRESS -> {
            val data = manager.getData<DialogManager.CompressData>() ?: return
            CompressDialog(
                defaultName = data.defaultName,
                itemCount = data.itemCount,
                parentPath = data.parentPath,
                onDismiss = { manager.dismiss() },
                onConfirm = { zipName -> manager.confirm(zipName) }
            )
        }

        DialogType.EXTRACT -> {
            val data = manager.getData<DialogManager.ExtractData>() ?: return
            ExtractDialog(
                defaultName = data.defaultName,
                itemCount = data.itemCount,
                parentPath = data.parentPath,
                onDismiss = { manager.dismiss() },
                onConfirm = { folderName -> manager.confirm(folderName) }
            )
        }

        DialogType.PASSWORD -> {
            val data = manager.getData<DialogManager.PasswordData>() ?: return
            PasswordDialog(
                title = data.title,
                message = data.message,
                onDismiss = { manager.dismiss() },
                onConfirm = { password -> manager.confirm(password) }
            )
        }

        DialogType.DELETE_CONFIRM -> {
            val data = manager.getData<DialogManager.DeleteConfirmData>() ?: return
            DeleteConfirmDialog(
                itemCount = data.itemCount,
                itemNames = data.itemNames,
                onDismiss = { manager.dismiss() },
                onConfirm = { manager.confirm(Unit) }
            )
        }

        null -> {
            // No dialog to show
        }
    }
}
