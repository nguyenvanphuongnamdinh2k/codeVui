package com.example.codevui.ui.largefiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.common.dialogs.MoveToTrashDialog
import com.example.codevui.ui.progress.OperationProgressDialog
import com.example.codevui.ui.components.FileThumbnail
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.ui.selection.TriState
import com.example.codevui.util.formatFileSize
import com.example.codevui.util.Logger
import kotlinx.coroutines.launch

private val log = Logger("LargeFilesScreen")

// ══════════════════════════════════════════════════════
// LargeFilesScreen — Main screen
// ══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFilesScreen(
    viewModel: LargeFilesViewModel = viewModel(),
    onBack: () -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onTrashClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection = viewModel.selection
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Operation state
    val operationState by viewModel.operationState.collectAsState()
    val operationTitle by viewModel.operationTitle.collectAsState()
    val isDialogHidden by viewModel.isDialogHidden.collectAsState()
    val isOperationRunning = operationState is ProgressState.Running || operationState is ProgressState.Counting

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSizeThresholdScreen by remember { mutableStateOf(false) }
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showTypeFilterDropdown by remember { mutableStateOf(false) }

    // ── Move to Trash confirm — qua FileOperationService ──
    fun confirmDelete() {
        val selectedPaths = selection.selectedIds.toList()
        showDeleteDialog = false
        if (selectedPaths.isEmpty()) return

        selection.exit()
        viewModel.trashFiles(selectedPaths)
    }

    // ── Dialogs ──
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

    // ── Size Threshold sub-screen ──
    if (showSizeThresholdScreen) {
        SizeThresholdScreen(
            currentThreshold = uiState.sizeThreshold,
            currentCustomBytes = uiState.customThresholdBytes,
            onBack = { showSizeThresholdScreen = false },
            onSelectPreset = { threshold ->
                viewModel.setSizeThreshold(threshold)
                showSizeThresholdScreen = false
            },
            onSetCustom = { bytes ->
                viewModel.setCustomThreshold(bytes)
                showSizeThresholdScreen = false
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    // Select all / Back
                    if (selection.isSelectionMode) {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    val allIds = uiState.groups.flatMap { g -> g.files.map { it.path } }
                                    selection.selectAll(allIds)
                                }
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val allIds = uiState.groups.flatMap { g -> g.files.map { it.path } }
                            SelectionCheckbox(
                                isSelected = selection.selectedIds.size == allIds.size && allIds.isNotEmpty(),
                                triState = when {
                                    selection.selectedIds.size == allIds.size && allIds.isNotEmpty() -> TriState.ALL
                                    selection.selectedIds.isNotEmpty() -> TriState.PARTIAL
                                    else -> TriState.NONE
                                },
                                onClick = { selection.selectAll(allIds) }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Tất cả", fontSize = 13.sp, color = Color(0xFF888888))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    }
                },
                title = {
                    if (selection.isSelectionMode) {
                        Text("Đã chọn ${selection.selectedIds.size}", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Chọn mục", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (selection.isSelectionMode) {
                        // Thoát selection
                        TextButton(onClick = { selection.exit() }) {
                            Text("Thoát", fontWeight = FontWeight.Medium)
                        }
                    } else {
                        TextButton(onClick = { selection.exit(); onBack() }) {
                            Text("Thoát", fontWeight = FontWeight.Medium)
                        }
                    }

                    // ⋮ Menu
                    Box {
                        IconButton(onClick = { showMenuDropdown = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Dung lượng file lớn") },
                                onClick = {
                                    showMenuDropdown = false
                                    showSizeThresholdScreen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Loại file") },
                                onClick = {
                                    showMenuDropdown = false
                                    showTypeFilterDropdown = true
                                }
                            )
                        }
                    }

                    // Type filter dropdown (separate)
                    Box {
                        DropdownMenu(
                            expanded = showTypeFilterDropdown,
                            onDismissRequest = { showTypeFilterDropdown = false }
                        ) {
                            LargeFileTypeFilter.entries.forEach { filter ->
                                val isActive = uiState.typeFilter == filter
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = filter.label,
                                                color = if (isActive) Color(0xFF1A73E8) else Color.Unspecified
                                            )
                                            if (isActive) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF1A73E8),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.setTypeFilter(filter)
                                        showTypeFilterDropdown = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            // Delete button khi đang selection
            if (selection.isSelectionMode && selection.selectedIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Xóa (${selection.selectedIds.size})")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Tab row (chỉ "Bộ nhớ trong") ──
            TabRow(
                selectedTabIndex = 0,
                containerColor = Color.White,
                contentColor = Color(0xFF1A73E8),
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(selected = true, onClick = {}) {
                    Text(
                        "Bộ nhớ trong",
                        modifier = Modifier.padding(vertical = 12.dp),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }

            // ── Content ──
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Đang quét file lớn...", fontSize = 14.sp, color = Color(0xFF666666))
                        }
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(uiState.error!!, color = Color(0xFF999999), fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.refresh() }) { Text("Thử lại") }
                        }
                    }
                }
                uiState.groups.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📁", fontSize = 64.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("Không tìm thấy file lớn", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Không có file nào lớn hơn ${formatFileSize(if (uiState.sizeThreshold == SizeThreshold.CUSTOM) uiState.customThresholdBytes else uiState.sizeThreshold.bytes)}",
                                fontSize = 13.sp, color = Color(0xFF999999)
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        uiState.groups.forEachIndexed { groupIndex, group ->
                            // Group header
                            item(key = "header_${group.label}") {
                                Text(
                                    text = group.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF888888)
                                )
                            }

                            // File items
                            items(
                                items = group.files,
                                key = { file -> file.path }
                            ) { file ->
                                LargeFileItem(
                                    file = file,
                                    selection = viewModel.selection,
                                    onFileClick = onFileClick
                                )
                            }

                            if (groupIndex < uiState.groups.lastIndex) {
                                item(key = "spacer_${group.label}") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                        // Bottom spacer
                        item(key = "bottom_spacer") {
                            Spacer(Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// LargeFileItem — Row hiển thị mỗi file
// ══════════════════════════════════════════════════════

@Composable
private fun LargeFileItem(
    file: RecentFile,
    selection: com.example.codevui.ui.selection.SelectionState,
    onFileClick: (RecentFile) -> Unit
) {
    val id = file.path
    val isSelected = selection.isSelected(id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (selection.isSelectionMode) {
                    selection.toggle(id)
                } else {
                    // Long-press để vào selection, tap để mở file
                    if (!selection.isSelectionMode) {
                        selection.enterSelectionMode(id)
                    }
                }
            }
            .background(if (isSelected) Color(0xFFF0F6FF) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        SelectionCheckbox(
            isSelected = isSelected,
            triState = if (isSelected) TriState.ALL else TriState.NONE,
            onClick = {
                if (!selection.isSelectionMode) {
                    selection.enterSelectionMode(id)
                } else {
                    selection.toggle(id)
                }
            }
        )

        Spacer(Modifier.width(12.dp))

        // Thumbnail
        FileThumbnail(
            file = file,
            modifier = Modifier.size(48.dp)
        )

        Spacer(Modifier.width(16.dp))

        // Name + size
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 15.sp,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatFileSize(file.size),
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
        }
    }
}

// ══════════════════════════════════════════════════════
// SizeThresholdScreen — Chọn "Dung lượng file lớn"
// ══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SizeThresholdScreen(
    currentThreshold: SizeThreshold,
    currentCustomBytes: Long,
    onBack: () -> Unit,
    onSelectPreset: (SizeThreshold) -> Unit,
    onSetCustom: (Long) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    // Custom size dialog
    if (showCustomDialog) {
        CustomSizeDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { bytes ->
                showCustomDialog = false
                onSetCustom(bytes)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dung lượng file lớn", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Chọn độ lớn file cần có trước khi chúng được đánh dấu khi phân tích lưu trữ.",
                fontSize = 14.sp,
                color = Color(0xFF666666),
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))

            // Radio options card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8F8F8)
            ) {
                Column {
                    SizeThreshold.entries.forEachIndexed { index, threshold ->
                        val isSelected = currentThreshold == threshold
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        if (threshold == SizeThreshold.CUSTOM) {
                                            showCustomDialog = true
                                        } else {
                                            onSelectPreset(threshold)
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null, // handled by selectable
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF1A73E8)
                                )
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = threshold.label,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                        if (index < SizeThreshold.entries.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = Color(0xFFE8E8E8)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// CustomSizeDialog — Nhập kích thước tùy chỉnh
// ══════════════════════════════════════════════════════

@Composable
private fun CustomSizeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var sizeText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf("MB") }
    var showUnitDropdown by remember { mutableStateOf(false) }

    val isValid = sizeText.toLongOrNull()?.let { it > 0 } ?: false

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kích thước tùy chỉnh", fontWeight = FontWeight.Bold) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Kích thước") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(Modifier.width(12.dp))

                // Unit selector
                Box {
                    TextButton(onClick = { showUnitDropdown = true }) {
                        Text("$selectedUnit ▼", fontSize = 16.sp)
                    }
                    DropdownMenu(
                        expanded = showUnitDropdown,
                        onDismissRequest = { showUnitDropdown = false }
                    ) {
                        listOf("MB", "GB").forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = {
                                    selectedUnit = unit
                                    showUnitDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = sizeText.toLongOrNull() ?: return@TextButton
                    val bytes = when (selectedUnit) {
                        "GB" -> value * 1024 * 1024 * 1024
                        else -> value * 1024 * 1024
                    }
                    onConfirm(bytes)
                },
                enabled = isValid
            ) {
                Text(
                    "Hoàn tất",
                    fontWeight = FontWeight.Bold,
                    color = if (isValid) Color(0xFF1A73E8) else Color(0xFFBBBBBB)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Thoát", fontWeight = FontWeight.Medium)
            }
        }
    )
}
