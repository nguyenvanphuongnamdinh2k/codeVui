package com.example.codevui.ui.archive

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.data.FileOperations
import com.example.codevui.data.FileRepository
import com.example.codevui.ui.common.SelectableScaffold
import com.example.codevui.ui.common.dialogs.DialogHandler
import com.example.codevui.ui.common.dialogs.PasswordDialog
import com.example.codevui.ui.common.dialogs.rememberDialogManager
import com.example.codevui.ui.common.viewmodel.OperationResultSnackbar
import com.example.codevui.ui.components.ArchiveEntryItem
import com.example.codevui.ui.components.SortBar
import com.example.codevui.ui.selection.FileActionState
import com.example.codevui.ui.selection.FolderPickerSheet
import com.example.codevui.ui.selection.selectionActionHandler
import com.example.codevui.util.Logger

private val log = Logger("ArchiveScreen")

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    archivePath: String,
    archiveName: String,
    fullPath: String = archivePath,
    isPreviewMode: Boolean = false,  // Preview mode - auto enter selection với special menu
    viewModel: ArchiveViewModel = viewModel(),
    onBack: () -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection = viewModel.selection
    val snackbarHostState = remember { SnackbarHostState() }
    val extractResult by viewModel.extractResult.collectAsState()
    val fileActionState = remember { FileActionState(androidx.lifecycle.SavedStateHandle()) }
    val dialogManager = rememberDialogManager()

    // Password dialog state
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordArchivePath by remember { mutableStateOf("") }
    var archiveFileName by remember { mutableStateOf("") }
    var archiveFullPath by remember { mutableStateOf("") }

    // Set password callback
    DisposableEffect(viewModel) {
        viewModel.onPasswordRequired = { path ->
            passwordArchivePath = path
            showPasswordDialog = true
        }
        onDispose {
            viewModel.onPasswordRequired = null
        }
    }

    // Track folders đã được auto-selected để tránh auto-select lại khi navigate back
    val autoSelectedPaths = remember { mutableSetOf<String>() }

    // Reset selection state khi thoát khỏi archive hoặc về root
    val onResetArchiveSelection = {
        selection.exit()
        autoSelectedPaths.clear()
    }

    LaunchedEffect(archivePath, isPreviewMode) {
        // Clear selection state mỗi khi archivePath hoặc isPreviewMode thay đổi
        log.d("=== LaunchedEffect(archivePath, isPreviewMode) FIRED ===")
        log.d("  archivePath = $archivePath")
        log.d("  isPreviewMode = $isPreviewMode")
        log.d("  autoSelectedPaths.size = ${autoSelectedPaths.size}")
        selection.exit()
        autoSelectedPaths.clear()
        log.d("  After exit(): selectedIds = ${selection.selectedIds}")

        archiveFileName = archiveName
        archiveFullPath = fullPath
        viewModel.openArchive(archivePath, archiveName, fullPath, isPreviewMode = isPreviewMode)
        log.d("  openArchive() called")
    }

    // Auto-enter selection mode in preview mode after data is loaded
    LaunchedEffect(isPreviewMode, uiState.folders, uiState.files, uiState.currentPath, uiState.isReady) {
        if (isPreviewMode && uiState.isReady) {
            val allIds = uiState.folders.map { "dir:${it.path}" } +
                         uiState.files.map { "file:${it.path}" }

            log.d("=== LaunchedEffect(auto-select) FIRED ===")
            log.d("  isPreviewMode = $isPreviewMode, isReady = ${uiState.isReady}, isLoading = ${uiState.isLoading}")
            log.d("  currentPath = '${uiState.currentPath}'")
            log.d("  folders.count = ${uiState.folders.size}, files.count = ${uiState.files.size}")
            log.d("  allIds = $allIds")
            log.d("  isSelectionMode = ${selection.isSelectionMode}")
            log.d("  currentPath in autoSelectedPaths = ${uiState.currentPath in autoSelectedPaths}")
            log.d("  selectedIds = ${selection.selectedIds}")

            if (!selection.isSelectionMode && allIds.isNotEmpty() &&
                uiState.currentPath !in autoSelectedPaths
            ) {
                // BRANCH 1: Initial load — select all items at root
                log.d("  → BRANCH 1: Initial load - selecting all ${allIds.size} items")
                allIds.forEach { id ->
                    if (!selection.isSelected(id)) selection.toggle(id)
                }
                autoSelectedPaths.add(uiState.currentPath)
                log.d("  → After select all: selectedIds = ${selection.selectedIds}")
            } else if (selection.isSelectionMode && uiState.currentPath.isNotEmpty()) {
                // BRANCH 2: Navigate into a folder
                val parentFolderPath = uiState.currentPath
                val parentEffectivelySelected = viewModel.isParentEffectivelySelected(parentFolderPath)
                log.d("  → BRANCH 2: Navigate into folder")
                log.d("  parentFolderPath = '$parentFolderPath'")
                log.d("  isParentEffectivelySelected = $parentEffectivelySelected")

                if (parentEffectivelySelected && uiState.currentPath !in autoSelectedPaths) {
                    log.d("  → Auto-selecting all ${allIds.size} children")
                    allIds.forEach { childId ->
                        if (!selection.isSelected(childId)) {
                            selection.toggle(childId)
                        }
                    }
                    autoSelectedPaths.add(uiState.currentPath)
                    log.d("  → After auto-select: selectedIds = ${selection.selectedIds}")
                } else {
                    log.d("  → NOT auto-selecting (parent not effectively selected or already visited)")
                }
            } else if (selection.isSelectionMode && uiState.currentPath.isEmpty() && allIds.isNotEmpty()) {
                // BRANCH 3: Root — if all children are selected but root folder itself is not,
                // auto-select the root folder (happens when reopening archive and children were selected)
                val allChildrenSelected = allIds.all { selection.isSelected(it) }
                log.d("  → BRANCH 3: Root with children already selected")
                log.d("  allChildrenSelected = $allChildrenSelected")
                if (allChildrenSelected && uiState.currentPath !in autoSelectedPaths) {
                    val rootId = allIds.first()
                    if (!selection.isSelected(rootId)) {
                        selection.enterSelectionMode(rootId)
                        log.d("  → Root folder '$rootId' auto-selected")
                        log.d("  → After root select: selectedIds = ${selection.selectedIds}")
                    }
                    autoSelectedPaths.add(uiState.currentPath)
                } else {
                    log.d("  → No action (not all children selected or already visited)")
                }
            } else {
                log.d("  → BRANCH 4: No action (isSelectionMode=${selection.isSelectionMode}, currentPath='${uiState.currentPath}', allIds.size=${allIds.size})")
            }
        }
    }

    // Hiển thị Snackbar khi extract hoàn thành (using shared component)
    OperationResultSnackbar(
        resultManager = viewModel.resultManager,
        snackbarHostState = snackbarHostState,
        onActionPerformed = { destPath ->
            log.d("onActionPerformed callback received: destPath='$destPath'")
            log.d("Calling onNavigateToFolder with: '$destPath'")
            onNavigateToFolder(destPath)
            log.d("onNavigateToFolder completed")
        }
    )

    // Back: exit selection mode → lùi folder trong archive → về screen trước
    BackHandler {
        if (selection.isSelectionMode && !isPreviewMode) selection.exit()
        else if (!viewModel.goBack()) onBack()
    }

    // Exit selection mode when navigating in normal mode only
    LaunchedEffect(uiState.currentPath) {
        if (!isPreviewMode) {
            selection.exit()
        }
    }

    val allIds = remember(uiState.folders, uiState.files) {
        uiState.folders.map { "dir:${it.path}" } + uiState.files.map { "file:${it.path}" }
    }

    // ── FolderPickerSheet cho Extract ─────────────────────────────────────
    if (fileActionState.showPicker && fileActionState.pendingOperation != null) {
        FolderPickerSheet(
            operationType = fileActionState.pendingOperation!!,
            selectionState = selection,
            onDismiss = { fileActionState.dismiss() },
            onConfirm = { destPath ->
                fileActionState.consumeOperation()
                viewModel.extractSelected(destPath)
            }
        )
    }

    // Calculate selected count in current page
    val currentPageIds = remember(uiState.folders, uiState.files) {
        uiState.folders.map { "dir:${it.path}" } + uiState.files.map { "file:${it.path}" }
    }
    val selectedInPage = remember(selection.selectedIds, currentPageIds) {
        currentPageIds.count { selection.isSelected(it) }
    }
    val allSelectedInPage = selectedInPage == currentPageIds.size && currentPageIds.isNotEmpty()

    // Preview mode: Use custom scaffold with PreviewBottomBar
    // Normal mode: Use SelectableScaffold with normal actions
    if (isPreviewMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // Show selected count
                        Text(
                            text = "Đã chọn $selectedInPage",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!viewModel.goBack()) onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    },
                    actions = {
                        // Select All / Deselect All button
                        IconButton(onClick = {
                            if (allSelectedInPage) {
                                // Deselect all in current page
                                currentPageIds.forEach { id ->
                                    if (selection.isSelected(id)) {
                                        selection.toggle(id)
                                    }
                                }
                            } else {
                                // Select all in current page
                                currentPageIds.forEach { id ->
                                    if (!selection.isSelected(id)) {
                                        selection.toggle(id)
                                    }
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = if (allSelectedInPage) "Bỏ chọn tất cả" else "Chọn tất cả"
                            )
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Thêm")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                ArchivePreviewHandler(
                    viewModel = viewModel,
                    selectionState = selection,
                    dialogManager = dialogManager,
                    onExit = onBack
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { padding ->
            ArchiveContent(
                padding = padding,
                uiState = uiState,
                selection = selection,
                viewModel = viewModel,
                isPreviewMode = isPreviewMode,
                currentPageIds = currentPageIds,
                selectedInPage = selectedInPage,
                allSelectedInPage = allSelectedInPage,
                onBack = onBack,
                onNavigateToFolder = onNavigateToFolder,
                onResetSelection = onResetArchiveSelection
            )
        }
    } else {
        val normalActions = selectionActionHandler(
            selectionState = selection,
            fileActionState = fileActionState,
            onOperationComplete = { },
            onCopyFiles = { _, _, _ -> },
            onMoveFiles = { _, _, _ -> },
            onCompressFiles = { _, _ -> }
        )

        // Override copy action thành extract action
        val actions = remember(selection, fileActionState) {
            normalActions.copy(
                onCopy = {
                    fileActionState.requestCopy()
                }
            )
        }

        SelectableScaffold(
            selection = selection,
            actions = actions,
            totalCount = allIds.size,
            allIds = allIds,
            onBack = { if (!viewModel.goBack()) onBack() },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            normalTopBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!viewModel.goBack()) onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Thêm")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            ArchiveContent(
                padding = padding,
                uiState = uiState,
                selection = selection,
                viewModel = viewModel,
                isPreviewMode = isPreviewMode,
                currentPageIds = emptyList(),
                selectedInPage = 0,
                onBack = onBack,
                onNavigateToFolder = onNavigateToFolder,
                onResetSelection = onResetArchiveSelection
            )
        }
    }

    // ── Password Dialog ───────────────────────────────────────────────────
    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = {
                showPasswordDialog = false
                passwordArchivePath = ""
                archiveFileName = ""
                archiveFullPath = ""
            },
            onConfirm = { password ->
                showPasswordDialog = false
                // Retry opening archive with password and save it
                viewModel.setPassword(password)
                viewModel.openArchive(passwordArchivePath, archiveFileName, archiveFullPath, password)
                passwordArchivePath = ""
                archiveFileName = ""
                archiveFullPath = ""
            }
        )
    }

    // ── Operation Progress Dialog ────────────────────────────────────────
    if (uiState.isOperating) {
        OperationProgressDialog(
            operationType = uiState.operationType,
            progress = uiState.operationProgress,
            total = uiState.operationTotal
        )
    }

    // ── Dialog Handler — Render all dialogs for preview mode ─────────────
    if (isPreviewMode) {
        DialogHandler(dialogManager)
    }
}

