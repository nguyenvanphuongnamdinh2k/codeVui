package com.example.codevui.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.codevui.util.formatDateFull
import com.example.codevui.util.formatFileSize

/**
 * Reusable details dialog
 * Shows file/folder information
 */
@Composable
fun DetailsDialog(
    paths: List<String>,
    onDismiss: () -> Unit
) {
    val file = java.io.File(paths.first())
    val totalSize = remember(paths) {
        paths.sumOf { path ->
            val f = java.io.File(path)
            if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            else f.length()
        }
    }
    val totalItems = if (paths.size == 1 && file.isDirectory) {
        file.listFiles()?.size ?: 0
    } else paths.size

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        },
        title = {
            Text("Chi tiết", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (paths.size == 1) DetailRow("Tên", file.name)
                DetailRow("Kích thước", formatFileSize(totalSize))
                DetailRow("Đường dẫn", file.parent ?: "")
                if (paths.size == 1) {
                    DetailRow("Sửa đổi lần cuối", formatDateFull(file.lastModified() / 1000))
                    if (file.isDirectory) DetailRow("Số mục", "$totalItems mục")
                } else {
                    DetailRow("Đã chọn", "${paths.size} mục")
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFF888888),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color(0xFF1A1A1A)
        )
    }
}
