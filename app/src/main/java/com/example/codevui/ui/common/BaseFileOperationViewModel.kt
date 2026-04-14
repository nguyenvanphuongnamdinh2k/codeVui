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
import com.example.codevui.model.ArchiveEntry
import com.example.codevui.service.FileOperationService
import com.example.codevui.service.OperationInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel hỗ trợ multi-operation qua FileOperationService.
 *
 * Thay đổi so với bản cũ:
 * - operationsMap thay cho single operationState (multi-operation)
 * - Mỗi operation có ID riêng, theo dõi độc lập
 * - Giữ backward-compatible: operationState/operationTitle vẫn trỏ tới operation mới nhất
 *
 * Usage:
 *   class BrowseViewModel(app, ssh) : BaseFileOperationViewModel(app) { ... }
 */
abstract class BaseFileOperationViewModel(
    application: Application
) : AndroidViewModel(application) {

    // ── Multi-operation state ─────────────────────────────────────────────────

    /**
     * Map of operationId → OperationInfo — theo dõi tất cả operation đang/đã chạy.
     * UI observe flow này để hiển thị progress cho từng operation.
     */
    private val _operationsMap = MutableStateFlow<Map<Int, OperationInfo>>(emptyMap())
    val operationsMap: StateFlow<Map<Int, OperationInfo>> = _operationsMap.asStateFlow()

    // ── Backward-compatible state (latest operation) ─────────────────────────

    /** State của operation mới nhất — backward-compatible cho code cũ */
    private val _operationState = MutableStateFlow<ProgressState?>(null)
    val operationState: StateFlow<ProgressState?> = _operationState.asStateFlow()

    private val _operationTitle = MutableStateFlow("")
    val operationTitle: StateFlow<String> = _operationTitle.asStateFlow()

    private val _isDialogHidden = MutableStateFlow(false)
    val isDialogHidden: StateFlow<Boolean> = _isDialogHidden.asStateFlow()

    /** Operation ID đang được hiển thị trong dialog (operation mới nhất) */
    private var activeOperationId: Int = -1

    // Destination path để hiển thị Snackbar sau khi hoàn thành
    protected var lastOperationDestPath: String? = null

    // ── Service binding ───────────────────────────────────────────────────────

    private var boundService: FileOperationService? = null
    private var isBound = false
    private var serviceObserveJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as FileOperationService.LocalBinder).getService()
            boundService = service

            // Cancel job cũ trước khi tạo job mới (tránh duplicate collect khi reconnect)
            serviceObserveJob?.cancel()
            serviceObserveJob = viewModelScope.launch {
                service.operationsMap.collect { opsMap ->
                    _operationsMap.value = opsMap

                    // Cập nhật backward-compatible state cho active operation
                    val activeOp = opsMap[activeOperationId]
                    if (activeOp != null) {
                        _operationState.value = activeOp.state
                        _operationTitle.value = activeOp.title

                        when (activeOp.state) {
                            is ProgressState.Done -> {
                                onOperationDone(
                                    activeOp.state.success,
                                    activeOp.state.failed,
                                    activeOp.actionName
                                )
                                // Clear operation từ service sau khi xử lý
                                service.clearOperation(activeOperationId)
                            }
                            is ProgressState.Error -> {
                                service.clearOperation(activeOperationId)
                            }
                            else -> {}
                        }
                    }

                    // Kiểm tra các operation khác đã Done (multi-op)
                    for ((opId, info) in opsMap) {
                        if (opId == activeOperationId) continue
                        when (info.state) {
                            is ProgressState.Done -> {
                                onOperationDone(info.state.success, info.state.failed, info.actionName)
                                service.clearOperation(opId)
                            }
                            is ProgressState.Error -> {
                                service.clearOperation(opId)
                            }
                            else -> {}
                        }
                    }

                    // Nếu không còn operation nào → unbind
                    if (opsMap.isEmpty() && !service.isRunning()) {
                        unbindFromService()
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            boundService = null
            isBound = false
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Copy files to destination. Trả về operationId.
     */
    fun copyFiles(sourcePaths: List<String>, destDir: String, resolvedDestPath: String? = null): Int {
        _isDialogHidden.value = false
        lastOperationDestPath = resolvedDestPath ?: destDir
        val opId = FileOperationService.startCopy(getApplication(), sourcePaths, destDir)
        activeOperationId = opId
        bindToService()
        return opId
    }

    /**
     * Move files to destination. Trả về operationId.
     */
    fun moveFiles(sourcePaths: List<String>, destDir: String, resolvedDestPath: String? = null): Int {
        _isDialogHidden.value = false
        lastOperationDestPath = resolvedDestPath ?: destDir
        val opId = FileOperationService.startMove(getApplication(), sourcePaths, destDir)
        activeOperationId = opId
        bindToService()
        return opId
    }

    /**
     * Compress files. Trả về operationId.
     */
    fun compressFiles(sourcePaths: List<String>, customName: String? = null, resolvedDestPath: String? = null): Int {
        _isDialogHidden.value = false
        lastOperationDestPath = resolvedDestPath ?: if (sourcePaths.isNotEmpty()) {
            java.io.File(sourcePaths.first()).parent
        } else null
        val opId = FileOperationService.startCompress(getApplication(), sourcePaths, customName)
        activeOperationId = opId
        bindToService()
        return opId
    }

    /**
     * Extract archive entries. Trả về operationId.
     * Chạy qua ForegroundService — có progress bar + notification + WakeLock.
     */
    fun extractFiles(
        archivePath: String,
        entryPaths: List<String>,
        destPath: String,
        allEntries: List<ArchiveEntry>,
        password: String? = null
    ): Int {
        _isDialogHidden.value = false
        lastOperationDestPath = destPath
        val opId = FileOperationService.startExtract(
            getApplication(), archivePath, entryPaths, destPath, allEntries, password
        )
        activeOperationId = opId
        bindToService()
        return opId
    }

    /**
     * Move files to trash (delete). Trả về operationId.
     * Chạy qua ForegroundService — có progress bar + notification + WakeLock.
     */
    fun trashFiles(sourcePaths: List<String>): Int {
        _isDialogHidden.value = false
        lastOperationDestPath = sourcePaths.firstOrNull()
        val opId = FileOperationService.startTrash(getApplication(), sourcePaths)
        activeOperationId = opId
        bindToService()
        return opId
    }

    fun hideOperationDialog() { _isDialogHidden.value = true }

    fun showOperationDialog() { _isDialogHidden.value = false }

    fun dismissOperationResult() {
        _operationState.value = null
        _isDialogHidden.value = false
    }

    fun cancelOperation() {
        if (activeOperationId > 0) {
            boundService?.cancelOperation(activeOperationId)
        }
        _operationState.value = null
        _isDialogHidden.value = false
    }

    /** Cancel operation cụ thể theo ID */
    fun cancelOperation(opId: Int) {
        boundService?.cancelOperation(opId)
        if (opId == activeOperationId) {
            _operationState.value = null
            _isDialogHidden.value = false
        }
    }

    fun isOperationRunning(): Boolean {
        val s = _operationState.value
        return s is ProgressState.Running || s is ProgressState.Counting
    }

    /** Kiểm tra xem có bất kỳ operation nào đang chạy */
    fun isAnyOperationRunning(): Boolean {
        return _operationsMap.value.any { (_, info) ->
            info.state is ProgressState.Running || info.state is ProgressState.Counting
        }
    }

    /** Kiểm tra xem có thể bắt đầu operation mới */
    fun canStartOperation(): Boolean {
        return boundService?.canStartOperation() ?: true
    }

    // ── Override trong subclass nếu cần ─────────────────────────────────────

    /**
     * Gọi sau khi operation Done — subclass override để reload data.
     */
    protected open fun onOperationDone(success: Int, failed: Int, actionName: String) {}

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun bindToService() {
        if (isBound) return
        val intent = Intent(getApplication(), FileOperationService::class.java)
        isBound = getApplication<Application>().bindService(
            intent, serviceConnection, Context.BIND_AUTO_CREATE
        )
    }

    private fun unbindFromService() {
        if (!isBound) return
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (_: Exception) {}
        boundService = null
        isBound = false
    }

    override fun onCleared() {
        super.onCleared()
        unbindFromService()
    }
}
