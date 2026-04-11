package com.example.codevui.ui.duplicates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.model.DuplicateGroup
import com.example.codevui.model.DuplicateItem
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.common.dialogs.MoveToTrashDialog
import com.example.codevui.ui.progress.OperationProgressDialog
import com.example.codevui.ui.components.FileThumbnail
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.ui.selection.TriState
import com.example.codevui.util.formatFileSize
import com.example.codevui.util.formatDateFull
import com.example.codevui.util.Logger
import kotlinx.coroutines.launch

private val log = Logger("DuplicatesScreen")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    viewModel: DuplicatesViewModel = viewModel(),
    onBack: () -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onTrashClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection = viewModel.selection
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Operation state
    val operationState by viewModel.operationState.collectAsState()
    val operationTitle by viewModel.operationTitle.collectAsState()
    val isDialogHidden by viewModel.isDialogHidden.collectAsState()
    val isOperationRunning = operationState is ProgressState.Running || operationState is ProgressState.Counting

    // Compute all duplicate item IDs (non-original items) from all groups
    val allDuplicateIds = remember(uiState.groups) {
        uiState.groups.flatMap { group ->
            group.items.filter { !it.isOriginal }.map { it.file.path }
        }.toSet()
    }

    // Select all duplicates (non-original)
    fun selectAllDuplicates() {
        allDuplicateIds.forEach { id ->
            if (!selection.isSelected(id)) {
                if (!selection.isSelectionMode) {
                    selection.enterSelectionMode(id)
                } else {
                    selection.toggle(id)
                }
            }
        }
        if (allDuplicateIds.isNotEmpty() && !selection.isSelectionMode) {
            selection.enterSelectionMode(allDuplicateIds.first())
            allDuplicateIds.drop(1).forEach { selection.toggle(it) }
        }
    }

    // Thực hiện move to trash sau khi user xác nhận — qua FileOperationService
    fun confirmDelete() {
        val selectedPaths = selection.selectedIds.toList()
        showDeleteDialog = false
        if (selectedPaths.isEmpty()) return

        selection.exit()
        viewModel.trashFiles(selectedPaths)
    }

    // Dialog xác nhận xóa
    if (showDeleteDialog) {
        MoveToTrashDialog(
            itemCount = selection.selectedIds.size,
            onDismiss = { showDeleteDialog = false },
            onConfirm = { confirmDelete() }
        )
    }

    // Progress dialog khi đang xóa file
    if (isOperationRunning && !isDialogHidden && operationState != null) {
        OperationProgressDialog(
            title = operationTitle,
            state = operationState!!,
            onCancel = { viewModel.cancelOperation() },
            onDismiss = { viewModel.hideOperationDialog() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (selection.isSelectionMode) {
                        Text("Đã chọn ${selection.selectedIds.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            Text("File trùng lặp", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (!uiState.isScanning && uiState.totalGroups > 0) {
                                Text(
                                    text = "${uiState.totalGroups} nhóm · Tiết kiệm ${formatFileSize(uiState.totalWasted)}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selection.isSelectionMode) selection.exit()
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    if (!selection.isSelectionMode && !uiState.isScanning && uiState.groups.isNotEmpty()) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (uiState.groups.isNotEmpty() && !uiState.isScanning) {
                Column {
                    // Bottom action bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selection.isSelectionMode) {
                                // Selection mode: Xóa button
                                Button(
                                    onClick = { showDeleteDialog = true },
                                    enabled = selection.selectedIds.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE53935),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Xóa (${selection.selectedIds.size})")
                                }
                            } else {
                                // Normal mode: Chọn trùng lặp button
                                Button(
                                    onClick = { selectAllDuplicates() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1A73E8),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Chọn trùng lặp (${allDuplicateIds.size})")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isScanning -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = uiState.scanProgress,
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = uiState.error!!, color = Color(0xFF999999), fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text("Thử lại")
                        }
                    }
                }
                uiState.groups.isEmpty() && !uiState.isScanning -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "📁", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Không có file trùng lặp",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Quét trong thư mục Download và thư mục chính",
                            fontSize = 13.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        uiState.groups.forEachIndexed { groupIndex, group ->
                            item(key = "header_${group.hash}") {
                                DuplicateGroupHeader(group = group)
                            }
                            items(
                                items = group.items,
                                key = { item -> item.file.path }
                            ) { item ->
                                DuplicateFileItem(
                                    item = item,
                                    groupSize = group.size,
                                    selection = selection,
                                    onFileClick = onFileClick
                                )
                            }
                            if (groupIndex < uiState.groups.lastIndex) {
                                item(key = "spacer_${group.hash}") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupHeader(group: DuplicateGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            tint = Color(0xFF1A73E8),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${group.items.size} file trùng lặp",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF333333)
            )
            Text(
                text = "${formatFileSize(group.size)} mỗi file · Tiết kiệm ${formatFileSize(group.wastedBytes)}",
                fontSize = 12.sp,
                color = Color(0xFF888888)
            )
        }
    }
}

@Composable
private fun DuplicateFileItem(
    item: DuplicateItem,
    groupSize: Long,
    selection: SelectionState,
    onFileClick: (RecentFile) -> Unit
) {
    val id = item.file.path
    val isSelected = selection.isSelected(id)
    val isSelectionMode = selection.isSelectionMode

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelectionMode) {
                    if (!selection.isSelected(id)) {
                        if (!selection.isSelectionMode) selection.enterSelectionMode(id)
                        selection.toggle(id)
                    } else {
                        selection.toggle(id)
                        if (selection.selectedIds.isEmpty()) selection.exit()
                    }
                } else {
                    onFileClick(item.file)
                }
            }
            .background(if (isSelected) Color(0xFFF0F6FF) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            SelectionCheckbox(
                isSelected = isSelected,
                triState = if (isSelected) TriState.ALL else TriState.NONE,
                onClick = {
                    if (!selection.isSelected(id)) {
                        if (!selection.isSelectionMode) selection.enterSelectionMode(id)
                        selection.toggle(id)
                    } else {
                        selection.toggle(id)
                        if (selection.selectedIds.isEmpty()) selection.exit()
                    }
                }
            )
            Spacer(Modifier.width(12.dp))
        } else if (item.isOriginal) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Gốc", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
        }

        // Thumbnail
        FileThumbnail(
            file = item.file,
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.file.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row {
                Text(
                    text = item.parentFolder,
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.file.dateModified > 0) {
                    Text(
                        text = " · ${formatDateFull(item.file.dateModified)}",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = formatFileSize(groupSize),
            fontSize = 13.sp,
            color = Color(0xFF888888)
        )
    }
}
