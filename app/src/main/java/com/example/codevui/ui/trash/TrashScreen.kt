package com.example.codevui.ui.trash

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.codevui.data.TrashItem
import com.example.codevui.model.FileType
import com.example.codevui.ui.common.EmptyState
import com.example.codevui.ui.common.LoadingIndicator
import com.example.codevui.ui.common.viewmodel.OperationResultSnackbar
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.util.Logger
import com.example.codevui.util.formatFileSize
import java.io.File

private val log = Logger("TrashScreen")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TrashViewModel = viewModel(),
    onBack: () -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Selection state
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    val selectedCount = selectedIds.size
    val allIds = uiState.items.map { it.id }
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) {
            val newSet = selectedIds - id
            if (newSet.isEmpty()) {
                isSelectionMode = false
                emptySet()
            } else newSet
        } else selectedIds + id
    }

    fun enterSelectionMode(id: String) {
        selectedIds = setOf(id)
        isSelectionMode = true
    }

    fun exitSelection() {
        selectedIds = emptySet()
        isSelectionMode = false
    }

    fun selectAll() {
        selectedIds = if (selectedIds.size == allIds.size) emptySet() else allIds.toSet()
        if (selectedIds.isEmpty()) isSelectionMode = false
    }

    OperationResultSnackbar(
        resultManager = viewModel.resultManager,
        snackbarHostState = snackbarHostState,
        onActionPerformed = { destPath ->
            if (destPath.isNotEmpty()) onNavigateToFolder(destPath)
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSelectionMode) exitSelection() else onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = { }) {
                                // Spacer/placeholder — no select-all here in trash
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                if (isSelectionMode && selectedCount > 0) {
                    TrashBottomBar(
                        selectedCount = selectedCount,
                        onRestore = {
                            log.d("BottomBar: Restore clicked, ${selectedIds.size} items")
                            viewModel.restore(selectedIds.toList())
                            exitSelection()
                        },
                        onPermanentDelete = {
                            log.d("BottomBar: Xóa vĩnh viễn clicked, ${selectedIds.size} items")
                            viewModel.permanentlyDelete(selectedIds.toList())
                            exitSelection()
                        }
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = Color.White
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Header
                TrashHeader(
                    totalCount = uiState.totalCount,
                    isSelectionMode = isSelectionMode,
                    selectedCount = selectedCount,
                    onSelectAll = { selectAll() },
                    onCancel = { exitSelection() },
                    onEmptyTrash = {
                        viewModel.emptyTrash()
                        exitSelection()
                    }
                )

                if (uiState.isLoading) {
                    LoadingIndicator()
                } else if (uiState.isEmpty) {
                    EmptyState("Thùng rác trống")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Description (dynamic từ Room data)
                        item(key = "description") {
                            TrashDescription(
                                daysLeft = uiState.oldestItemDaysLeft,
                                progress = uiState.oldestItemProgress
                            )
                        }

                        // Items
                        itemsIndexed(
                            items = uiState.items,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            TrashListItem(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = item.id in selectedIds,
                                showDivider = index < uiState.items.lastIndex,
                                onClick = {
                                    if (isSelectionMode) toggle(item.id)
                                },
                                onLongClick = {
                                    if (!isSelectionMode) enterSelectionMode(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── TrashHeader ────────────────────────────────────────────────────────────────

@Composable
private fun TrashHeader(
    totalCount: Int,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onEmptyTrash: () -> Unit = {}
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Dọn sạch thùng rác?") },
            text = { Text("Tất cả $totalCount mục sẽ bị xóa vĩnh viễn. Không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onEmptyTrash()
                }) {
                    Text("Xóa tất cả", color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (isSelectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSelectAll) {
                    SelectionCheckbox(
                        isSelected = selectedCount == totalCount && totalCount > 0,
                        onClick = onSelectAll
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tất cả",
                    fontSize = 13.sp,
                    color = Color(0xFF666666)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Đã chọn $selectedCount",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) {
                    Text("Thoát", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thùng rác",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    if (totalCount > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$totalCount mục",
                            fontSize = 14.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
                if (totalCount > 0) {
                    TextButton(onClick = { showConfirmDialog = true }) {
                        Text(
                            text = "Dọn sạch",
                            color = Color(0xFFE53935),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ── TrashDescription ───────────────────────────────────────────────────────────

@Composable
private fun TrashDescription(
    daysLeft: Int = 30,
    progress: Float = 0f
) {
    // progress bar màu đỏ nếu còn < 5 ngày
    val barColor = if (daysLeft < 5) Color(0xFFE53935) else Color(0xFF1A73E8)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Progress line: "Còn X ngày nữa là xóa"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (daysLeft > 0) "Còn $daysLeft ngày nữa là xóa" else "Sắp xóa vĩnh viễn",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (daysLeft < 5) Color(0xFFE53935) else Color(0xFF666666)
            )
            Spacer(Modifier.width(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = barColor,
                trackColor = Color(0xFFE0E0E0)
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Thùng rác này hiển thị các mục đã bị xóa khỏi File của bạn, Bộ sưu tập và một số ứng dụng của bên thứ ba. Các mục này sẽ bị xóa vĩnh viễn sau 30 ngày.",
            fontSize = 13.sp,
            color = Color(0xFF999999),
            lineHeight = 18.sp
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
}

// ── TrashListItem ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashListItem(
    item: TrashItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fileType = FileType.fromMimeType(item.mimeType.ifEmpty { null })
    val icon = when {
        item.isDirectory -> "📁"
        fileType == FileType.IMAGE -> "🖼️"
        fileType == FileType.VIDEO -> "🎬"
        fileType == FileType.AUDIO -> "🎵"
        fileType == FileType.DOC -> "📄"
        fileType == FileType.APK -> "📱"
        fileType == FileType.ARCHIVE -> "📦"
        else -> "📎"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) Color(0xFFF0F6FF) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            if (fileType == FileType.IMAGE || fileType == FileType.VIDEO) {
                val trashPath = File(
                    android.os.Environment.getExternalStorageDirectory(),
                    ".Trash/files/${item.trashName}"
                )
                if (trashPath.exists()) {
                    AsyncImage(
                        model = trashPath,
                        contentDescription = item.originalName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(icon, fontSize = 24.sp)
                }
            } else {
                Text(icon, fontSize = 24.sp)
            }
        }

        Spacer(Modifier.width(16.dp))

        // Name + size + days left
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.originalName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatFileSize(item.size),
                    fontSize = 13.sp,
                    color = Color(0xFF999999)
                )
                if (item.daysUntilExpiry > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text("•", fontSize = 13.sp, color = Color(0xFFCCCCCC))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Còn ${item.daysUntilExpiry} ngày",
                        fontSize = 13.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
    }

    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 88.dp, end = 20.dp),
            thickness = 0.5.dp,
            color = Color(0xFFF0F0F0)
        )
    }
}

// ── TrashBottomBar ────────────────────────────────────────────────────────────

@Composable
private fun TrashBottomBar(
    selectedCount: Int,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE0E0E0))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Restore button
                Column(
                    modifier = Modifier.clickable(onClick = onRestore),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Khôi phục",
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Khôi phục",
                        fontSize = 12.sp,
                        color = Color(0xFF1A73E8)
                    )
                }

                // Permanent delete button
                Column(
                    modifier = Modifier.clickable(onClick = onPermanentDelete),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xóa vĩnh viễn",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Xóa vĩnh viễn",
                        fontSize = 12.sp,
                        color = Color(0xFFE53935)
                    )
                }
            }
        }
    }
}
