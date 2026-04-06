package com.example.codevui.ui.filelist

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileRepository
import com.example.codevui.model.FileType
import com.example.codevui.ui.common.BaseMediaStoreViewModel
import com.example.codevui.ui.common.Reloadable
import com.example.codevui.ui.common.Sortable
import com.example.codevui.ui.selection.FileActionState
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FileListViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : BaseMediaStoreViewModel(application), Sortable, Reloadable {

    private val repository = FileRepository(application)
    val selection = SelectionState(savedStateHandle)
    val fileAction = FileActionState(savedStateHandle)

    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState.asStateFlow()

    // Operation result for Snackbar
    data class OperationResult(val destPath: String, val success: Int, val failed: Int, val actionName: String)
    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    private var currentFileType: FileType = FileType.OTHER
    private var currentTitle: String = ""

    fun loadFiles(fileType: FileType, title: String) {
        // Chỉ reload nếu fileType/title thay đổi
        if (_uiState.value.fileType == fileType && _uiState.value.title == title && _uiState.value.files.isNotEmpty()) {
            return // Đã load rồi, giữ nguyên state
        }
        currentFileType = fileType
        currentTitle = title
        _uiState.update { it.copy(fileType = fileType, title = title, isLoading = true) }
        reload()
    }

    override fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val state = _uiState.value
            val files = repository.getFilesByType(
                fileType = state.fileType,
                sortBy = state.sortBy,
                ascending = state.ascending
            )
            val totalSize = repository.getTotalSizeByType(state.fileType)
            _uiState.update {
                it.copy(files = files, totalSize = formatFileSize(totalSize), totalSizeBytes = totalSize, isLoading = false)
            }
        }
    }

    override fun onSortChanged(sortBy: FileRepository.SortBy) {
        val current = _uiState.value
        val newAscending = if (current.sortBy == sortBy) !current.ascending else false
        _uiState.update { it.copy(sortBy = sortBy, ascending = newAscending) }
        reload()
    }

    override fun toggleSortDirection() {
        _uiState.update { it.copy(ascending = !it.ascending) }
        reload()
    }

    override fun onOperationDone(success: Int, failed: Int, actionName: String) {
        // Set snackbar state với thông tin kết quả
        lastOperationDestPath?.let { destPath ->
            _operationResult.value = OperationResult(destPath, success, failed, actionName)
        }
        reload()
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }
}
