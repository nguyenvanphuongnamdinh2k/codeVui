package com.example.codevui.ui.common.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Confirmation dialog for moving files to trash.
 * Shown when user clicks "Delete" on selected files.
 */
@Composable
fun MoveToTrashDialog(
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Thoát", fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onConfirm) {
                    Text(
                        text = "Chuyển vào Thùng rác",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        title = {
            Text(
                text = if (itemCount == 1) {
                    "Chuyển 1 mục vào Thùng rác?"
                } else {
                    "Chuyển $itemCount mục vào Thùng rác?"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = if (itemCount == 1) {
                    "Bạn có thể khôi phục mục này trong vòng 30 ngày."
                } else {
                    "Bạn có thể khôi phục các mục này trong vòng 30 ngày."
                },
                fontSize = 14.sp,
                color = Color(0xFF666666)
            )
        }
    )
}
