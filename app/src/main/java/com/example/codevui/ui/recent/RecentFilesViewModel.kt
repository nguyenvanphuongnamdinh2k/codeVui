package com.example.codevui.ui.recent

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileRepository
import com.example.codevui.ui.common.BaseMediaStoreViewModel
import com.example.codevui.ui.common.Reloadable
import com.example.codevui.ui.selection.FileActionState
import com.example.codevui.ui.selection.SelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecentFilesViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : BaseMediaStoreViewModel(application), Reloadable {

    private val repository = FileRepository(application)
    val selection = SelectionState(savedStateHandle)
    val fileAction = FileActionState(savedStateHandle)

    private val _uiState = MutableStateFlow(RecentFilesUiState())
    val uiState: StateFlow<RecentFilesUiState> = _uiState.asStateFlow()

    // Operation result for Snackbar
    data class OperationResult(val destPath: String, val success: Int, val failed: Int, val actionName: String)
    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    private val tabsLimit = Int.MAX_VALUE

    override fun onOperationDone(success: Int, failed: Int, actionName: String) {
        lastOperationDestPath?.let { destPath ->
            _operationResult.value = OperationResult(destPath, success, failed, actionName)
        }
        reload()
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }

    fun load(limit: Int = 20) {
        if (_uiState.value.allFiles.isNotEmpty()) {
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            loadInternal()
        }
    }

    override fun reload() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            loadInternal()
        }
    }

    private fun loadInternal() {
        val allFiles = repository.getRecentFiles(limit = tabsLimit)
        val appNames = allFiles.mapNotNull { it.ownerApp }.distinct().sorted()
        val tabs = mutableListOf("Tất cả").also { it.addAll(appNames) }

        val currentTab = _uiState.value.selectedTab
        val tabName = tabs.getOrNull(currentTab)
        val filtered = when {
            currentTab == 0 -> allFiles
            tabName != null -> allFiles.filter { it.ownerApp == tabName }
            else -> allFiles
        }

        _uiState.update {
            it.copy(files = filtered, allFiles = allFiles, tabs = tabs, isLoading = false)
        }
    }

    fun selectTab(index: Int) {
        val allFiles = _uiState.value.allFiles
        val tabs = _uiState.value.tabs
        val tabName = tabs.getOrNull(index)
        val filtered = when {
            index == 0 -> allFiles
            tabName != null -> allFiles.filter { it.ownerApp == tabName }
            else -> allFiles
        }
        _uiState.update { it.copy(selectedTab = index, files = filtered) }
    }
}