/**
 * ArchiveContent — Archive content display (extracted for reuse between preview and normal mode)
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
private fun ArchiveContent(
    padding: PaddingValues,
    uiState: ArchiveUiState,
    selection: com.example.codevui.ui.selection.SelectionState,
    viewModel: ArchiveViewModel,
    isPreviewMode: Boolean,
    currentPageIds: List<String>,
    selectedInPage: Int,
    allSelectedInPage: Boolean = false,  // default for normal mode call site

    onBack: () -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onResetSelection: () -> Unit = {}  // Reset selection khi thoát khỏi archive
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Breadcrumb — custom để chỉ cho phép navigate trong archive
        ArchiveBreadcrumb(
            segments = uiState.pathSegments,
            archiveFileIndex = uiState.archiveFileIndex,
            archivePath = uiState.archivePath,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            onHomeClick = onBack,
            onSegmentClick = { index ->
                log.d("=== onSegmentClick(index=$index) ===")
                log.d("  archiveFileIndex = ${uiState.archiveFileIndex}")
                log.d("  pathSegments = ${uiState.pathSegments}")
                if (index >= uiState.archiveFileIndex) {
                    // Navigate trong archive
                    val pathStackIndex = index - uiState.archiveFileIndex
                    log.d("  → Navigate WITHIN archive, pathStackIndex = $pathStackIndex")
                    if (pathStackIndex == 0) {
                        log.d("  → Reset selection (navigate to root)")
                        onResetSelection()
                    }
                    viewModel.navigateToSegment(pathStackIndex)
                } else {
                    // Navigate ra parent folder - reset archive selection and exit archive preview
                    log.d("  → Navigate OUT of archive (segment before zip)")
                    onResetSelection()
                    val parentPath = buildPathFromSegments(uiState.pathSegments.take(index + 1))
                    log.d("  → parentPath = '$parentPath'")
                    if (parentPath.isNotEmpty()) {
                        onNavigateToFolder(parentPath)
                    }
                }
            },
            isPreviewMode = isPreviewMode
        )

        val listState = rememberLazyListState()
        val showSortBar by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
        LaunchedEffect(uiState.sortBy, uiState.ascending) {
            listState.scrollToItem(0)
        }

        // Custom SortBar with Select All for preview mode
        AnimatedVisibility(visible = showSortBar, enter = fadeIn(), exit = fadeOut()) {
            if (isPreviewMode) {
                ArchiveSortBar(
                    selectedCount = selectedInPage,
                    totalCount = currentPageIds.size,
                    allSelected = allSelectedInPage,
                    onSelectAll = {
                        if (allSelectedInPage) {
                            currentPageIds.forEach { id ->
                                if (selection.isSelected(id)) selection.toggle(id)
                            }
                        } else {
                            currentPageIds.forEach { id ->
                                if (!selection.isSelected(id)) selection.toggle(id)
                            }
                        }
                    }
                )
            } else {
                SortBar(
                    sortBy = uiState.sortBy,
                    ascending = uiState.ascending,
                    onSortChanged = viewModel::onSortChanged,
                    onToggleDirection = viewModel::toggleSortDirection
                )
            }
        }

        // Loading
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        // Error
        if (uiState.error != null && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.error!!, color = Color(0xFF999999), fontSize = 14.sp)
            }
        }

        // Archive contents
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // Folders inside archive
            itemsIndexed(
                items = uiState.folders
            ) { index, entry ->
                val id = "dir:${entry.path}"
                // Compute tri-state: ALL if folder selected and all children selected,
                // PARTIAL if folder selected but only some/all children selected,
                // NONE if folder not selected
                val folderTriState = viewModel.getFolderTriState(entry.path)
                ArchiveEntryItem(
                    entry = entry,
                    showDivider = index < uiState.folders.lastIndex || uiState.files.isNotEmpty(),
                    isSelectionMode = selection.isSelectionMode,
                    isSelected = selection.isSelected(id),
                    folderTriState = folderTriState,
                    onClick = {
                        viewModel.openFolder(entry.path, entry.name)
                        // Auto-selection handled by LaunchedEffect
                    },
                    onLongClick = {
                        if (!isPreviewMode) {
                            selection.enterSelectionMode(id)
                        }
                    },
                    onToggleSelect = {
                        val childrenIds = viewModel.getVisibleChildrenIds(entry.path)
                        if (selection.isSelected(id)) {
                            // Currently selected → deselect folder and all visible descendants
                            selection.toggle(id)
                            childrenIds.forEach { childId ->
                                if (selection.isSelected(childId)) selection.toggle(childId)
                            }
                        } else {
                            // Currently not selected → select folder and all visible descendants
                            selection.toggle(id)
                            childrenIds.forEach { childId ->
                                if (!selection.isSelected(childId)) selection.toggle(childId)
                            }
                        }
                    },
                    isPreviewMode = isPreviewMode,
                    archivePath = uiState.archivePath,
                    password = viewModel.getPassword()
                )
            }

            // Files inside archive
            itemsIndexed(
                items = uiState.files
            ) { index, entry ->
                val id = "file:${entry.path}"
                ArchiveEntryItem(
                    entry = entry,
                    showDivider = index < uiState.files.lastIndex,
                    isSelectionMode = selection.isSelectionMode,
                    isSelected = selection.isSelected(id),
                    onClick = { /* TODO: preview */ },
                    onLongClick = {
                        if (!isPreviewMode) {
                            selection.enterSelectionMode(id)
                        }
                    },
                    onToggleSelect = {
                        selection.toggle(id)
                        // Update parent folder's tri-state (both normal and preview mode)
                        val parentPath = uiState.currentPath
                        if (parentPath.isNotEmpty()) {
                            val parentId = "dir:$parentPath"
                            val parentChildren = viewModel.getVisibleChildrenIds(parentPath)
                            if (parentChildren.isNotEmpty()) {
                                val selectedCount = parentChildren.count { selection.isSelected(it) }
                                val wasParentSelected = selection.isSelected(parentId)
                                when {
                                    selectedCount == parentChildren.size && !wasParentSelected -> {
                                        selection.toggle(parentId)
                                    }
                                    selectedCount == 0 && wasParentSelected -> {
                                        selection.toggle(parentId)
                                    }
                                }
                            }
                        }
                    },
                    isPreviewMode = isPreviewMode,
                    archivePath = uiState.archivePath,
                    password = viewModel.getPassword()
                )
            }

            // Empty
            if (!uiState.isLoading && uiState.error == null &&
                uiState.folders.isEmpty() && uiState.files.isEmpty()
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Thư mục trống", color = Color(0xFF999999))
                    }
                }
            }
        }
    }
}

