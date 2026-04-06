package com.example.codevui.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Delete confirmation dialog
 * Shows warning before deleting files/folders
 */
@Composable
fun DeleteConfirmDialog(
    itemCount: Int,
    itemNames: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Xóa", fontWeight = FontWeight.Medium, color = Color(0xFFD32F2F))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
        title = {
            Text("Xác nhận xóa", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (itemCount == 1) {
                        "Bạn có chắc chắn muốn xóa mục này?"
                    } else {
                        "Bạn có chắc chắn muốn xóa $itemCount mục?"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )

                if (itemNames.isNotEmpty()) {
                    Text(
                        text = itemNames.take(3).joinToString("\n") { "• $it" } +
                                if (itemNames.size > 3) "\n• ..." else "",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                }

                Text(
                    text = "Thao tác này không thể hoàn tác.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFD32F2F)
                )
            }
        }
    )
}
