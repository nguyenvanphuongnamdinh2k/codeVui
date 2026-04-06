package com.example.codevui.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.codevui.Screen

/**
 * DrawerPane — sidebar navigation cho landscape
 * isExpanded = true → full width với label
 * isExpanded = false → narrow rail chỉ icon
 */
@Composable
fun DrawerPane(
    currentScreen: Screen,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    storageUsed: String = "",
    storageTotal: String = "",
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .then(if (isExpanded) Modifier.width(260.dp) else Modifier.width(64.dp))
            .fillMaxHeight()
            .background(Color(0xFFFAFAFA))
            .statusBarsPadding()
            .animateContentSize(),
        horizontalAlignment = if (isExpanded) Alignment.Start else Alignment.CenterHorizontally
    ) {
        // Header: hamburger toggle + settings
        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleExpand) {
                    Icon(Icons.Default.Menu, contentDescription = "Thu nhỏ menu", tint = Color(0xFF444444))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = Color(0xFF444444))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onToggleExpand) {
                    Icon(Icons.Default.Menu, contentDescription = "Mở rộng menu", tint = Color(0xFF444444))
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            DrawerItem(
                icon = Icons.Outlined.Schedule,
                label = "Gần đây",
                isExpanded = isExpanded,
                isSelected = currentScreen is Screen.RecentFiles,
                onClick = { onNavigate(Screen.RecentFiles()) }
            )
            DrawerItem(
                icon = Icons.Outlined.FileDownload,
                label = "Lượt tải về",
                isExpanded = isExpanded,
                isSelected = currentScreen is Screen.FileList &&
                        (currentScreen as? Screen.FileList)?.title == "Lượt tải về",
                onClick = {
                    onNavigate(Screen.FileList(
                        com.example.codevui.model.FileType.DOWNLOAD, "Lượt tải về"
                    ))
                }
            )
            DrawerItem(
                icon = Icons.Outlined.Star,
                label = "Yêu thích",
                isExpanded = isExpanded,
                isSelected = currentScreen is Screen.Favorites,
                onClick = { onNavigate(Screen.Favorites) }
            )

            DrawerDivider(isExpanded)

            DrawerItem(Icons.Outlined.Photo, "Ảnh", Color(0xFFE91E63), isExpanded,
                currentScreen is Screen.FileList && (currentScreen as? Screen.FileList)?.title == "Ảnh"
            ) { onNavigate(Screen.FileList(com.example.codevui.model.FileType.IMAGE, "Ảnh")) }

            DrawerItem(Icons.Outlined.Audiotrack, "Âm thanh", Color(0xFFFF5722), isExpanded,
                currentScreen is Screen.FileList && (currentScreen as? Screen.FileList)?.title == "Âm thanh"
            ) { onNavigate(Screen.FileList(com.example.codevui.model.FileType.AUDIO, "Âm thanh")) }

            DrawerItem(Icons.Outlined.Videocam, "Video", Color(0xFF2196F3), isExpanded,
                currentScreen is Screen.FileList && (currentScreen as? Screen.FileList)?.title == "Video"
            ) { onNavigate(Screen.FileList(com.example.codevui.model.FileType.VIDEO, "Video")) }

            DrawerItem(Icons.Outlined.Adb, "File cài đặt", Color(0xFF4CAF50), isExpanded,
                currentScreen is Screen.FileList && (currentScreen as? Screen.FileList)?.title == "Các file cài đặt"
            ) { onNavigate(Screen.FileList(com.example.codevui.model.FileType.APK, "Các file cài đặt")) }

            DrawerItem(Icons.AutoMirrored.Outlined.InsertDriveFile, "Tài liệu", Color(0xFFFF9800), isExpanded,
                currentScreen is Screen.FileList && (currentScreen as? Screen.FileList)?.title == "Tài liệu"
            ) { onNavigate(Screen.FileList(com.example.codevui.model.FileType.DOC, "Tài liệu")) }

            DrawerDivider(isExpanded)

            // Internal storage
            DrawerItem(
                icon = Icons.Outlined.PhoneAndroid,
                label = "Bộ nhớ trong",
                isExpanded = isExpanded,
                isSelected = currentScreen is Screen.Browse,
                subtitle = if (isExpanded && storageUsed.isNotEmpty()) "$storageUsed / $storageTotal" else null,
                onClick = { onNavigate(Screen.Browse()) }
            )
            DrawerItem(
                icon = Icons.Outlined.CleaningServices,
                label = "Quản lý lưu trữ",
                isExpanded = isExpanded,
                isSelected = currentScreen is Screen.StorageManager,
                onClick = { onNavigate(Screen.StorageManager) }
            )
            DrawerItem(
                icon = Icons.Outlined.DeleteOutline,
                label = "Thùng rác",
                isExpanded = isExpanded,
                isSelected = currentScreen is Screen.Trash,
                onClick = { onNavigate(Screen.Trash) }
            )
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color(0xFF666666),
    isExpanded: Boolean = true,
    isSelected: Boolean = false,
    subtitle: String? = null,
    onClick: () -> Unit = {}
) {
    if (isExpanded) {
        // Full item with label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Color(0xFFE8E8E8) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = Color(0xFF999999))
                }
            }
        }
    } else {
        // Collapsed: icon only, centered
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Color(0xFFE8E8E8) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DrawerDivider(isExpanded: Boolean) {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = if (isExpanded) 24.dp else 12.dp),
        thickness = 0.5.dp,
        color = Color(0xFFE0E0E0)
    )
    Spacer(Modifier.height(8.dp))
}