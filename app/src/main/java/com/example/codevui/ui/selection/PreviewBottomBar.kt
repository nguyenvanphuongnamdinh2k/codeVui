package com.example.codevui.ui.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PreviewBottomBar — Custom bottom bar cho Archive Preview Mode
 * Chỉ hiển thị: Di chuyển | Xóa | Giải nén | Thoát
 */
@Composable
fun PreviewBottomBar(
    modifier: Modifier = Modifier,
    onMove: () -> Unit = {},
    onDelete: () -> Unit = {},
    onExtract: () -> Unit = {},
    onExit: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shadowElevation = 8.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PreviewBottomAction(
                icon = Icons.Outlined.DriveFileMove,
                label = "Di chuyển",
                onClick = onMove
            )
            PreviewBottomAction(
                icon = Icons.Outlined.Delete,
                label = "Xóa",
                onClick = onDelete,
                tint = Color(0xFFD32F2F)  // Red color for delete
            )
            PreviewBottomAction(
                icon = Icons.Outlined.FolderOpen,
                label = "Giải nén",
                onClick = onExtract
            )
            PreviewBottomAction(
                icon = Icons.Outlined.Close,
                label = "Thoát",
                onClick = onExit
            )
        }
    }
}

/**
 * PreviewBottomAction — Single action button in preview bottom bar
 */
@Composable
private fun PreviewBottomAction(
    icon: ImageVector,
    label: String,
    tint: Color = Color(0xFF444444),
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = tint
        )
    }
}
