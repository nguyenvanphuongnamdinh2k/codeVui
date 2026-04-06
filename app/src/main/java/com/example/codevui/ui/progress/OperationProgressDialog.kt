package com.example.codevui.ui.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.codevui.data.FileOperations.ProgressState

/**
 * Progress dialog kiểu Samsung My Files:
 *   - Title: "Đang sao chép...", "Đang di chuyển...", "Đang nén mục..."
 *   - LinearProgressIndicator (determinate khi biết total, indeterminate khi đang đếm)
 *   - Counter "1/8" bên trái, "4%" bên phải
 *   - Nút "Thoát" (cancel) và "Ẩn cửa sổ pop-up" (dismiss nhưng vẫn chạy)
 *
 * @param title         Tiêu đề operation ("Đang sao chép..." v.v.)
 * @param state         ProgressState từ FileOperations flow
 * @param onCancel      Callback khi bấm "Thoát" — caller nên cancel coroutine Job
 * @param onDismiss     Callback khi bấm "Ẩn cửa sổ pop-up" — ẩn dialog, job vẫn chạy
 */
@Composable
fun OperationProgressDialog(
    title: String,
    state: ProgressState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* không cho dismiss bằng back/tap ngoài */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // ── Title ──────────────────────────────────────────
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ── Tên file đang xử lý ───────────────────────────
                val currentFile = when (state) {
                    is ProgressState.Running -> state.currentFile
                    is ProgressState.Counting -> "Đang chuẩn bị..."
                    else -> ""
                }
                if (currentFile.isNotEmpty()) {
                    Text(
                        text = currentFile,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Progress Bar ──────────────────────────────────
                when (state) {
                    is ProgressState.Counting -> {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is ProgressState.Running -> {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── Counter + Phần trăm ───────────────────────────
                if (state is ProgressState.Running) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${state.done}/${state.total}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${state.percent}%",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Divider + Buttons ─────────────────────────────
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thoát — cancel operation
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Thoát",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Divider dọc giữa 2 nút
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Ẩn cửa sổ pop-up — dismiss dialog, job vẫn chạy
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Ẩn cửa sổ pop-up",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}