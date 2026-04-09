package com.example.codevui.ui.storage

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.DomainType
import com.example.codevui.data.FileRepository
import com.example.codevui.data.RecommendRepository
import com.example.codevui.data.StorageAnalysis
import com.example.codevui.data.StorageVolume
import com.example.codevui.data.StorageVolumeManager
import com.example.codevui.data.StorageUsageManager
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
    private val context = application

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
                // ── 1. Load all mounted volumes ─────────────────────────────
                StorageVolumeManager.refresh(context)
                val mountedVolumes = StorageVolumeManager.getMountedVolumes(context)
                log.d("loadStorageData: found ${mountedVolumes.size} mounted volumes")

                // Build per-volume storage states
                val volumeStates = mountedVolumes.map { vol ->
                    VolumeStorageState(
                        domainType = vol.domainType,
                        displayName = vol.displayName,
                        totalBytes = vol.totalBytes,
                        usedBytes = vol.usedBytes,
                        freeBytes = vol.freeBytes,
                        usedPercent = vol.usedPercent,
                        isLoading = false
                    )
                }

                // ── 2. Get internal storage info ───────────────────────────
                val info = repository.getInternalStorageInfo()
                log.d("loadStorageData: getInternalStorageInfo done — totalBytes=${info.totalBytes} (${info.totalBytes / 1_000_000_000.0} GB), usedBytes=${info.usedBytes} (${info.usedBytes / 1_000_000_000.0} GB), freeBytes=${info.freeBytes} (${info.freeBytes / 1_000_000_000.0} GB)")

                // ── 3. Get file analysis for internal storage ──────────────
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

                // System = total - used - other
                // Dùng raw partition size (không correction) để tránh systemBytes âm khi correction tăng totalBytes
                val rawTotalBytes = repository.getRawInternalStorageTotalBytes()
                val systemBytes = (rawTotalBytes - info.usedBytes - otherBytes).coerceAtLeast(0L)
                log.d("loadStorageData: systemBytes=$systemBytes (rawTotal=${rawTotalBytes} - used=${info.usedBytes} - other=$otherBytes)")

                _uiState.update {
                    it.copy(
                        volumes = volumeStates,
                        selectedVolumeDomainType = DomainType.INTERNAL_STORAGE,
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

    /**
     * Chọn 1 volume để xem chi tiết
     * Mirror MyFiles: khi tap vào SD/USB trong storage manager
     */
    fun selectVolume(domainType: Int) {
        log.d("selectVolume: domainType=$domainType")
        _uiState.update { it.copy(selectedVolumeDomainType = domainType) }

        // Load analysis cho volume mới nếu cần
        if (domainType == DomainType.INTERNAL_STORAGE) {
            // Internal đã load rồi, không cần load lại
            return
        }
        // SD/USB: load chi tiết trong background
        viewModelScope.launch(Dispatchers.IO) {
            loadVolumeAnalysis(domainType)
        }
    }

    /**
     * Load file analysis cho 1 volume cụ thể (SD/USB)
     */
    private suspend fun loadVolumeAnalysis(domainType: Int) {
        val rootPath = DomainType.getRootPath(domainType) ?: return
        log.d("loadVolumeAnalysis: domainType=$domainType, path=$rootPath")

        _uiState.update { state ->
            state.copy(
                volumes = state.volumes.map { vol ->
                    if (vol.domainType == domainType) vol.copy(isLoading = true) else vol
                }
            )
        }

        try {
            // Load analysis từ repository với path cụ thể
            val breakdown = repository.getVolumeFileBreakdown(rootPath)

            _uiState.update { state ->
                state.copy(
                    volumes = state.volumes.map { vol ->
                        if (vol.domainType == domainType) vol.copy(isLoading = false) else vol
                    },
                    fileBreakdowns = state.fileBreakdowns + (domainType to breakdown)
                )
            }
        } catch (e: Exception) {
            log.e("loadVolumeAnalysis: failed for domainType=$domainType", e)
            _uiState.update { state ->
                state.copy(
                    volumes = state.volumes.map { vol ->
                        if (vol.domainType == domainType) vol.copy(isLoading = false, error = e.message) else vol
                    }
                )
            }
        }
    }

    /** Refresh tất cả storage data */
    fun refresh() {
        StorageVolumeManager.refresh(context)
        loadStorageData()
        loadRecommendCards()
    }

    /** Điều hướng khi tap vào một category trong storage bar */
    fun onCategoryClick(title: String) {
        // Navigation handled by parent screen via callback
    }
}
