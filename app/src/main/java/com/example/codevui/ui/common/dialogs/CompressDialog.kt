package com.example.codevui.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Reusable compress dialog
 * Used for creating ZIP archives
 */
@Composable
fun CompressDialog(
    defaultName: String,
    itemCount: Int = 1,
    parentPath: String? = null,  // Parent folder path để check conflict
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Auto-resolve conflict cho default name
    val resolvedDefaultName = remember(defaultName, parentPath) {
        if (parentPath != null) {
            val zipFile = File(parentPath, "$defaultName.zip")
            if (zipFile.exists()) {
                // Generate unique name với (1), (2), etc.
                var counter = 1
                var uniqueName = "$defaultName ($counter)"
                while (File(parentPath, "$uniqueName.zip").exists()) {
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

    var zipName by remember(resolvedDefaultName) { mutableStateOf(resolvedDefaultName) }

    // Check if current zip file name conflicts với existing file
    val fileExists = remember(zipName, parentPath) {
        if (parentPath != null && zipName.isNotBlank()) {
            File(parentPath, "$zipName.zip").exists()
        } else {
            false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(zipName) },
                enabled = zipName.isNotBlank() && !fileExists
            ) {
                Text("Nén", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Thoát")
            }
        },
        title = {
            Text(
                text = if (itemCount == 1) "Nén mục" else "Nén các mục",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = zipName,
                        onValueChange = { zipName = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = fileExists
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Zip", fontSize = 14.sp, color = Color(0xFF666666))
                    Text(" ▼", fontSize = 12.sp, color = Color(0xFF666666))
                }

                // Error message nếu file đã tồn tại
                if (fileExists) {
                    Text(
                        text = "File đã tồn tại - vui lòng chọn tên file khác",
                        fontSize = 12.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    )
}
