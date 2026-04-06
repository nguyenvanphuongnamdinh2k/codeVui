package com.example.codevui.ui.common.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.codevui.ui.common.dialogs.PasswordDialog

/**
 * State holder cho password dialog
 * Quản lý state và lifecycle của password dialog
 */
class PasswordDialogState {
    var showDialog by mutableStateOf(false)
        private set

    var archivePath by mutableStateOf("")
        private set

    var destPath by mutableStateOf("")
        private set

    var onConfirmCallback: ((String) -> Unit)? = null
        private set

    /**
     * Show password dialog
     * @param path Archive file path
     * @param destination Destination path (optional, for extraction)
     * @param onConfirm Callback khi user nhập password
     */
    fun show(
        path: String,
        destination: String = "",
        onConfirm: (String) -> Unit
    ) {
        archivePath = path
        destPath = destination
        onConfirmCallback = onConfirm
        showDialog = true
    }

    /**
     * Dismiss password dialog
     */
    fun dismiss() {
        showDialog = false
        archivePath = ""
        destPath = ""
        onConfirmCallback = null
    }

    /**
     * Confirm password
     */
    fun confirm(password: String) {
        onConfirmCallback?.invoke(password)
        dismiss()
    }
}

/**
 * Composable helper để setup password callback với ViewModel
 */
@Composable
fun rememberPasswordDialogState(): PasswordDialogState {
    return remember { PasswordDialogState() }
}

/**
 * Setup password callback cho ViewModel
 * Tự động cleanup khi composable dispose
 */
@Composable
fun <T> SetupPasswordCallback(
    viewModel: T,
    dialogState: PasswordDialogState,
    setCallback: T.(((String, String) -> Unit)?) -> Unit
) where T : Any {
    DisposableEffect(viewModel) {
        viewModel.setCallback { archivePath, destPath ->
            dialogState.show(
                path = archivePath,
                destination = destPath,
                onConfirm = { password ->
                    // Callback sẽ được handle bởi caller
                }
            )
        }
        onDispose {
            viewModel.setCallback(null)
        }
    }
}

/**
 * Render password dialog nếu cần
 */
@Composable
fun PasswordDialogHandler(
    state: PasswordDialogState,
    title: String = "Nhập mật khẩu",
    message: String = "File nén này được bảo vệ bằng mật khẩu"
) {
    if (state.showDialog) {
        PasswordDialog(
            onDismiss = { state.dismiss() },
            onConfirm = { password -> state.confirm(password) },
            title = title,
            message = message
        )
    }
}
