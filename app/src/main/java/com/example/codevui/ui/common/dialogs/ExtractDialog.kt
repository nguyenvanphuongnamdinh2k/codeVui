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
 * Reusable extract dialog for specifying destination folder name
 * Used when extracting archives
 */
@Composable
fun ExtractDialog(
    defaultName: String,
    itemCount: Int = 1,
    parentPath: String? = null,  // Parent folder path để check conflict
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Auto-resolve conflict cho default name
    val resolvedDefaultName = remember(defaultName, parentPath) {
        if (parentPath != null) {
            val destFolder = File(parentPath, defaultName)
            if (destFolder.exists()) {
                // Generate unique name với (1), (2), etc.
                var counter = 1
                var uniqueName = "$defaultName ($counter)"
                while (File(parentPath, uniqueName).exists()) {
                    counter++
                    uniqueName = "$defaultName ($counter)"
                }
                uniqueName
            } else {
                defaultName
            }
        } else {
            defaultName
        }
    }

    var folderName by remember(resolvedDefaultName) { mutableStateOf(resolvedDefaultName) }

    // Check if current folder name conflicts với existing folder
    val folderExists = remember(folderName, parentPath) {
        if (parentPath != null && folderName.isNotBlank()) {
            File(parentPath, folderName).exists()
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
                text = if (itemCount == 1) "Giải nén" else "Giải nén các mục",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Nhập tên thư mục đích:",
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
                        text = "Folder đã tồn tại - vui lòng chọn tên folder khác",
                        fontSize = 12.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    )
}
