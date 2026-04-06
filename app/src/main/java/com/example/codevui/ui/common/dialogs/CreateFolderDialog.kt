package com.example.codevui.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Dialog để tạo folder mới
 * Tự động tìm tên unique nếu folder đã tồn tại
 */
@Composable
fun CreateFolderDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Auto-generate unique folder name
    val defaultName = remember(currentPath) {
        var counter = 1
        var folderName = "Folder $counter"
        while (File(currentPath, folderName).exists()) {
            counter++
            folderName = "Folder $counter"
        }
        folderName
    }

    var folderName by remember(defaultName) { mutableStateOf(defaultName) }

    // Check if folder exists
    val folderExists = remember(folderName, currentPath) {
        if (folderName.isNotBlank()) {
            File(currentPath, folderName).exists()
        } else {
            false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank() && !folderExists
            ) {
                Text("Đồng ý", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
        title = {
            Text(
                text = "Tạo thư mục mới",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Nhập tên thư mục:",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = folderExists
                )

                // Error message nếu folder đã tồn tại
                if (folderExists) {
                    Text(
                        text = "Thư mục đã tồn tại - vui lòng chọn tên khác",
                        fontSize = 12.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    )
}
