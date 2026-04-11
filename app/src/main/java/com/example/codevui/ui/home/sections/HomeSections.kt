package com.example.codevui.ui.home.sections

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.codevui.model.CategoryItem
import com.example.codevui.model.FileType
import com.example.codevui.model.RecentFile
import com.example.codevui.model.StorageItem
import com.example.codevui.ui.components.*
import com.example.codevui.ui.thumbnail.ThumbnailData
import com.example.codevui.ui.thumbnail.VideoThumbnailFetcher

@Composable
fun CategoriesGrid(
    categories: List<CategoryItem>,
    modifier: Modifier = Modifier,
    onClick: (CategoryItem) -> Unit = {}
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    CategoryCard(
                        icon = item.icon,
                        tint = item.tint,
                        backgroundColor = item.bg,
                        label = item.label,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onClick(item) }
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun RecentFilesRow(
    files: List<RecentFile>,
    modifier: Modifier = Modifier,
    onClick: (RecentFile) -> Unit = {},
    onLongClick: (RecentFile) -> Unit = {}
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        files.forEach { file ->
            val hasThumb = file.type == FileType.IMAGE || file.type == FileType.VIDEO

            RecentFileCard(
                name = file.name,
                date = file.date,
                isVideo = file.type == FileType.VIDEO,
                thumbnailContent = when {
                    // Image → load trực tiếp từ uri (IMAGE không cần custom fetcher)
                    file.type == FileType.IMAGE && file.uri != Uri.EMPTY -> {
                        {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(file.uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = file.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // Video → dùng ThumbnailData.Video để load thumbnail qua VideoThumbnailFetcher
                    file.type == FileType.VIDEO && file.uri != Uri.EMPTY && file.path.isNotEmpty() -> {
                        {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(ThumbnailData.Video(uri = file.uri, path = file.path))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = file.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // Doc → file type icon
                    file.type == FileType.DOC -> {
                        { FileTypeIcon(Icons.Default.Description, Color(0xFFFF6B4A)) }
                    }
                    // Audio → dùng ThumbnailData.Audio để load embedded artwork qua AudioThumbnailFetcher
                    file.type == FileType.AUDIO -> {
                        if (file.uri != Uri.EMPTY && file.path.isNotEmpty()) {
                            {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(ThumbnailData.Audio(uri = file.uri, path = file.path))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            { FileTypeIcon(Icons.Outlined.Audiotrack, Color(0xFFFF5722)) }
                        }
                    }
                    // APK → dùng ThumbnailData.Apk để load icon qua ApkThumbnailFetcher
                    file.type == FileType.APK -> {
                        if (file.uri != Uri.EMPTY && file.path.isNotEmpty()) {
                            {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(ThumbnailData.Apk(uri = file.uri, path = file.path))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = file.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            { FileTypeIcon(Icons.Outlined.Adb, Color(0xFF4CAF50)) }
                        }
                    }
                    // Other (DOC, DOWNLOAD, ARCHIVE, etc.)
                    else -> {
                        { FileTypeIcon(Icons.Outlined.InsertDriveFile, Color(0xFF9E9E9E)) }
                    }
                },
                onClick = { onClick(file) },
                onLongClick = { onLongClick(file) }
            )
        }
    }
}

@Composable
fun StorageList(
    items: List<StorageItem>,
    modifier: Modifier = Modifier,
    onClick: (StorageItem) -> Unit = {}
) {
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            ListRow(
                icon = {
                    IconBox(
                        icon = item.icon,
                        tint = item.iconTint,
                        backgroundColor = item.iconBg
                    )
                },
                title = item.title,
                subtitle = item.subtitle,
                trailing = if (item.used != null && item.total != null) {
                    { StorageBadge(used = item.used, total = item.total) }
                } else null,
                showDivider = index < items.lastIndex,
                onClick = { onClick(item) }
            )
        }
    }
}