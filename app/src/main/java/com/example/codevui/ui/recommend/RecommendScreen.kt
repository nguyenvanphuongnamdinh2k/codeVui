package com.example.codevui.ui.recommend

import android.net.Uri
import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.codevui.AppImageLoader
import com.example.codevui.model.RecommendCard
import com.example.codevui.model.RecommendFile
import com.example.codevui.model.RecommendType
import com.example.codevui.ui.thumbnail.ThumbnailData
import com.example.codevui.util.formatFileSize
import com.example.codevui.util.formatStorageSize
import com.example.codevui.util.formatDateFull
import java.io.File

// ══════════════════════════════════════════════════════════════════════════════
// Main Screen
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendScreen(
    viewModel: RecommendViewModel = viewModel(),
    onBack: () -> Unit = {},
    initialCardType: RecommendType? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load card type được truyền từ navigation (nếu có)
    LaunchedEffect(initialCardType) {
        if (initialCardType != null) {
            viewModel.selectCard(initialCardType)
        }
    }

    // Show snackbar khi xóa thành công
    LaunchedEffect(uiState.deleteSuccessCount, uiState.deleteErrorCount) {
        if (uiState.deleteSuccessCount > 0 || uiState.deleteErrorCount > 0) {
            val msg = if (uiState.deleteErrorCount > 0) {
                "Đã xóa ${uiState.deleteSuccessCount} file. ${uiState.deleteErrorCount} file thất bại."
            } else {
                "Đã xóa ${uiState.deleteSuccessCount} file thành công."
            }
            snackbarHostState.showSnackbar(msg)
            viewModel.clearDeleteResult()
        }
    }

    // Show snackbar khi có lỗi
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.selectedCardType != null) {
                        Text(
                            text = uiState.cards.find { it.type == uiState.selectedCardType }?.title ?: "File đề xuất",
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text("File đề xuất", fontWeight = FontWeight.Medium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedCardType != null) {
                            viewModel.clearSelectedCard()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Loading ──────────────────────────────────────────────
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else if (uiState.selectedCardType != null) {
                // ── File list view ───────────────────────────────────
                FileListView(
                    files = uiState.files,
                    selectedPaths = uiState.selectedFilePaths,
                    isLoading = uiState.isLoadingFiles,
                    isDeleting = uiState.isDeleting,
                    onToggleSelect = viewModel::toggleFileSelection,
                    onSelectAll = viewModel::selectAllFiles,
                    onDelete = viewModel::deleteSelectedFiles,
                    onRefresh = { viewModel.selectCard(uiState.selectedCardType!!) }
                )
            } else {
                // ── Card list view ────────────────────────────────────
                CardListView(
                    cards = uiState.cards,
                    onCardClick = viewModel::selectCard
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Card List View — danh sách loại card (OLD_MEDIA, SCREENSHOT, etc.)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CardListView(
    cards: List<RecommendCard>,
    onCardClick: (RecommendType) -> Unit
) {
    if (cards.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Không có gì cần dọn dẹp",
                    fontSize = 16.sp,
                    color = Color(0xFF888888)
                )
                Text(
                    "Dung lượng của bạn đang ở mức tốt",
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cards, key = { it.type.value }) { card ->
            RecommendCardItem(
                card = card,
                onClick = { onCardClick(card.type) }
            )
        }

        item {
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RecommendCardItem(
    card: RecommendCard,
    onClick: () -> Unit
) {
    val (icon, iconBg) = getRecommendCardIcon(card.type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = card.description,
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            // Size
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatStorageSize(card.sizeBytes),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2196F3)
                )
                if (card.fileCount > 0) {
                    Text(
                        text = "${card.fileCount} file",
                        fontSize = 11.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// File List View — danh sách file khi chọn 1 card
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FileListView(
    files: List<RecommendFile>,
    selectedPaths: Set<String>,
    isLoading: Boolean,
    isDeleting: Boolean,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit
) {
    val hasSelection = selectedPaths.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Action bar (selection mode) ────────────────────────────
        AnimatedVisibility(
            visible = hasSelection,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            SelectionTopBar(
                selectedCount = selectedPaths.size,
                totalCount = files.size,
                onSelectAll = onSelectAll,
                onClear = { selectedPaths.forEach { onToggleSelect(it) } }
            )
        }

        // ── File list ──────────────────────────────────────────────
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Không có file nào", color = Color(0xFF888888))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(files, key = { it.path }) { file ->
                    FileListItem(
                        file = file,
                        isSelected = file.path in selectedPaths,
                        onToggle = { onToggleSelect(file.path) }
                    )
                }

                item {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        // ── Bottom action bar ──────────────────────────────────────
        AnimatedVisibility(
            visible = hasSelection && !isDeleting,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            BottomDeleteBar(
                selectedCount = selectedPaths.size,
                onDelete = onDelete
            )
        }

        if (isDeleting) {
            BottomDeleteProgressBar()
        }
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        color = Color(0xFF2196F3),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Bỏ chọn",
                    tint = Color.White
                )
            }

            Text(
                text = "Đã chọn $selectedCount/$totalCount",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            if (selectedCount < totalCount) {
                TextButton(onClick = onSelectAll) {
                    Text("Chọn tất cả", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun FileListItem(
    file: RecommendFile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val icon = getFileIcon(file.mimeType)
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) Color(0xFF2196F3) else Color(0xFFDDDDDD)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Thumbnail / Icon
        FileThumbnail(
            file = file,
            icon = icon,
            modifier = Modifier.size(44.dp)
        )

        Spacer(Modifier.width(12.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 14.sp,
                color = Color(0xFF222222),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatFileSize(file.size)} • ${formatDateFull(file.dateModified)}",
                fontSize = 11.sp,
                color = Color(0xFFAAAAAA)
            )
        }
    }
}

@Composable
private fun BottomDeleteBar(
    selectedCount: Int,
    onDelete: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$selectedCount file được chọn",
                fontSize = 13.sp,
                color = Color(0xFF666666),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Xóa", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun BottomDeleteProgressBar() {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Đang xóa...", fontSize = 14.sp, color = Color(0xFF666666))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════════════════════════════════════════

private fun getRecommendCardIcon(type: RecommendType): Pair<ImageVector, Color> {
    return when (type) {
        RecommendType.OLD_MEDIA_FILES -> Icons.Outlined.Image to Color(0xFF9C27B0)
        RecommendType.UNNECESSARY_FILES -> Icons.Outlined.Archive to Color(0xFF4CAF50)
        RecommendType.SCREENSHOT_FILES -> Icons.Outlined.PhotoCamera to Color(0xFFFF9800)
        RecommendType.DOWNLOAD_FILES -> Icons.Outlined.Download to Color(0xFF2196F3)
        RecommendType.COMPRESSED_FILES -> Icons.Outlined.FolderOpen to Color(0xFF607D8B)
    }
}

private fun getFileIcon(mimeType: String?): ImageVector {
    return when {
        mimeType == null -> Icons.AutoMirrored.Outlined.InsertDriveFile
        mimeType.startsWith("image/") -> Icons.Default.Image
        mimeType.startsWith("video/") -> Icons.Default.VideoFile
        mimeType.startsWith("audio/") -> Icons.Default.AudioFile
        mimeType == "application/vnd.android.package-archive" -> Icons.Default.Android
        mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("compressed") -> Icons.Default.Archive
        mimeType.startsWith("text/") -> Icons.Default.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

// ── Thumbnail helper ────────────────────────────────────────────────────────────

/**
 * Hiển thị thumbnail cho file:
 * - Image/Video/Audio/APK: dùng Coil + custom fetcher
 * - Others: fallback icon
 */
@Composable
private fun FileThumbnail(
    file: RecommendFile,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mime = file.mimeType ?: ""

    // Xác định loại thumbnail data cần load
    val thumbnailData: ThumbnailData? = when {
        mime.startsWith("image/") || mime.startsWith("video/") -> ThumbnailData.Video(
            uri = file.uri,
            path = file.path
        )
        mime.startsWith("audio/") -> ThumbnailData.Audio(
            uri = file.uri,
            path = file.path
        )
        mime == "application/vnd.android.package-archive" -> ThumbnailData.Apk(
            uri = file.uri,
            path = file.path
        )
        else -> null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF0F0F0)),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailData != null && file.uri != Uri.EMPTY) {
            // Load thumbnail qua Coil với custom fetcher
            val imageRequest = ImageRequest.Builder(context)
                .data(thumbnailData)
                .crossfade(true)
                .build()

            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        } else {
            // Fallback: hiện icon
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
