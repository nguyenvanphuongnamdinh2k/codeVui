package com.example.codevui.ui.common.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dialog hiển thị khi có conflict (trùng tên file/folder).
 * Dùng chung cho: Paste từ clipboard, Copy, Move.
 *
 * Cho user chọn: Thay thế, Đổi tên, hoặc Thoát.
 */
@Composable
fun ConflictDialog(
    conflictCount: Int,
    operationName: String = "thao tác",  // "sao chép", "di chuyển", "dán"
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onRename: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            // 3 buttons cùng hàng: Thay thế | Đổi tên | Thoát
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onReplace) {
                    Text("Thay thế", fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onRename) {
                    Text("Đổi tên", fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Thoát")
                }
            }
        },
        title = {
            Text(
                text = "Phát hiện trùng tên",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (conflictCount == 1) {
                        "Có 1 mục đã tồn tại trong thư mục đích."
                    } else {
                        "Có $conflictCount mục đã tồn tại trong thư mục đích."
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                Text(
                    text = "Bạn muốn làm gì?",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• Thay thế: Ghi đè các file trùng tên",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                    Text(
                        text = "• Đổi tên: Tự động đổi tên các file trùng",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                    Text(
                        text = "• Thoát: Hủy thao tác $operationName",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                }
            }
        }
    )
}
