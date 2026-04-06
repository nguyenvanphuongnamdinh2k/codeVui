package com.example.codevui.ui.duplicates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileRepository
import com.example.codevui.ui.common.viewmodel.OperationResultManager
import com.example.codevui.ui.selection.SelectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

class DuplicatesViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = FileRepository(application)
    private val log = Logger.getLogger("DuplicatesVM")!!

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    val selection = SelectionState(savedStateHandle)
    val resultManager = OperationResultManager()

    init {
        scanDuplicates()
    }

    fun scanDuplicates() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true, scanProgress = "Đang quét file...") }
            try {
                log.info("Starting duplicate scan")
                val groups = repository.findDuplicateFiles()
                val totalWasted = groups.sumOf { it.wastedBytes }
                log.info("Scan complete: ${groups.size} groups, wasted=${totalWasted} bytes")
                _uiState.update {
                    it.copy(
                        groups = groups,
                        isLoading = false,
                        isScanning = false,
                        scanProgress = "",
                        totalWasted = totalWasted,
                        totalGroups = groups.size,
                        error = null
                    )
                }
            } catch (e: Exception) {
                log.log(Level.SEVERE, "Error scanning duplicates", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isScanning = false,
                        scanProgress = "",
                        error = "Lỗi quét file trùng lặp: ${e.message}"
                    )
                }
            }
        }
    }

    fun refresh() {
        scanDuplicates()
    }
}