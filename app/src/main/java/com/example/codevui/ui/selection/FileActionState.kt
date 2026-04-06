package com.example.codevui.ui.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import com.example.codevui.data.FileOperations.OperationType

private const val KEY_SHOW_PICKER = "fileAction_showPicker"
private const val KEY_PENDING_OP  = "fileAction_pendingOp"

/**
 * FileActionState — survive configuration change qua SavedStateHandle.
 * Khởi tạo trong ViewModel: val fileAction = FileActionState(savedStateHandle)
 *
 * FIX: Dùng observable mutableStateOf + sync với SavedStateHandle
 */
class FileActionState(private val savedStateHandle: SavedStateHandle) {

    // Observable state cho Compose + sync với SavedStateHandle
    var showPicker by mutableStateOf(
        savedStateHandle.get<Boolean>(KEY_SHOW_PICKER) ?: false
    )
        private set

    var pendingOperation by mutableStateOf(
        savedStateHandle.get<String>(KEY_PENDING_OP)?.let {
            OperationType.valueOf(it)
        }
    )
        private set

    fun requestMove() {
        pendingOperation = OperationType.MOVE
        showPicker = true
        save()
    }

    fun requestCopy() {
        pendingOperation = OperationType.COPY
        showPicker = true
        save()
    }

    fun dismiss() {
        showPicker = false
        pendingOperation = null
        save()
    }

    fun consumeOperation(): OperationType? {
        val op = pendingOperation
        showPicker = false
        pendingOperation = null
        save()
        return op
    }

    // Sync mutableStateOf → SavedStateHandle
    private fun save() {
        savedStateHandle[KEY_SHOW_PICKER] = showPicker
        savedStateHandle[KEY_PENDING_OP] = pendingOperation?.name
    }
}
