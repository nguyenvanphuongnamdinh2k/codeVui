package com.example.codevui.ui.storage

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileRepository
import com.example.codevui.data.RecommendRepository
import com.example.codevui.data.StorageAnalysis
import com.example.codevui.model.FileType
import com.example.codevui.model.StorageItem
import com.example.codevui.util.Logger
import com.example.codevui.util.formatStorageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StorageManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val log = Logger("StorageManagerViewModel")
    private val repository = FileRepository(application)
    private val recommendRepository = RecommendRepository(application)

    private val _uiState = MutableStateFlow(StorageManagerUiState())
    val uiState: StateFlow<StorageManagerUiState> = _uiState.asStateFlow()

    init {
        loadStorageData()
        loadRecommendCards()
    }

    fun loadStorageData() {
        log.d("loadStorageData: start")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val info = repository.getInternalStorageInfo()
                log.d("loadStorageData: getInternalStorageInfo done — total=${info.totalBytes}, used=${info.usedBytes}, free=${info.freeBytes}")

                val analysis = repository.getStorageAnalysis()
                log.d("loadStorageData: getStorageAnalysis done — video=${analysis.videoBytes}, image=${analysis.imageBytes}, audio=${analysis.audioBytes}, archive=${analysis.archiveBytes}, apk=${analysis.apkBytes}, doc=${analysis.docBytes}, apps=${analysis.appsBytes}, trash=${analysis.trashBytes}")

                val usedPercent = if (info.totalBytes > 0) {
                    ((info.usedBytes.toDouble() / info.totalBytes) * 100).toInt().coerceIn(0, 100)
                } else 0

                // Tính residual: other = used - apps - mediaStore - trash
                val mediaStoreBytes = analysis.videoBytes + analysis.imageBytes +
                    analysis.audioBytes + analysis.archiveBytes + analysis.apkBytes + analysis.docBytes
                val otherBytes = (info.usedBytes - analysis.appsBytes - mediaStoreBytes - analysis.trashBytes)
                    .coerceAtLeast(0L)

                log.d("loadStorageData: otherBytes=$otherBytes (used=${info.usedBytes} - apps=${analysis.appsBytes} - mediaStore=$mediaStoreBytes - trash=${analysis.trashBytes})")

                // System = total - used - other (free space từ StatFs)
                val systemBytes = (info.totalBytes - info.usedBytes - otherBytes).coerceAtLeast(0L)
                log.d("loadStorageData: systemBytes=$systemBytes (total=${info.totalBytes} - used=${info.usedBytes} - other=$otherBytes)")

                _uiState.update {
                    it.copy(
                        storageInfo = info,
                        usedPercent = usedPercent,
                        usedFormatted = formatStorageSize(info.usedBytes),
                        totalFormatted = formatStorageSize(info.totalBytes),
                        videoBytes = analysis.videoBytes,
                        imageBytes = analysis.imageBytes,
                        audioBytes = analysis.audioBytes,
                        archiveBytes = analysis.archiveBytes,
                        apkBytes = analysis.apkBytes,
                        docBytes = analysis.docBytes,
                        appsBytes = analysis.appsBytes,
                        systemBytes = systemBytes,
                        otherBytes = otherBytes,
                        trashBytes = analysis.trashBytes,
                        unusedAppsBytes = analysis.unusedAppsBytes,
                        duplicateBytes = analysis.duplicateBytes,
                        largeFilesBytes = analysis.largeFilesBytes,
                        oldScreenshotsBytes = analysis.oldScreenshotsBytes,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                log.d("loadStorageData: complete — usedPercent=$usedPercent%, otherBytes=$otherBytes, systemBytes=$systemBytes")
            } catch (e: Exception) {
                log.e("loadStorageData: failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Lỗi khi tải dữ liệu lưu trữ"
                    )
                }
            }
        }
    }

    /** Load recommend cards (MyFiles style) — chạy song song với storage data */
    fun loadRecommendCards() {
        log.d("loadRecommendCards: start")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecommend = true) }
            try {
                val cards = recommendRepository.getAllCards()
                log.d("loadRecommendCards: loaded ${cards.size} cards")
                _uiState.update {
                    it.copy(
                        isLoadingRecommend = false,
                        recommendCards = cards
                    )
                }
            } catch (e: Exception) {
                log.e("loadRecommendCards: failed", e)
                _uiState.update { it.copy(isLoadingRecommend = false) }
            }
        }
    }

    /** Điều hướng khi tap vào một category trong storage bar */
    fun onCategoryClick(title: String) {
        // Navigation handled by parent screen via callback
    }
}
