package com.example.codevui.ui.selection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle

private const val KEY_IS_SELECTION_MODE = "selection_isSelectionMode"
private const val KEY_SELECTED_IDS = "selection_selectedIds"
private const val KEY_ACTIVE_CONTEXT = "selection_activeContextKey"

/**
 * SelectionState — survive configuration change (xoay màn hình, đổi theme)
 * bằng cách dùng SavedStateHandle từ ViewModel.
 *
 * Khởi tạo trong ViewModel:
 *   val selection = SelectionState(savedStateHandle)
 *
 * `activeContextKey` (optional): khi UI có nhiều "scope" độc lập (VD: Column View
 * landscape có nhiều cột) thì truyền key của scope đang select khi gọi
 * `enterSelectionMode`. Mọi `toggle` / `selectAll` sau đó vẫn chỉ áp dụng cho
 * scope đó. UI tự dựa vào `activeContextKey` để biết cột nào đang active.
 */
@Stable
class SelectionState(private val savedStateHandle: SavedStateHandle) {

    // Observable state cho Compose + sync với SavedStateHandle
    var isSelectionMode by mutableStateOf(
        savedStateHandle.get<Boolean>(KEY_IS_SELECTION_MODE) ?: false
    )
        private set

    var selectedIds by mutableStateOf(
        savedStateHandle.get<ArrayList<String>>(KEY_SELECTED_IDS)?.toSet() ?: emptySet()
    )
        private set

    /**
     * Key của "scope" đang được select (VD: path của column trong Column View).
     * `null` = không có scope rõ ràng (single-list screen như BrowseScreen portrait,
     * FileListScreen, RecentFilesScreen…).
     *
     * Được set khi gọi `enterSelectionMode(firstId, contextKey)` và giữ nguyên
     * cho đến khi `exit()`. KHÔNG bị reset khi user toggle off hết các item —
     * điều này đảm bảo "Select all" sau đó vẫn select đúng scope ban đầu.
     */
    var activeContextKey by mutableStateOf(
        savedStateHandle.get<String>(KEY_ACTIVE_CONTEXT)
    )
        private set

    val selectedCount: Int get() = selectedIds.size

    fun enterSelectionMode(firstId: String, contextKey: String? = null) {
        isSelectionMode = true
        selectedIds = setOf(firstId)
        activeContextKey = contextKey
        save()
    }

    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) {
            selectedIds - id  // Giữ selection mode — chỉ thoát khi user nhấn nút Thoát
        } else {
            selectedIds + id
        }
        save()
    }

    fun selectAll(allIds: List<String>) {
        selectedIds = if (selectedIds.size == allIds.size) emptySet() else allIds.toSet()
        // Giữ selection mode — chỉ thoát khi user nhấn nút Thoát
        save()
    }

    fun isSelected(id: String): Boolean = id in selectedIds

    fun exit() {
        isSelectionMode = false
        selectedIds = emptySet()
        activeContextKey = null
        save()
    }

    // Sync mutableStateOf → SavedStateHandle
    private fun save() {
        savedStateHandle[KEY_IS_SELECTION_MODE] = isSelectionMode
        savedStateHandle[KEY_SELECTED_IDS] = ArrayList(selectedIds)
        savedStateHandle[KEY_ACTIVE_CONTEXT] = activeContextKey
    }
}
