package com.example.codevui.ui.selection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle

private const val KEY_IS_SELECTION_MODE = "selection_isSelectionMode"
private const val KEY_SELECTED_IDS = "selection_selectedIds"

/**
 * SelectionState — survive configuration change (xoay màn hình, đổi theme)
 * bằng cách dùng SavedStateHandle từ ViewModel.
 *
 * Khởi tạo trong ViewModel:
 *   val selection = SelectionState(savedStateHandle)
 *
 * FIX: Dùng observable mutableStateOf + sync với SavedStateHandle
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

    val selectedCount: Int get() = selectedIds.size

    fun enterSelectionMode(firstId: String) {
        isSelectionMode = true
        selectedIds = setOf(firstId)
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
        save()
    }

    // Sync mutableStateOf → SavedStateHandle
    private fun save() {
        savedStateHandle[KEY_IS_SELECTION_MODE] = isSelectionMode
        savedStateHandle[KEY_SELECTED_IDS] = ArrayList(selectedIds)
    }
}