/**
 * Build absolute path từ breadcrumb segments
 */
private fun buildPathFromSegments(segments: List<String>): String {
    if (segments.isEmpty()) return ""

    // Skip "Bộ nhớ trong" segment
    val pathSegments = segments.drop(1)
    if (pathSegments.isEmpty()) {
        return android.os.Environment.getExternalStorageDirectory().absolutePath
    }

    return android.os.Environment.getExternalStorageDirectory().absolutePath +
           "/" + pathSegments.joinToString("/")
}

/**
 * ArchiveBreadcrumb — Custom breadcrumb for archive navigation
 * Hiển thị full path từ root đến archive file và folders bên trong
 * Cho phép click vào tất cả segments để navigate
 */
@Composable
private fun ArchiveBreadcrumb(
    segments: List<String>,
    archiveFileIndex: Int,
    archivePath: String,
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onSegmentClick: (Int) -> Unit = {},
    isPreviewMode: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "Home",
            tint = Color(0xFF888888),
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onHomeClick)
        )

        segments.forEachIndexed { index, segment ->
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(20.dp)
            )

            val isLast = index == segments.lastIndex
            val isClickable = !isLast  // Cho phép click tất cả segments trừ last
            val textColor = when {
                isLast -> Color(0xFF1A73E8)  // Last segment (current location)
                else -> Color(0xFF888888)  // All other segments (clickable)
            }

            Text(
                text = segment,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.clickable(enabled = isClickable) {
                    onSegmentClick(index)
                }
            )
        }
    }
}

/**
 * ArchiveSortBar — Custom sort bar with Select All for preview mode
 * Thay "Xem theo file" bằng "Select All / Deselect All"
 */
@Composable
private fun ArchiveSortBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showAllSelected = allSelected && totalCount > 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Select All / Deselect All button
        Row(
            modifier = Modifier.clickable(onClick = onSelectAll),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.example.codevui.ui.selection.SelectionCheckbox(
                isSelected = showAllSelected,
                onClick = onSelectAll,
                triState = if (showAllSelected) {
                    com.example.codevui.ui.selection.TriState.ALL
                } else {
                    com.example.codevui.ui.selection.TriState.NONE
                }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (showAllSelected) "Bỏ chọn tất cả" else "Chọn tất cả",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666)
            )
        }

        Spacer(Modifier.weight(1f))

        // Show selected count
        Text(
            text = "$selectedCount/$totalCount",
            fontSize = 13.sp,
            color = Color(0xFF888888)
        )
    }
}