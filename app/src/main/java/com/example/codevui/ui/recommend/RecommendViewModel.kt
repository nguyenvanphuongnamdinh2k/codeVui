package com.example.codevui.ui.recommend

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.MediaStoreScanner
import com.example.codevui.data.RecommendRepository
import com.example.codevui.data.TrashManager
import com.example.codevui.model.RecommendCard
import com.example.codevui.model.RecommendFile
import com.example.codevui.model.RecommendType
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel cho màn hình Recommend Files.
 *
 * Luồng hoạt động:
 * 1. Load cards → hiển thị danh sách loại card
 * 2. Tap card → load files → hiển thị danh sách file
 * 3. Select files → delete → move to trash → scan → reload
 */
class RecommendViewModel(application: Application) : AndroidViewModel(application) {

    private val log = Logger("RecommendViewModel")
    private val repository = RecommendRepository(application)
    private val trashManager = TrashManager(application)

    private val _uiState = MutableStateFlow(RecommendUiState())
    val uiState: StateFlow<RecommendUiState> = _uiState.asStateFlow()

    init {
        loadCards()
    }

    // ══════════════════════════════════════════════════════════════
    // Load cards (được gọi từ StorageManagerScreen)
    // ══════════════════════════════════════════════════════════════

    fun loadCards() {
        log.d("loadCards: start")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val cards = repository.getAllCards()
                log.d("loadCards: found ${cards.size} cards with non-zero size")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        cards = cards
                    )
                }
            } catch (e: Exception) {
                log.e("loadCards: failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Lỗi khi tải dữ liệu"
                    )
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Load files for a selected card
    // ══════════════════════════════════════════════════════════════

    fun selectCard(type: RecommendType) {
        log.d("selectCard: type=$type")
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCardType = type, isLoadingFiles = true, selectedFilePaths = emptySet()) }
            try {
                val files = repository.getCardFiles(type)
                val card = _uiState.value.cards.find { it.type == type }
                log.d("selectCard: found ${files.size} files for ${type.name}")
                _uiState.update {
                    it.copy(
                        isLoadingFiles = false,
                        files = files,
                        // Thêm card hiện tại vào danh sách nếu chưa có
                        cards = if (it.cards.none { c -> c.type == type }) {
                            it.cards + listOfNotNull(card)
                        } else it.cards
                    )
                }
            } catch (e: Exception) {
                log.e("selectCard: failed", e)
                _uiState.update {
                    it.copy(
                        isLoadingFiles = false,
                        errorMessage = e.message ?: "Lỗi khi tải danh sách file"
                    )
                }
            }
        }
    }

    fun clearSelectedCard() {
        log.d("clearSelectedCard")
        _uiState.update {
            it.copy(
                selectedCardType = null,
                files = emptyList(),
                selectedFilePaths = emptySet()
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Selection
    // ══════════════════════════════════════════════════════════════

    fun toggleFileSelection(path: String) {
        _uiState.update {
            val current = it.selectedFilePaths
            it.copy(
                selectedFilePaths = if (path in current) current - path else current + path
            )
        }
    }

    fun selectAllFiles() {
        _uiState.update {
            it.copy(selectedFilePaths = it.files.map { f -> f.path }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFilePaths = emptySet()) }
    }

    fun isFileSelected(path: String): Boolean = path in _uiState.value.selectedFilePaths

    // ══════════════════════════════════════════════════════════════
    // Delete — move selected files to trash
    // ══════════════════════════════════════════════════════════════

    fun deleteSelectedFiles() {
        val paths = _uiState.value.selectedFilePaths.toList()
        if (paths.isEmpty()) return

        log.d("deleteSelectedFiles: ${paths.size} files")
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            try {
                // Di chuyển vào thùng rác
                val (success, failed) = trashManager.moveToTrash(paths)

                // Scan để cập nhật MediaStore
                val parentDirs = paths.mapNotNull { path ->
                    File(path).parent
                }.distinct()

                if (parentDirs.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        MediaStoreScanner.scanPaths(getApplication(), parentDirs)
                    }
                }

                log.d("deleteSelectedFiles: done — success=$success, failed=$failed")

                // Xóa khỏi danh sách hiện tại
                _uiState.update { state ->
                    val remainingFiles = state.files.filter { f -> f.path !in paths.toSet() }
                    state.copy(
                        isDeleting = false,
                        files = remainingFiles,
                        selectedFilePaths = emptySet(),
                        deleteSuccessCount = success,
                        deleteErrorCount = failed
                    )
                }

                // Reload cards để cập nhật size
                loadCards()

            } catch (e: Exception) {
                log.e("deleteSelectedFiles: failed", e)
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = e.message ?: "Lỗi khi xóa file"
                    )
                }
            }
        }
    }

    fun clearDeleteResult() {
        _uiState.update { it.copy(deleteSuccessCount = 0, deleteErrorCount = 0) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
