package com.example.codevui.ui.browse.columnview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.example.codevui.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.components.FileThumbnail
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.util.formatDateFull
import com.example.codevui.util.formatFileSize

/**
 * ColumnPanel — 1 cột trong Column View
 * Chỉ active column (column cuối cùng) mới được phép select.
 * Các column khác chỉ dùng để navigate.
 */
@Composable
fun ColumnPanel(
    column: ColumnData,
    selection: SelectionState,
    columnWidth: Dp = 300.dp,
    isActiveColumn: Boolean = false,  // Chỉ column cuối cùng = true
    favoritePaths: Set<String> = emptySet(),
    onFolderClick: (FolderItem) -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Chỉ hiển thị selection mode nếu:
    // 1. Global selection mode đang bật
    // 2. VÀ đây là active column (column cuối cùng)
    val isSelectionModeHere = selection.isSelectionMode && isActiveColumn

    Column(
        modifier = modifier
            .width(columnWidth)
            .fillMaxHeight()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Folders
            itemsIndexed(
                items = column.folders,
                key = { _, f -> "folder:${f.path}" }
            ) { index, folder ->
                val id = "folder:${folder.path}"
                val isNavigated = folder.path == column.selectedItemPath
                ColumnFolderRow(
                    folder = folder,
                    isNavigated = isNavigated,
                    isSelectionMode = isSelectionModeHere,
                    isSelected = selection.isSelected(id),
                    isFavorite = favoritePaths.contains(folder.path),
                    showDivider = index < column.folders.lastIndex || column.files.isNotEmpty(),
                    onClick = {
                        // Chỉ cho phép toggle selection nếu đây là active column
                        if (selection.isSelectionMode && isActiveColumn) {
                            selection.toggle(id)
                        } else if (!selection.isSelectionMode) {
                            onFolderClick(folder)
                        }
                        // Nếu selection mode đang bật nhưng không phải active column → ignore click
                    },
                    onLongClick = {
                        // Chỉ cho phép enter selection mode nếu đây là active column
                        // Truyền column.path làm contextKey để khoá scope vào cột này
                        if (!selection.isSelectionMode && isActiveColumn) {
                            selection.enterSelectionMode(id, contextKey = column.path)
                        }
                        // Nếu không phải active column → ignore long click
                    },
                    onToggleSelect = {
                        if (isActiveColumn) selection.toggle(id)
                    }
                )
            }

            // Files
            itemsIndexed(
                items = column.files,
                key = { _, f -> "file:${f.path}" }
            ) { index, file ->
                val id = "file:${file.path}"
                ColumnFileRow(
                    file = file,
                    isSelectionMode = isSelectionModeHere,
                    isSelected = selection.isSelected(id),
                    isFavorite = favoritePaths.contains(file.path),
                    showDivider = index < column.files.lastIndex,
                    onClick = {
                        // Chỉ cho phép toggle selection nếu đây là active column
                        if (selection.isSelectionMode && isActiveColumn) {
                            selection.toggle(id)
                        } else if (!selection.isSelectionMode) {
                            onFileClick(file)
                        }
                        // Nếu selection mode đang bật nhưng không phải active column → ignore click
                    },
                    onLongClick = {
                        // Chỉ cho phép enter selection mode nếu đây là active column
                        // Truyền column.path làm contextKey để khoá scope vào cột này
                        if (!selection.isSelectionMode && isActiveColumn) {
                            selection.enterSelectionMode(id, contextKey = column.path)
                        }
                        // Nếu không phải active column → ignore long click
                    },
                    onToggleSelect = {
                        if (isActiveColumn) selection.toggle(id)
                    }
                )
            }

            // Empty
            if (column.folders.isEmpty() && column.files.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Trống", color = Color(0xFF999999), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnFolderRow(
    folder: FolderItem,
    isNavigated: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isFavorite: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val bgColor = when {
        isSelected -> Color(0xFFF0F6FF)
        isNavigated -> Color(0xFFE8E8E8)
        else -> Color.Transparent
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .background(bgColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                SelectionCheckbox(isSelected = isSelected, onClick = onToggleSelect)
                Spacer(Modifier.width(8.dp))
            }
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Text("📁", fontSize = 24.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontSize = 13.sp,
                    fontWeight = if (isNavigated) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatDateFull(folder.dateModified), fontSize = 10.sp, color = Color(0xFF999999))
            }
            Text("${folder.itemCount} mục", fontSize = 11.sp, color = Color(0xFF999999))
            if (isFavorite) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(id = R.drawable.favorite_icon),
                    contentDescription = "Yêu thích",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = if (isSelectionMode) 92.dp else 58.dp,
                    end = 12.dp
                ),
                thickness = 0.5.dp,
                color = Color(0xFFF0F0F0)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnFileRow(
    file: RecentFile,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isFavorite: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .background(if (isSelected) Color(0xFFF0F6FF) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                SelectionCheckbox(isSelected = isSelected, onClick = onToggleSelect)
                Spacer(Modifier.width(8.dp))
            }
            FileThumbnail(file = file, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontSize = 13.sp,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatDateFull(file.dateModified), fontSize = 10.sp, color = Color(0xFF999999))
            }
            Text(formatFileSize(file.size), fontSize = 11.sp, color = Color(0xFF999999))
            if (isFavorite) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(id = R.drawable.favorite_icon),
                    contentDescription = "Yêu thích",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = if (isSelectionMode) 92.dp else 58.dp,
                    end = 12.dp
                ),
                thickness = 0.5.dp,
                color = Color(0xFFF0F0F0)
            )
        }
    }
}