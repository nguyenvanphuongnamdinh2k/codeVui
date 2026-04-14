package com.example.codevui.ui.favorites

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.SavedStateHandle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.codevui.AppImageLoader
import com.example.codevui.R
import com.example.codevui.model.FavoriteItem
import com.example.codevui.model.FileType
import com.example.codevui.ui.common.viewmodel.OperationResultManager
import com.example.codevui.ui.progress.OperationProgressDialog
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.ui.selection.SelectionBottomBar
import com.example.codevui.ui.selection.SelectionTopBar
import com.example.codevui.ui.selection.selectionActionHandler
import com.example.codevui.ui.thumbnail.ThumbnailData
import com.example.codevui.util.Logger
import com.example.codevui.util.formatDateFull
import com.example.codevui.util.formatFileSize
import java.io.File

// ══════════════════════════════════════════════════════════════════════════════
// Main Screen
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = viewModel(),
    onBack: () -> Unit = {},
    onFolderClick: (path: String) -> Unit = {},
    onFileClick: (item: FavoriteItem) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Get selection actions (handles Copy/Move/Share/Delete/Dialogs internally)
    val actions = selectionActionHandler(
        selectionState = viewModel.selection,
        fileActionState = viewModel.fileAction,
        fileClipboard = viewModel.clipboard,
        currentPath = Environment.getExternalStorageDirectory().absolutePath,
        onOperationComplete = { /* Room Flow auto-updates */ },
        onCopyFiles = { paths, dest, _ -> viewModel.copyFiles(paths, dest) },
        onMoveFiles = { paths, dest, _ -> viewModel.moveFiles(paths, dest) },
        onCompressFiles = { paths, zipName -> viewModel.compressFiles(paths, zipName) },
        onExtractArchive = { _, _ -> /* Not supported in FavoritesScreen */ }
    )

    // ── Show operation progress dialog
    val currentOpState = viewModel.operationState.value
    if (currentOpState != null) {
        OperationProgressDialog(
            title = uiState.operationTitle,
            state = currentOpState,
            onDismiss = { /* keep running in background */ },
            onCancel = { viewModel.cancelOperation() }
        )
    }

    // ── Snackbar for delete result
    LaunchedEffect(uiState.deletedCount) {
        if (uiState.deletedCount > 0) {
            snackbarHostState.showSnackbar("Đã xóa ${uiState.deletedCount} khỏi yêu thích")
            viewModel.clearDeletedCount()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // ── Operation result snackbar
    val operationResult by viewModel.resultManager.operationResult.collectAsStateWithLifecycle()
    LaunchedEffect(operationResult) {
        operationResult?.let { result ->
            if (result.success > 0) {
                val actionName = result.actionName
                snackbarHostState.showSnackbar("Đã $actionName ${result.success} mục")
            }
            viewModel.clearOperationResult()
        }
    }

    Scaffold(
        topBar = {
            if (viewModel.selection.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = viewModel.selection.selectedCount,
                    totalCount = uiState.favorites.size,
                    onSelectAll = { viewModel.selection.selectAll(uiState.favorites.map { it.path }) },
                    onExit = { viewModel.selection.exit() }
                )
            } else {
                FavoritesTopBar(onBack = onBack)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ── Normal mode hint
                if (!viewModel.selection.isSelectionMode) {
                    if (uiState.favorites.isNotEmpty()) {
                        FavoritesHint(totalCount = uiState.favorites.size)
                    }
                }

                // ── Content
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                } else if (uiState.favorites.isEmpty()) {
                    FavoritesEmptyState()
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            items = uiState.favorites,
                            key = { _, item -> item.fileId }
                        ) { index, item ->
                            FavoriteListItem(
                                item = item,
                                isSelectionMode = viewModel.selection.isSelectionMode,
                                isSelected = viewModel.selection.isSelected(item.path),
                                showDivider = index < uiState.favorites.lastIndex,
                                onClick = {
                                    if (viewModel.selection.isSelectionMode) {
                                        viewModel.selection.toggle(item.path)
                                    } else if (item.isDirectory) {
                                        onFolderClick(item.path)
                                    } else {
                                        onFileClick(item)
                                    }
                                },
                                onLongClick = {
                                    if (!viewModel.selection.isSelectionMode) {
                                        viewModel.selection.enterSelectionMode(item.path)
                                    }
                                }
                            )
                        }

                        // Spacer for bottom bar
                        item {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(
                                        if (viewModel.selection.isSelectionMode && viewModel.selection.selectedCount > 0) {
                                            72.dp
                                        } else 0.dp
                                    )
                            )
                        }
                    }
                }
            }

            // ── Bottom action bar
            AnimatedVisibility(
                visible = viewModel.selection.isSelectionMode && viewModel.selection.selectedCount > 0,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                SelectionBottomBar(
                    onMove = actions.onMove,
                    onCopy = actions.onCopy,
                    onShare = actions.onShare,
                    onDelete = {
                        // Delete from favorites (move to trash)
                        val paths = viewModel.selection.selectedIds
                            .map { it.removePrefix("file:").removePrefix("folder:") }
                        viewModel.deleteSelected(paths)
                        viewModel.selection.exit()
                    },
                    onCopyToClipboard = actions.onCopyToClipboard,
                    onCopyToFileClipboard = actions.onCopyToFileClipboard,
                    onDetails = actions.onDetails,
                    onRename = actions.onRename,
                    onCompress = actions.onCompress,
                    onExtract = actions.onExtract,
                    onAddToFavorites = actions.onAddToFavorites,
                    onRemoveFromFavorites = actions.onRemoveFromFavorites,
                    onAddToHomeScreen = actions.onAddToHomeScreen
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TopBar
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }
        },
        actions = {
            Icon(
                Icons.Outlined.Star,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 16.dp)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Hint / Header
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FavoritesHint(totalCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Yêu thích",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$totalCount mục",
                    fontSize = 14.sp,
                    color = Color(0xFF888888)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nhấn giữ để chọn và thực hiện thao tác",
            fontSize = 13.sp,
            color = Color(0xFFAAAAAA)
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
}

// ══════════════════════════════════════════════════════════════════════════════
// Empty State
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FavoritesEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Star,
                contentDescription = null,
                tint = Color(0xFFDDDDDD),
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Chưa có mục yêu thích",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Nhấn giữ để thêm file vào yêu thích",
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// List Item
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteListItem(
    item: FavoriteItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fileType = FileType.fromMimeType(item.mimeType)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFFF0F6FF) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        if (isSelectionMode) {
            SelectionCheckbox(
                isSelected = isSelected,
                onClick = onClick
            )
            Spacer(Modifier.width(12.dp))
        }

        // Thumbnail
        FavoriteThumbnail(
            item = item,
            fileType = fileType,
            modifier = Modifier.size(52.dp)
        )

        Spacer(Modifier.width(14.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (item.isDirectory) "Thư mục" else formatFileSize(item.size),
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
                if (!item.isDirectory && item.dateModified > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("•", color = Color(0xFFDDDDDD))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatDateFull(item.dateModified),
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }

        if (!isSelectionMode) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFDDDDDD),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 86.dp, end = 20.dp),
            thickness = 0.5.dp,
            color = Color(0xFFF0F0F0)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Thumbnail
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FavoriteThumbnail(
    item: FavoriteItem,
    fileType: FileType,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val log = Logger("FavoriteThumbnail")

    var imageLoaded by remember { mutableStateOf(false) }

    log.d("FavoriteThumbnail: name=${item.name}, mimeType=${item.mimeType}, uri=${item.uri}, isDir=${item.isDirectory}, fileType=$fileType")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (!imageLoaded) Modifier.background(Color(0xFFF5F5F5))
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!item.isDirectory && item.uri != android.net.Uri.EMPTY) {
            val thumbnailData: ThumbnailData? = when (fileType) {
                FileType.IMAGE -> null
                FileType.VIDEO -> if (item.path.isNotEmpty()) ThumbnailData.Video(
                    uri = item.uri,
                    path = item.path
                ) else null
                FileType.AUDIO -> ThumbnailData.Audio(
                    uri = item.uri,
                    path = item.path
                )
                FileType.APK -> if (item.path.isNotEmpty()) ThumbnailData.Apk(
                    uri = item.uri,
                    path = item.path
                ) else null
                else -> null
            }

            if (thumbnailData != null) {
                val imageRequest = ImageRequest.Builder(context)
                    .data(thumbnailData)
                    .crossfade(true)
                    .build()

                SubcomposeAsyncImage(
                    model = imageRequest,
                    imageLoader = AppImageLoader.get(context),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Icon(
                            getFavoriteIcon(fileType, item.isDirectory),
                            contentDescription = null,
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    error = {
                        Icon(
                            getFavoriteIcon(fileType, item.isDirectory),
                            contentDescription = null,
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    success = {
                        imageLoaded = true
                        SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                    }
                )
            } else if (fileType == FileType.IMAGE && item.uri != android.net.Uri.EMPTY) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .crossfade(true)
                        .build(),
                    imageLoader = AppImageLoader.get(context),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Icon(
                            getFavoriteIcon(fileType, item.isDirectory),
                            contentDescription = null,
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    error = {
                        Icon(
                            getFavoriteIcon(fileType, item.isDirectory),
                            contentDescription = null,
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    success = {
                        imageLoaded = true
                        SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize())
                    }
                )
            } else {
                Icon(
                    getFavoriteIcon(fileType, item.isDirectory),
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(26.dp)
                )
            }
        } else {
            Icon(
                getFavoriteIcon(fileType, item.isDirectory),
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun getFavoriteIcon(fileType: FileType, isDirectory: Boolean): androidx.compose.ui.graphics.vector.ImageVector =
    when {
        isDirectory -> Icons.Default.Folder
        fileType == FileType.IMAGE -> Icons.Default.Image
        fileType == FileType.VIDEO -> Icons.Default.VideoFile
        fileType == FileType.AUDIO -> Icons.Default.AudioFile
        fileType == FileType.DOC -> Icons.Default.Description
        fileType == FileType.APK -> Icons.Default.Android
        fileType == FileType.ARCHIVE -> Icons.Default.Archive
        else -> Icons.Default.InsertDriveFile
    }