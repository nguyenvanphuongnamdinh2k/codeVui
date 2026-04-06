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
 * Dialog để tạo file mới.
 * Nếu người dùng không nhập extension thì tự động thêm ".txt".
 */
@Composable
fun CreateFileDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit  // trả về tên file đã có extension
) {
    var fileName by remember { mutableStateOf("") }

    // Tên file hiệu quả: tự động thêm .txt nếu không có extension
    val effectiveName = remember(fileName) {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) ""
        else if (trimmed.contains('.')) trimmed
        else "$trimmed.txt"
    }

    val fileExists = remember(effectiveName, currentPath) {
        effectiveName.isNotEmpty() && File(currentPath, effectiveName).exists()
    }

    val isValid = effectiveName.isNotEmpty() && !fileExists

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(effectiveName) },
                enabled = isValid
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
            Text(text = "Tạo file mới", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Nhập tên file (ví dụ: notes.txt, readme.md):",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("tên file", color = Color(0xFFAAAAAA)) },
                    isError = fileExists
                )
                // Hiển thị tên hiệu quả nếu sẽ thêm .txt
                if (fileName.isNotBlank() && !fileName.trim().contains('.')) {
                    Text(
                        text = "Sẽ tạo: $effectiveName",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
                if (fileExists) {
                    Text(
                        text = "File đã tồn tại - vui lòng chọn tên khác",
                        fontSize = 12.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    )
}
