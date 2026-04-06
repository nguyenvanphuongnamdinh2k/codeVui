package com.example.codevui.ui.common

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.service.FileOperationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel chứa toàn bộ logic bind FileOperationService.
 * Các ViewModel cần copy/move/zip chỉ cần extend class này.
 *
 * Usage:
 *   class BrowseViewModel(app, ssh) : BaseFileOperationViewModel(app) { ... }
 *   class RecentFilesViewModel(app, ssh) : BaseFileOperationViewModel(app) { ... }
 */
abstract class BaseFileOperationViewModel(
    application: Application
) : AndroidViewModel(application) {

    // ── State expose ra ngoài ─────────────────────────────────────────────────

    private val _operationState = MutableStateFlow<ProgressState?>(null)
    val operationState: StateFlow<ProgressState?> = _operationState.asStateFlow()

    private val _operationTitle = MutableStateFlow("")
    val operationTitle: StateFlow<String> = _operationTitle.asStateFlow()

    private val _isDialogHidden = MutableStateFlow(false)
    val isDialogHidden: StateFlow<Boolean> = _isDialogHidden.asStateFlow()

    // Destination path để hiển thị Snackbar sau khi hoàn thành
    protected var lastOperationDestPath: String? = null

    // ── Service binding ───────────────────────────────────────────────────────

    private var boundService: FileOperationService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as FileOperationService.LocalBinder).getService()
            boundService = service

            viewModelScope.launch {
                service.operationTitle.collect { _operationTitle.value = it }
            }
            viewModelScope.launch {
                service.operationState.collect { state ->
                    _operationState.value = state
                    when (state) {
                        is ProgressState.Done -> {
                            // Callback với thông tin kết quả
                            val actionName = extractActionName(_operationTitle.value)
                            onOperationDone(state.success, state.failed, actionName)
                        }
                        is ProgressState.Error -> {
                            // Không hiển thị Toast, để UI xử lý
                        }
                        null -> unbindFromService()
                        else -> {}
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            boundService = null
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Copy files to destination.
     * @param resolvedDestPath Optional: resolved file path when rename conflict was pre-resolved
     *                          (used for snackbar navigation to the actual renamed file)
     */
    fun copyFiles(sourcePaths: List<String>, destDir: String, resolvedDestPath: String? = null) {
        _isDialogHidden.value = false
        lastOperationDestPath = resolvedDestPath ?: destDir
        FileOperationService.startCopy(getApplication(), sourcePaths, destDir)
        bindToService()
    }

    /**
     * Move files to destination.
     * @param resolvedDestPath Optional: resolved file path when rename conflict was pre-resolved
     *                          (used for snackbar navigation to the actual renamed file)
     */
    fun moveFiles(sourcePaths: List<String>, destDir: String, resolvedDestPath: String? = null) {
        _isDialogHidden.value = false
        lastOperationDestPath = resolvedDestPath ?: destDir
        FileOperationService.startMove(getApplication(), sourcePaths, destDir)
        bindToService()
    }

    fun compressFiles(sourcePaths: List<String>, customName: String? = null, resolvedDestPath: String? = null) {
        _isDialogHidden.value = false
        // Compress lưu vào thư mục của file đầu tiên
        lastOperationDestPath = resolvedDestPath ?: if (sourcePaths.isNotEmpty()) {
            java.io.File(sourcePaths.first()).parent
        } else null
        FileOperationService.startCompress(getApplication(), sourcePaths, customName)
        bindToService()
    }

    fun hideOperationDialog() { _isDialogHidden.value = true }

    fun showOperationDialog() { _isDialogHidden.value = false }

    fun cancelOperation() {
        boundService?.cancelOperation()
        unbindFromService()
        _operationState.value = null
        _isDialogHidden.value = false
    }

    fun dismissOperationResult() {
        _operationState.value = null
        _isDialogHidden.value = false
    }

    fun isOperationRunning(): Boolean {
        val s = _operationState.value
        return s is ProgressState.Running || s is ProgressState.Counting
    }

    // ── Override trong subclass nếu cần ──────────────────────────────────────

    /**
     * Gọi sau khi operation Done — subclass override để reload data.
     * @param success số mục thành công
     * @param failed số mục thất bại
     * @param actionName tên thao tác (Sao chép, Di chuyển, Nén)
     */
    protected open fun onOperationDone(success: Int, failed: Int, actionName: String) {}

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun bindToService() {
        val intent = Intent(getApplication(), FileOperationService::class.java)
        getApplication<Application>().bindService(
            intent, serviceConnection, Context.BIND_AUTO_CREATE
        )
    }

    private fun unbindFromService() {
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: Exception) {}
        boundService = null
    }

    override fun onCleared() {
        super.onCleared()
        if (boundService != null) unbindFromService()
    }

    private fun extractActionName(title: String): String {
        return when {
            title.contains("sao chép", ignoreCase = true) -> "Sao chép"
            title.contains("di chuyển", ignoreCase = true) -> "Di chuyển"
            title.contains("nén", ignoreCase = true) -> "Nén"
            else -> "Thao tác"
        }
    }
}