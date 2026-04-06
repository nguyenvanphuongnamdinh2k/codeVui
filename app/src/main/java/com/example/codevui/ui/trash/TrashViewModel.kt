package com.example.codevui.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.TrashItem
import com.example.codevui.data.TrashManager
import com.example.codevui.ui.common.viewmodel.OperationResultManager
import com.example.codevui.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val log = Logger("TrashVM")

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val isLoading: Boolean = false,
    val isEmpty: Boolean = true,
    val totalCount: Int = 0,
    // Ngày còn lại tính từ item CŨ NHẤT (deleteTimeEpoch nhỏ nhất)
    val oldestItemDaysLeft: Int = 30,
    // Progress 0f..1f: số ngày đã qua / 30 của item cũ nhất
    val oldestItemProgress: Float = 0f
)

class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val trashManager = TrashManager(application)
    val resultManager = OperationResultManager()

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    val operationResult = resultManager.operationResult

    init {
        // Observe Room realtime — tự cập nhật khi data thay đổi
        trashManager.observeTrashItems()
            .onEach { items ->
                val oldest = items.minByOrNull { it.deleteTimeEpoch }
                val daysLeft = if (oldest != null) oldest.daysUntilExpiry else 30
                val progress = if (oldest != null) {
                    val elapsed = 30 - oldest.daysUntilExpiry
                    (elapsed / 30f).coerceIn(0f, 1f)
                } else 0f

                _uiState.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        isEmpty = items.isEmpty(),
                        totalCount = items.size,
                        oldestItemDaysLeft = daysLeft,
                        oldestItemProgress = progress
                    )
                }
                log.d("Trash observed: ${items.size} items, oldest daysLeft=$daysLeft")
            }
            .launchIn(viewModelScope)
    }

    fun moveToTrash(filePaths: List<String>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            log.d("Moving ${filePaths.size} items to trash")
            val (success, failed) = trashManager.moveToTrash(filePaths)
            log.d("moveToTrash result: success=$success, failed=$failed")
            if (success > 0) {
                resultManager.setResult(
                    destPath = "",
                    success = success,
                    failed = failed,
                    actionName = "Xóa"
                )
            }
            onComplete()
        }
    }

    fun restore(trashIds: List<String>) {
        viewModelScope.launch {
            log.d("Restoring ${trashIds.size} items")
            val (success, failed, destDir) = trashManager.restore(trashIds)
            log.d("restore result: success=$success, failed=$failed, destDir=$destDir")
            resultManager.setResult(
                destPath = destDir,
                success = success,
                failed = failed,
                actionName = "Khôi phục"
            )
        }
    }

    fun permanentlyDelete(trashIds: List<String>) {
        viewModelScope.launch {
            log.d("Permanently deleting ${trashIds.size} items")
            val (success, failed) = trashManager.permanentlyDelete(trashIds)
            log.d("permanentlyDelete result: success=$success, failed=$failed")
            resultManager.setResult(
                destPath = "",
                success = success,
                failed = failed,
                actionName = "Xóa vĩnh viễn"
            )
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            log.d("Emptying trash")
            val (success, failed) = trashManager.emptyTrash()
            resultManager.setResult(
                destPath = "",
                success = success,
                failed = failed,
                actionName = "Xóa tất cả"
            )
        }
    }
}
