package com.example.codevui.ui.search

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileRepository
import com.example.codevui.model.FileType
import com.example.codevui.ui.common.BaseMediaStoreViewModel
import com.example.codevui.ui.common.Reloadable
import com.example.codevui.ui.selection.FileActionState
import com.example.codevui.ui.selection.SelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : BaseMediaStoreViewModel(application), Reloadable {

    private val repository = FileRepository(application)
    val selection = SelectionState(savedStateHandle)
    val fileAction = FileActionState(savedStateHandle)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Operation result for Snackbar
    data class OperationResult(val destPath: String, val success: Int, val failed: Int, val actionName: String)
    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        // Debounce search: chờ 300ms sau khi user ngừng gõ
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch()
        }
    }

    fun toggleFilter() {
        _uiState.update { it.copy(isFilterExpanded = !it.isFilterExpanded) }
    }

    fun setTimeFilter(filter: TimeFilter?) {
        _uiState.update {
            it.copy(selectedTimeFilter = if (it.selectedTimeFilter == filter) null else filter)
        }
        triggerSearch()
    }

    fun toggleTypeFilter(type: FileType) {
        _uiState.update {
            val newTypes = if (type in it.selectedTypes) {
                it.selectedTypes - type
            } else {
                it.selectedTypes + type
            }
            it.copy(selectedTypes = newTypes)
        }
        triggerSearch()
    }

    fun toggleSearchInContent() {
        _uiState.update { it.copy(searchInContent = !it.searchInContent) }
    }

    fun search() {
        triggerSearch()
    }

    private fun triggerSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch()
        }
    }

    private suspend fun performSearch() {
        val state = _uiState.value
        if (state.query.isBlank() && state.selectedTypes.isEmpty() && state.selectedTimeFilter == null) {
            _uiState.update { it.copy(files = emptyList(), folders = emptyList(), hasSearched = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true, hasSearched = true) }

        val afterTimestamp = state.selectedTimeFilter?.let {
            val now = System.currentTimeMillis() / 1000
            now - (it.daysAgo.toLong() * 24 * 60 * 60)
        }

        val (files, folders) = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val fileResults = repository.searchFiles(
                query = state.query,
                fileTypes = state.selectedTypes.ifEmpty { null },
                afterTimestamp = afterTimestamp
            )
            val folderResults = if (state.query.isNotBlank()) {
                repository.searchFoldersSuspend(state.query) // Sử dụng suspend version với cancellation support
            } else emptyList()

            fileResults to folderResults
        }

        _uiState.update {
            it.copy(files = files, folders = folders, isLoading = false)
        }
    }

    override fun reload() {
        triggerSearch()
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
