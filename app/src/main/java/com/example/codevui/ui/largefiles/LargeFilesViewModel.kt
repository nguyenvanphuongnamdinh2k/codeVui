package com.example.codevui.ui.largefiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileRepository
import com.example.codevui.model.FileType
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val log = Logger("LargeFilesVM")

class LargeFilesViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = FileRepository(application)

    private val _uiState = MutableStateFlow(LargeFilesUiState())
    val uiState: StateFlow<LargeFilesUiState> = _uiState.asStateFlow()

    val selection = SelectionState(savedStateHandle)

    init {
        loadLargeFiles()
    }

    fun loadLargeFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val threshold = getEffectiveThreshold()
                log.d("Loading large files, threshold=${threshold}")

                val allFiles = repository.findLargeFiles(threshold)
                val filtered = applyTypeFilter(allFiles, _uiState.value.typeFilter)
                val groups = groupBySize(filtered, threshold)

                _uiState.update {
                    it.copy(
                        allFiles = allFiles,
                        groups = groups,
                        isLoading = false,
                        totalFiles = filtered.size,
                        totalSize = filtered.sumOf { f -> f.size },
                        error = null
                    )
                }
                log.d("Loaded ${allFiles.size} files, filtered=${filtered.size}")
            } catch (e: Exception) {
                log.e("Error loading large files", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Lỗi tải danh sách: ${e.message}"
                    )
                }
            }
        }
    }

    // ── Threshold ────────────────────────────────────────

    fun setSizeThreshold(threshold: SizeThreshold) {
        _uiState.update { it.copy(sizeThreshold = threshold) }
        if (threshold != SizeThreshold.CUSTOM) {
            loadLargeFiles()
        }
    }

    fun setCustomThreshold(bytes: Long) {
        _uiState.update { it.copy(customThresholdBytes = bytes, sizeThreshold = SizeThreshold.CUSTOM) }
        loadLargeFiles()
    }

    // ── Type filter ─────────────────────────────────────

    fun setTypeFilter(filter: LargeFileTypeFilter) {
        _uiState.update { it.copy(typeFilter = filter) }
        // Re-apply filter on existing data
        viewModelScope.launch(Dispatchers.IO) {
            val allFiles = _uiState.value.allFiles
            val filtered = applyTypeFilter(allFiles, filter)
            val threshold = getEffectiveThreshold()
            val groups = groupBySize(filtered, threshold)
            _uiState.update {
                it.copy(
                    groups = groups,
                    totalFiles = filtered.size,
                    totalSize = filtered.sumOf { f -> f.size }
                )
            }
        }
    }

    fun refresh() {
        loadLargeFiles()
    }

    // ── Internal ────────────────────────────────────────

    private fun getEffectiveThreshold(): Long {
        val state = _uiState.value
        return if (state.sizeThreshold == SizeThreshold.CUSTOM) {
            state.customThresholdBytes
        } else {
            state.sizeThreshold.bytes
        }
    }

    private fun applyTypeFilter(files: List<RecentFile>, filter: LargeFileTypeFilter): List<RecentFile> {
        return when (filter) {
            LargeFileTypeFilter.ALL -> files
            LargeFileTypeFilter.IMAGE -> files.filter { it.type == FileType.IMAGE }
            LargeFileTypeFilter.VIDEO -> files.filter { it.type == FileType.VIDEO }
            LargeFileTypeFilter.OTHER -> files.filter {
                it.type != FileType.IMAGE && it.type != FileType.VIDEO
            }
        }
    }

    /**
     * Nhóm file theo khoảng kích thước giảm dần.
     * VD: threshold = 25MB → tạo nhóm "Hơn 1 GB", "Hơn 500 MB", "Hơn 100 MB", "Hơn 25 MB"
     */
    private fun groupBySize(files: List<RecentFile>, threshold: Long): List<SizeGroup> {
        // Tạo danh sách breakpoints giảm dần
        val breakpoints = mutableListOf<Pair<String, Long>>()
        val gb1 = 1024L * 1024 * 1024
        val mb500 = 500L * 1024 * 1024
        val mb400 = 400L * 1024 * 1024
        val mb100 = 100L * 1024 * 1024
        val mb25 = 25L * 1024 * 1024

        if (threshold <= gb1) breakpoints.add("Hơn 1 GB" to gb1)
        if (threshold <= mb500) breakpoints.add("Hơn 500 MB" to mb500)
        if (threshold <= mb400) breakpoints.add("Hơn 400 MB" to mb400)
        if (threshold <= mb100) breakpoints.add("Hơn 100 MB" to mb100)
        if (threshold <= mb25) breakpoints.add("Hơn 25 MB" to mb25)

        // Nếu threshold tùy chỉnh không trùng breakpoint nào
        if (breakpoints.none { it.second == threshold }) {
            val label = formatThresholdLabel(threshold)
            breakpoints.add(label to threshold)
            breakpoints.sortByDescending { it.second }
        }

        val result = mutableListOf<SizeGroup>()
        val assigned = mutableSetOf<Int>()

        for ((label, minBytes) in breakpoints) {
            val groupFiles = files.filterIndexed { index, file ->
                index !in assigned && file.size >= minBytes
            }
            if (groupFiles.isNotEmpty()) {
                groupFiles.forEachIndexed { i, _ ->
                    assigned.add(files.indexOf(groupFiles[i]))
                }
                result.add(SizeGroup(label = label, minBytes = minBytes, files = groupFiles))
            }
        }

        return result
    }

    private fun formatThresholdLabel(bytes: Long): String {
        val mb = bytes / (1024 * 1024)
        return if (mb >= 1024) "Hơn ${mb / 1024} GB" else "Hơn $mb MB"
    }
}
