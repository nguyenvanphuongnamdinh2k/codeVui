package com.example.codevui.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FavoriteManager
import com.example.codevui.data.MediaStoreScanner
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
 * 3. Delete selected → move to trash → scan → reload
 * 4. Reorder → cập nhật sortOrder
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val log = Logger("FavoritesViewModel")
    private val context = application

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
        validateOnStart()
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
    // Delete — move selected favorites to trash
    // ══════════════════════════════════════════════════════════════

    fun deleteFavorite(paths: List<String>) {
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            try {
                // Xóa khỏi favorites trước
                val removed = FavoriteManager.removeFavorites(context, paths)

                // Scan parent dirs
                val parentDirs = paths.mapNotNull { java.io.File(it).parent }.distinct()
                if (parentDirs.isNotEmpty()) {
                    MediaStoreScanner.scanPaths(context, parentDirs)
                }

                log.d("deleteFavorite: paths=${paths.size}, removed=$removed")
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deletedCount = removed
                    )
                }
            } catch (e: Exception) {
                log.e("deleteFavorite: failed", e)
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = e.message ?: "Lỗi khi xóa yêu thích"
                    )
                }
            }
        }
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
