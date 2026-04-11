package com.example.codevui.ui.components

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.res.painterResource
import com.example.codevui.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.codevui.AppImageLoader
import com.example.codevui.model.FileType
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.ui.thumbnail.ThumbnailData
import com.example.codevui.util.formatDateFull
import com.example.codevui.util.formatFileSize

/**
 * FileListItem — Reusable file row
 * Supports: normal mode (click) + selection mode (checkbox + long press to enter)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: RecentFile,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isLandscape: Boolean = false,
    isFavorite: Boolean = false,
    isRenamed: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onToggleSelect: () -> Unit = {}
) {
    val verticalPadding = if (isLandscape) 6.dp else 12.dp
    val horizontalPadding = if (isLandscape) 12.dp else 20.dp
    val thumbnailSize = if (isLandscape) 40.dp else 56.dp
    val titleFontSize = if (isLandscape) 13.sp else 15.sp
    val metaFontSize = if (isLandscape) 11.sp else 13.sp
    val spacerWidth = if (isLandscape) 10.dp else 16.dp
    val innerSpacerHeight = if (isLandscape) 2.dp else 4.dp

    // Animate background khi vừa rename (xanh nhạt → transparent sau 2.5s)
    val animatedBg by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFFF0F6FF)
            isRenamed -> Color(0xFFE8F5E9)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 600),
        label = "renameHighlight"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onToggleSelect() else onClick()
                    },
                    onLongClick = {
                        if (!isSelectionMode) onLongClick()
                    }
                )
                .background(animatedBg)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox khi selection mode
            if (isSelectionMode) {
                SelectionCheckbox(
                    isSelected = isSelected,
                    onClick = onToggleSelect
                )
                Spacer(Modifier.width(if (isLandscape) 8.dp else 12.dp))
            }

            // Thumbnail / Icon
            FileThumbnail(
                file = file,
                modifier = Modifier.size(thumbnailSize)
            )

            Spacer(Modifier.width(spacerWidth))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(innerSpacerHeight))
                Row {
                    Text(
                        text = formatDateFull(file.dateModified),
                        fontSize = metaFontSize,
                        color = Color(0xFF999999)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatFileSize(file.size),
                        fontSize = metaFontSize,
                        color = Color(0xFF999999)
                    )
                }
            }

            // Favorite star (giống MyFiles file_list_item — hiện cả ở selection mode)
            if (isFavorite) {
                Spacer(Modifier.width(if (isLandscape) 8.dp else 12.dp))
                Icon(
                    painter = painterResource(id = R.drawable.favorite_icon),
                    contentDescription = "Yêu thích",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(if (isLandscape) 16.dp else 18.dp)
                )
            }
        }
        if (showDivider) {
            val dividerStart = when {
                isSelectionMode && isLandscape -> 92.dp
                isSelectionMode -> 132.dp
                isLandscape -> 62.dp
                else -> 92.dp
            }
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = dividerStart,
                    end = horizontalPadding
                ),
                thickness = 0.5.dp,
                color = Color(0xFFF0F0F0)
            )
        }
    }
}

/**
 * FileThumbnail — load thumbnail cho image, video, audio, APK
 * Dùng Coil với custom fetchers đã đăng ký trong AppImageLoader
 */
@Composable
fun FileThumbnail(
    file: RecentFile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Data truyền vào Coil — custom fetcher sẽ nhận đúng type
    val imageData: Any? = when (file.type) {
        FileType.IMAGE -> if (file.uri != Uri.EMPTY) file.uri else null

        FileType.VIDEO -> if (file.path.isNotEmpty()) ThumbnailData.Video(
            uri = file.uri,
            path = file.path
        ) else null

        FileType.AUDIO -> if (file.uri != Uri.EMPTY) ThumbnailData.Audio(
            uri = file.uri,
            path = file.path
        ) else null

        FileType.APK -> if (file.path.isNotEmpty()) ThumbnailData.Apk(
            uri = file.uri,
            path = file.path
        ) else null

        else -> null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center
    ) {
        if (imageData != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageData)
                    .crossfade(true)
                    .build(),
                imageLoader = AppImageLoader.get(context),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                // Fallback về icon nếu load thất bại
                error = fileTypeIconPainter(file.type),
                placeholder = null
            )
        } else {
            FileTypeIcon(file.type)
        }
    }
}

@Composable
private fun FileTypeIcon(type: FileType) {
    val (icon, tint) = fileTypeIconInfo(type)
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(28.dp)
    )
}

@Composable
private fun fileTypeIconPainter(type: FileType): androidx.compose.ui.graphics.painter.Painter {
    val (icon, _) = fileTypeIconInfo(type)
    return androidx.compose.ui.res.painterResource(
        // Coil error cần Painter — dùng rememberVectorPainter
        id = android.R.drawable.ic_menu_report_image // fallback drawable
    ).let {
        androidx.compose.ui.graphics.vector.rememberVectorPainter(image = icon)
    }
}

private fun fileTypeIconInfo(type: FileType): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    return when (type) {
        FileType.IMAGE  -> Icons.Outlined.Image to Color(0xFF2196F3)
        FileType.VIDEO  -> Icons.Outlined.VideoFile to Color(0xFF9C27B0)
        FileType.AUDIO  -> Icons.Outlined.Audiotrack to Color(0xFFFF5722)
        FileType.DOC    -> Icons.Outlined.InsertDriveFile to Color(0xFFFF9800)
        FileType.APK    -> Icons.Outlined.Adb to Color(0xFF4CAF50)
        else            -> Icons.Outlined.InsertDriveFile to Color(0xFF9E9E9E)
    }
}