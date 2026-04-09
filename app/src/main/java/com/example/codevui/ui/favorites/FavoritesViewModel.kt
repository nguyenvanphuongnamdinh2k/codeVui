package com.example.codevui.ui.favorites

import android.app.Application
import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FavoriteManager
import com.example.codevui.data.MediaStoreScanner
import com.example.codevui.ui.clipboard.ClipboardManager
import com.example.codevui.ui.common.BaseFileOperationViewModel
import com.example.codevui.ui.common.viewmodel.OperationResultManager
import com.example.codevui.ui.selection.FileActionState
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel cho màn hình Favorites.
 *
 * Luồng hoạt động:
 * 1. Observe favorites từ Room (Flow) → tự động update UI
 * 2. Validate favorites (xóa những file không tồn tại)
 * 3. Selection mode: Move/Copy/Share/Delete/Rename/Details/Compress
 * 4. Reorder → cập nhật sortOrder
 */
class FavoritesViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle = SavedStateHandle()
) : BaseFileOperationViewModel(application) {

    private val log = Logger("FavoritesViewModel")
    private val context = application

    // Selection state (survive rotation)
    val selection = SelectionState(savedStateHandle)
    val fileAction = FileActionState(savedStateHandle)
    val clipboard = ClipboardManager(savedStateHandle)

    // Operation result manager (shared snackbar)
    val resultManager = OperationResultManager()

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
        validateOnStart()
        // Observe operation state from BaseFileOperationViewModel
        viewModelScope.launch {
            operationState.collect { state ->
                _uiState.update { it.copy(operationState = state) }
            }
        }
        viewModelScope.launch {
            operationTitle.collect { title ->
                _uiState.update { it.copy(operationTitle = title) }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Observe favorites (Flow — tự động update khi DB thay đổi)
    // ══════════════════════════════════════════════════════════════

    private fun observeFavorites() {
        viewModelScope.launch {
            FavoriteManager.observeFavorites(context).collect { favorites ->
                log.d("observeFavorites: ${favorites.size} favorites")
                _uiState.update {
                    it.copy(
                        favorites = favorites,
                        isLoading = false
                    )
                }
            }
        }
    }

    /** Validate favorites khi mở màn hình — xóa những file không tồn tại */
    private fun validateOnStart() {
        viewModelScope.launch {
            val removed = FavoriteManager.validateFavorites(context)
            if (removed > 0) {
                log.d("validateOnStart: removed $removed invalid favorites")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // File operations (Move/Copy/Compress) — dùng BaseFileOperationViewModel
    // ══════════════════════════════════════════════════════════════

    /** Copy files — delegate to BaseFileOperationViewModel.copyFiles() */
    fun copyFiles(sourcePaths: List<String>, destDir: String) {
        if (sourcePaths.isEmpty()) return
        copyFiles(sourcePaths, destDir, null)
    }

    /** Move files — delegate to BaseFileOperationViewModel.moveFiles() */
    fun moveFiles(sourcePaths: List<String>, destDir: String) {
        if (sourcePaths.isEmpty()) return
        moveFiles(sourcePaths, destDir, null)
    }

    /** Compress files — delegate to BaseFileOperationViewModel.compressFiles() */
    fun compressFiles(sourcePaths: List<String>, zipName: String?) {
        if (sourcePaths.isEmpty()) return
        compressFiles(sourcePaths, zipName, null)
    }

    override fun onOperationDone(success: Int, failed: Int, actionName: String) {
        // Favorites dùng Room Flow nên không cần reload thủ công
        log.d("onOperationDone: $actionName success=$success failed=$failed")
    }

    fun clearOperationResult() {
        resultManager.clearResult()
    }

    // ══════════════════════════════════════════════════════════════
    // Delete — move selected favorites to trash
    // ══════════════════════════════════════════════════════════════

    fun deleteSelected(paths: List<String>) {
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            try {
                val removed = FavoriteManager.removeFavorites(context, paths)

                // Scan parent dirs
                val parentDirs = paths.mapNotNull { java.io.File(it).parent }.distinct()
                if (parentDirs.isNotEmpty()) {
                    MediaStoreScanner.scanPaths(context, parentDirs)
                }

                log.d("deleteSelected: paths=${paths.size}, removed=$removed")
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletedCount = removed
                    )
                }
            } catch (e: Exception) {
                log.e("deleteSelected: failed", e)
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = e.message ?: "Lỗi khi xóa yêu thích"
                    )
                }
            }
        }
    }

    // Keep old method for backward compat (if any caller uses it)
    fun deleteFavorite(paths: List<String>) {
        deleteSelected(paths)
    }

    // ══════════════════════════════════════════════════════════════
    // Reorder — drag & drop reorder
    // ══════════════════════════════════════════════════════════════

    fun reorderFavorites(orderedPaths: List<String>) {
        viewModelScope.launch {
            FavoriteManager.reorderFavorites(context, orderedPaths)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearDeletedCount() {
        _uiState.update { it.copy(deletedCount = 0) }
    }
}
