package com.example.codevui.ui.unusedapps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileRepository
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val log = Logger("UnusedAppsVM")

class UnusedAppsViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = FileRepository(application)

    private val _uiState = MutableStateFlow(UnusedAppsUiState())
    val uiState: StateFlow<UnusedAppsUiState> = _uiState.asStateFlow()

    val selection = SelectionState(savedStateHandle)

    init {
        loadUnusedApps()
    }

    fun loadUnusedApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val apps = repository.getUnusedApps()
                val sorted = applySorting(apps, _uiState.value.sortBy, _uiState.value.sortAscending)

                _uiState.update {
                    it.copy(
                        apps = sorted,
                        isLoading = false,
                        totalApps = sorted.size,
                        totalSize = sorted.sumOf { app -> app.sizeBytes },
                        error = null
                    )
                }
                log.d("Loaded ${sorted.size} unused apps")
            } catch (e: Exception) {
                log.e("Error loading unused apps", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Lỗi tải danh sách: ${e.message}"
                    )
                }
            }
        }
    }

    fun setSortBy(sortBy: UnusedAppsSortBy) {
        val currentState = _uiState.value
        // Toggle direction nếu cùng sort type
        val ascending = if (currentState.sortBy == sortBy) !currentState.sortAscending else false
        val sorted = applySorting(currentState.apps, sortBy, ascending)
        _uiState.update {
            it.copy(apps = sorted, sortBy = sortBy, sortAscending = ascending)
        }
    }

    fun setTab(tab: UnusedAppsTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun refresh() {
        loadUnusedApps()
    }

    private fun applySorting(
        apps: List<UnusedAppInfo>,
        sortBy: UnusedAppsSortBy,
        ascending: Boolean
    ): List<UnusedAppInfo> {
        val sorted = when (sortBy) {
            UnusedAppsSortBy.SIZE -> apps.sortedBy { it.sizeBytes }
            UnusedAppsSortBy.NAME -> apps.sortedBy { it.appName.lowercase() }
            UnusedAppsSortBy.LAST_USED -> apps.sortedBy { it.lastUsedTimestamp }
        }
        return if (ascending) sorted else sorted.reversed()
    }
}
