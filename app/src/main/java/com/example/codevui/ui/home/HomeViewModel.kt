package com.example.codevui.ui.home

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileRepository
import com.example.codevui.model.CategoryItem
import com.example.codevui.model.FileType
import com.example.codevui.model.StorageItem
import com.example.codevui.util.formatStorageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStaticData()
    }

    /**
     * Gọi từ UI sau khi có storage permission
     */
    fun onPermissionGranted() {
        loadRecentFiles()
        loadStorageInfo()
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(isPermissionDenied = true) }
    }

    private fun loadStaticData() {
        _uiState.update {
            it.copy(
                categories = getCategories(),
                storageItems = getStorageItems(),
                utilityItems = getUtilityItems()
            )
        }
    }

    private fun loadRecentFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val files = repository.getRecentFiles(limit = 20)
            val newCount = files.count { file ->
                val oneDayAgo = (System.currentTimeMillis() / 1000) - (24 * 60 * 60)
                file.dateModified > oneDayAgo
            }

            _uiState.update {
                it.copy(
                    recentFiles = files,
                    newFileCount = newCount,
                    isLoading = false
                )
            }
        }
    }

    private fun loadStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = repository.getInternalStorageInfo()
            val used = formatStorageSize(info.usedBytes)
            val total = formatStorageSize(info.totalBytes)

            _uiState.update { state ->
                state.copy(
                    storageItems = state.storageItems.map { item ->
                        if (item.title == "Bộ nhớ trong") {
                            item.copy(used = used, total = total)
                        } else item
                    }
                )
            }
        }
    }

    private fun getCategories() = listOf(
        CategoryItem(Icons.Outlined.Photo, Color(0xFFE91E63), Color(0xFFFCE4EC), "Ảnh", FileType.IMAGE),
        CategoryItem(Icons.Outlined.Videocam, Color(0xFF2196F3), Color(0xFFE3F2FD), "Video", FileType.VIDEO),
        CategoryItem(Icons.Outlined.Audiotrack, Color(0xFFFF5722), Color(0xFFFBE9E7), "File âm thanh", FileType.AUDIO),
        CategoryItem(Icons.Outlined.InsertDriveFile, Color(0xFFFF9800), Color(0xFFFFF3E0), "Tài liệu", FileType.DOC),
        CategoryItem(Icons.Outlined.FileDownload, Color(0xFF009688), Color(0xFFE0F2F1), "Lượt tải về", FileType.DOWNLOAD),
        CategoryItem(Icons.Outlined.Adb, Color(0xFF4CAF50), Color(0xFFE8F5E9), "Các file cài đặt", FileType.APK),
    )

    private fun getStorageItems() = listOf(
        StorageItem(Icons.Outlined.PhoneAndroid, Color(0xFF666666), Color(0xFFF5F5F5), "Bộ nhớ trong"),
    )

    private fun getUtilityItems() = listOf(
        StorageItem(Icons.Outlined.DeleteOutline, Color(0xFF666666), Color(0xFFF5F5F5), "Thùng rác"),
        StorageItem(Icons.Outlined.CleaningServices, Color(0xFF666666), Color(0xFFF5F5F5), "Quản lý lưu trữ"),
    )
}
