package com.example.codevui.ui.duplicates

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.data.FileRepository
import com.example.codevui.model.DuplicateGroup
import com.example.codevui.ui.common.BaseMediaStoreViewModel
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
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : BaseMediaStoreViewModel(application) {

    private val repository = FileRepository(application)
    private val log = Logger.getLogger("DuplicatesVM")!!

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    val selection = SelectionState(savedStateHandle)
    val resultManager = OperationResultManager()

    init {
        reload()
    }

    override fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isScanning = true, scanProgress = "Đang quét file...") }
            try {
                log.info("Starting duplicate scan")
                val newGroups = repository.findDuplicateFiles()
                val totalWasted = newGroups.sumOf { it.wastedBytes }

                // Smart diff: giữ nguyên object reference nếu group không đổi
                // → LazyColumn chỉ recompose item thực sự thay đổi
                val currentGroups = _uiState.value.groups
                val diffedGroups = diffGroups(currentGroups, newGroups)

                _uiState.update {
                    it.copy(
                        groups = diffedGroups,
                        isLoading = false,
                        isScanning = false,
                        scanProgress = "",
                        totalWasted = totalWasted,
                        totalGroups = newGroups.size,
                        error = null
                    )
                }
                log.info("Scan complete: ${newGroups.size} groups, wasted=${totalWasted} bytes")
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

    /**
     * Diff 2 danh sách groups — giữ nguyên object reference cho groups không đổi.
     * Compose LazyColumn so sánh equals() trên item → skip recompose nếu giống nhau.
     */
    private fun diffGroups(
        current: List<DuplicateGroup>,
        new: List<DuplicateGroup>
    ): List<DuplicateGroup> {
        if (current.isEmpty()) return new

        val currentByHash = current.associateBy { it.hash }
        val result = mutableListOf<DuplicateGroup>()

        for (ng in new) {
            val existing = currentByHash[ng.hash]
            if (existing != null && existing == ng) {
                // Group không đổi → giữ nguyên reference → Compose skip recompose
                result.add(existing)
            } else {
                // Group mới hoặc thay đổi → dùng new object
                result.add(ng)
            }
        }
        return result
    }

    fun refresh() {
        reload()
    }

    override fun onOperationDone(success: Int, failed: Int, actionName: String) {
        // Operation done → reload duplicate scan
        reload()
    }
}
