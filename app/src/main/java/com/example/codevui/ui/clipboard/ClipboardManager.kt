package com.example.codevui.ui.clipboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.example.codevui.data.FileOperations.OperationType
import com.example.codevui.util.Logger

private val log = Logger("ClipboardManager")

private const val KEY_CLIPBOARD_PATHS = "clipboard_paths"
private const val KEY_CLIPBOARD_OPERATION = "clipboard_operation"

/**
 * ClipboardManager — quản lý clipboard cho copy/cut files
 * Survive configuration change qua SavedStateHandle
 *
 * Clipboard có 2 operation types:
 * - COPY: Copy files (giữ nguyên source)
 * - MOVE: Cut files (xóa source sau khi paste)
 */
class ClipboardManager(private val savedStateHandle: SavedStateHandle) {

    // Observable state cho Compose + sync với SavedStateHandle
    // Internal để có thể access từ BrowseScreen cho reactivity
    internal var clipboardPaths by mutableStateOf(
        savedStateHandle.get<ArrayList<String>>(KEY_CLIPBOARD_PATHS)?.toList() ?: emptyList()
    )
        private set

    var clipboardOperation by mutableStateOf(
        savedStateHandle.get<String>(KEY_CLIPBOARD_OPERATION)?.let {
            OperationType.valueOf(it)
        }
    )
        private set

    /**
     * Check if clipboard has content
     */
    val hasContent: Boolean
        get() = clipboardPaths.isNotEmpty()

    /**
     * Get number of items in clipboard
     */
    val itemCount: Int
        get() = clipboardPaths.size

    /**
     * Copy files to clipboard (COPY operation)
     */
    fun copyToClipboard(paths: List<String>) {
        log.d("=== copyToClipboard ===")
        log.d("Paths count: ${paths.size}")
        paths.forEachIndexed { index, path ->
            log.d("  [$index] $path")
        }
        clipboardPaths = paths
        clipboardOperation = OperationType.COPY
        save()
        log.d("Clipboard state: hasContent=${hasContent}, itemCount=${itemCount}")
    }

    /**
     * Cut files to clipboard (MOVE operation)
     */
    fun cutToClipboard(paths: List<String>) {
        log.d("=== cutToClipboard ===")
        log.d("Paths count: ${paths.size}")
        paths.forEachIndexed { index, path ->
            log.d("  [$index] $path")
        }
        clipboardPaths = paths
        clipboardOperation = OperationType.MOVE
        save()
        log.d("Clipboard state: hasContent=${hasContent}, itemCount=${itemCount}")
    }

    /**
     * Clear clipboard (sau khi paste)
     */
    fun clear() {
        log.d("=== clear ===")
        log.d("Clearing ${clipboardPaths.size} items from clipboard")
        clipboardPaths = emptyList()
        clipboardOperation = null
        save()
        log.d("Clipboard cleared: hasContent=${hasContent}")
    }

    /**
     * Get clipboard data (paths and operation type)
     * Returns null if clipboard is empty
     */
    fun getClipboardData(): Pair<List<String>, OperationType>? {
        return if (hasContent && clipboardOperation != null) {
            clipboardPaths to clipboardOperation!!
        } else {
            null
        }
    }

    // Sync mutableStateOf → SavedStateHandle
    private fun save() {
        savedStateHandle[KEY_CLIPBOARD_PATHS] = ArrayList(clipboardPaths)
        savedStateHandle[KEY_CLIPBOARD_OPERATION] = clipboardOperation?.name
    }
}
