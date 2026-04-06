package com.example.codevui.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.codevui.model.ArchiveEntry
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.ui.selection.TriState
import com.example.codevui.ui.thumbnail.ThumbnailData
import com.example.codevui.util.formatDateFull
import com.example.codevui.util.formatFileSize

/**
 * ArchiveEntryItem — hiển thị 1 item bên trong file nén
 * Folder → icon folder + name + item count style
 * File → icon theo extension + name + size
 * Reuse pattern giống FileListItem / FolderListItem
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArchiveEntryItem(
    entry: ArchiveEntry,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    folderTriState: TriState = TriState.NONE,  // tri-state for folder selection (partial/all)
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    isPreviewMode: Boolean = false,  // Preview mode cho phép click folder để navigate
    archivePath: String = "",  // Path to archive file for thumbnail extraction
    password: String? = null  // Password for encrypted archives
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        // Preview mode: click folder để navigate, click file/item để toggle select
                        if (isPreviewMode && entry.isDirectory) {
                            onClick()
                        } else if (isSelectionMode || (isPreviewMode && !entry.isDirectory)) {
                            // Selection mode → toggle select
                            // Preview mode + file → toggle select
                            onToggleSelect()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = {
                        if (!isSelectionMode) onLongClick()
                    }
                )
                .background(
                    when {
                        entry.isDirectory && folderTriState != TriState.NONE -> Color(0xFFF0F6FF)
                        isSelected -> Color(0xFFF0F6FF)
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox: luôn hiển thị trong preview mode, hoặc khi đang ở selection mode
            if (isSelectionMode || isPreviewMode) {
                if (entry.isDirectory) {
                    // Folder uses tri-state checkbox (partial / all / none)
                    SelectionCheckbox(
                        isSelected = folderTriState == TriState.ALL,
                        onClick = onToggleSelect,
                        triState = folderTriState
                    )
                } else {
                    // File uses simple boolean checkbox
                    SelectionCheckbox(
                        isSelected = isSelected,
                        onClick = onToggleSelect
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            // Icon
            if (entry.isDirectory) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📁", fontSize = 32.sp)
                }
            } else {
                ArchiveFileIcon(
                    fileName = entry.name,
                    entryPath = entry.path,
                    archivePath = archivePath,
                    password = password,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Name + info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                if (entry.isDirectory) {
                    if (entry.lastModified > 0) {
                        Text(
                            text = formatDateFull(entry.lastModified),
                            fontSize = 13.sp,
                            color = Color(0xFF999999)
                        )
                    }
                } else {
                    Row {
                        if (entry.lastModified > 0) {
                            Text(
                                text = formatDateFull(entry.lastModified),
                                fontSize = 13.sp,
                                color = Color(0xFF999999)
                            )
                            Spacer(Modifier.weight(1f))
                        }
                        Text(
                            text = formatFileSize(entry.size),
                            fontSize = 13.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
            }
        }
        if (showDivider) {
            // Preview mode luôn hiển thị checkbox nên dùng selection padding
            val dividerStart = if (isSelectionMode || isPreviewMode) 124.dp else 84.dp
            HorizontalDivider(
                modifier = Modifier.padding(start = dividerStart, end = 20.dp),
                thickness = 0.5.dp,
                color = Color(0xFFF0F0F0)
            )
        }
    }
}

/**
 * ArchiveFileIcon — icon theo extension của file bên trong archive
 * Load thumbnail cho image files, hiển thị icon cho file types khác
 */
@Composable
private fun ArchiveFileIcon(
    fileName: String,
    entryPath: String,
    archivePath: String,
    password: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = fileName.substringAfterLast('.', "").lowercase()

    // Check if this is an image file that should load thumbnail
    val isImage = ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    if (isImage && archivePath.isNotEmpty()) {
        // Load thumbnail for image files
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            val thumbnailData = ThumbnailData.Archive(
                uri = Uri.EMPTY,  // Archive không cần Uri
                path = archivePath,
                entryPath = entryPath,
                password = password
            )

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailData)
                    .crossfade(true)
                    .memoryCacheKey("archive:$archivePath::$entryPath")
                    .diskCacheKey("archive:$archivePath::$entryPath")
                    .build(),
                contentDescription = fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    // Show icon placeholder while loading
                    Icon(
                        imageVector = Icons.Outlined.Photo,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(24.dp)
                    )
                },
                error = {
                    // Show icon on error
                    Icon(
                        imageVector = Icons.Outlined.Photo,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    } else {
        // Show icon for non-image files
        val (icon, tint) = when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" ->
                Icons.Outlined.Photo to Color(0xFFE91E63)
            "mp4", "avi", "mkv", "mov", "wmv" ->
                Icons.Outlined.Videocam to Color(0xFF2196F3)
            "mp3", "wav", "flac", "aac", "ogg", "m4a" ->
                Icons.Outlined.Audiotrack to Color(0xFFFF5722)
            "pdf" ->
                Icons.Outlined.PictureAsPdf to Color(0xFFE53935)
            "doc", "docx" ->
                Icons.Outlined.InsertDriveFile to Color(0xFF1A73E8)
            "xls", "xlsx" ->
                Icons.Outlined.InsertDriveFile to Color(0xFF0F9D58)
            "txt", "log", "md" ->
                Icons.Outlined.TextSnippet to Color(0xFF666666)
            "apk" ->
                Icons.Outlined.Adb to Color(0xFF4CAF50)
            "zip", "rar", "7z", "tar", "gz" ->
                Icons.Outlined.FolderZip to Color(0xFFFF9800)
            else ->
                Icons.Outlined.InsertDriveFile to Color(0xFF9E9E9E)
        }

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}