package com.example.codevui.ui.common.viewmodel

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.codevui.util.Logger

private val log = Logger("OpResultMgr")

/**
 * Data class cho operation result
 */
data class OperationResult(
    val destPath: String,
    val success: Int,
    val failed: Int,
    val actionName: String
)

/**
 * Manager để xử lý operation result và hiển thị snackbar
 * Tái sử dụng logic chung cho BrowseViewModel, ArchiveViewModel, etc.
 */
class OperationResultManager {

    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    /**
     * Set operation result
     */
    fun setResult(destPath: String, success: Int, failed: Int, actionName: String) {
        log.d("setResult: $actionName - success=$success, failed=$failed, dest=$destPath")
        _operationResult.value = OperationResult(destPath, success, failed, actionName)
    }

    /**
     * Clear operation result
     */
    fun clearResult() {
        _operationResult.value = null
    }

    /**
     * Format message cho snackbar
     */
    fun formatMessage(result: OperationResult): String {
        return when {
            result.failed == 0 -> "${result.actionName} ${result.success} mục thành công"
            result.success == 0 -> "${result.actionName} thất bại (${result.failed} mục lỗi)"
            else -> "${result.actionName} ${result.success} thành công, ${result.failed} lỗi"
        }
    }
}

/**
 * Composable helper để hiển thị snackbar cho operation result
 * Tự động handle LaunchedEffect và navigation action
 */
@Composable
fun OperationResultSnackbar(
    resultManager: OperationResultManager,
    snackbarHostState: SnackbarHostState,
    actionLabel: String = "Mở folder đích",
    onActionPerformed: (String) -> Unit = {}
) {
    val result by resultManager.operationResult.collectAsStateWithLifecycle()

    LaunchedEffect(result) {
        result?.let { r ->
            log.d( "=== Snackbar LaunchedEffect triggered ===")
            log.d( "Result: action=${r.actionName}, destPath='${r.destPath}', success=${r.success}, failed=${r.failed}")
            log.d( "Showing snackbar: ${resultManager.formatMessage(r)}")

            val message = resultManager.formatMessage(r)
            val snackbarResult = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long
            )

            log.d( "Snackbar result: $snackbarResult")

            if (snackbarResult == SnackbarResult.ActionPerformed) {
                log.d( "User clicked action button - navigating to: '${r.destPath}'")
                onActionPerformed(r.destPath)
                log.d( "onActionPerformed callback completed")
            } else {
                log.d( "Snackbar dismissed without action")
            }

            resultManager.clearResult()
            log.d( "Result cleared")
        }
    }
}
