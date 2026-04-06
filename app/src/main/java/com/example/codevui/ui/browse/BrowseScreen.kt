package com.example.codevui.ui.browse

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.progress.OperationProgressDialog
import com.example.codevui.ui.browse.columnview.ColumnBrowseView
import com.example.codevui.ui.common.EmptyState
import com.example.codevui.ui.common.LoadingIndicator
import com.example.codevui.ui.common.SelectableScaffold
import com.example.codevui.ui.common.dialogs.CreateFileDialog
import com.example.codevui.ui.common.dialogs.CreateFolderDialog
import com.example.codevui.ui.common.dialogs.ConflictDialog
import com.example.codevui.ui.common.dialogs.PasswordDialog
import com.example.codevui.ui.common.viewmodel.OperationResultSnackbar
import com.example.codevui.ui.components.Breadcrumb
import com.example.codevui.ui.components.FileListItem
import com.example.codevui.ui.components.FolderListItem
import com.example.codevui.ui.components.SortBar
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.ui.selection.selectionActionHandler
import com.example.codevui.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val log = Logger("BrowseScreen")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    sessionId: Long = 0L,
    initialPath: String? = null,  // Path để navigate đến khi mở
    viewModel: BrowseViewModel = viewModel(),
    onBack: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onTrashClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection = viewModel.selection
    val snackbarHostState = remember { SnackbarHostState() }
    val operationResult by viewModel.operationResult.collectAsState()

    // ── Operation progress state ──────────────────────────────────────────
    val operationState by viewModel.operationState.collectAsState()
    val operationTitle by viewModel.operationTitle.collectAsState()
    val isDialogHidden by viewModel.isDialogHidden.collectAsState()

    // Password dialog state
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordArchivePath by remember { mutableStateOf("") }
    var passwordDestPath by remember { mutableStateOf("") }

    // More menu state
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }

    // Paste conflict dialog state (menu Dán)
    var showPasteConflictDialog by remember { mutableStateOf(false) }
    var pasteConflictData by remember { mutableStateOf<PasteData?>(null) }

    // Track clipboard state - use clipboardPaths directly for reactivity
    val hasClipboardContent by remember { derivedStateOf {
        val hasContent = viewModel.clipboard.clipboardPaths.isNotEmpty()
        log.d("Clipboard state changed: hasContent=$hasContent, itemCount=${viewModel.clipboard.itemCount}")
        hasContent
    } }

    // Set password callback
    DisposableEffect(viewModel) {
        viewModel.onPasswordRequired = { archivePath, destPath ->
            passwordArchivePath = archivePath
            passwordDestPath = destPath
            showPasswordDialog = true
        }
        onDispose {
            viewModel.onPasswordRequired = null
        }
    }

    val actions = selectionActionHandler(
        selectionState = selection,
        fileActionState = viewModel.fileAction,
        fileClipboard = viewModel.clipboard,
        currentPath = uiState.currentPath,
        onOperationComplete = { viewModel.reload() },
        onCopyFiles = { paths, dest, resolvedPath ->
            viewModel.copyFiles(paths, dest, resolvedPath)
        },
        onMoveFiles = { paths, dest, resolvedPath ->
            viewModel.moveFiles(paths, dest, resolvedPath)
        },
        onCompressFiles = { paths, zipName -> viewModel.compressFiles(paths, zipName) },
        onExtractArchive = { archivePath, dest -> viewModel.extractArchive(archivePath, dest) }
    )
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Navigate đến folder từ notification deep link
    LaunchedEffect(sessionId, initialPath) {
        log.d("=== LaunchedEffect triggered ===")
        log.d("sessionId=$sessionId, initialPath='$initialPath'")
        log.d("currentPath='${uiState.currentPath}'")

        if (initialPath != null) {
            log.d("Navigating to initialPath: '$initialPath'")
            viewModel.navigateToPath(initialPath)
        } else if (uiState.currentPath.isEmpty()) {
            log.d("Opening root (currentPath is empty)")
            viewModel.openRoot()
        } else {
            log.d("Skipping navigation - currentPath already set")
        }
    }

    BackHandler {
        if (selection.isSelectionMode) selection.exit()
        else if (!viewModel.goBack()) onBack()
    }

    LaunchedEffect(uiState.currentPath) { selection.exit() }

    // Tự dismiss dialog 1.5s sau khi Done/Error
    LaunchedEffect(operationState) {
        val state = operationState
        if (state is ProgressState.Done || state is ProgressState.Error) {
            delay(1500)
            viewModel.dismissOperationResult()
        }
    }

    // Hiển thị Snackbar khi operation hoàn thành (using shared component)
    OperationResultSnackbar(
        resultManager = viewModel.resultManager,
        snackbarHostState = snackbarHostState,
        onActionPerformed = { destPath -> viewModel.navigateToPath(destPath) }
    )

    val allIds = remember(uiState.columns, uiState.folders, uiState.files, isLandscape) {
        if (isLandscape && uiState.columns.isNotEmpty()) {
            uiState.columns.flatMap { col ->
                col.folders.map { "folder:${it.path}" } + col.files.map { "file:${it.path}" }
            }
        } else {
            uiState.folders.map { "folder:${it.path}" } + uiState.files.map { "file:${it.path}" }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                if (isLandscape) {
                    TopAppBar(
                        title = { },
                        modifier = Modifier.height(48.dp),
                        navigationIcon = {
                            IconButton(
                                onClick = { if (!viewModel.goBack()) onBack() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Quay lại",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onSearchClick, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", modifier = Modifier.size(20.dp))
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Thêm", modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Tạo thư mục mới") },
                                        onClick = {
                                            showMoreMenu = false
                                            showCreateFolderDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tạo file mới") },
                                        onClick = {
                                            showMoreMenu = false
                                            showCreateFileDialog = true
                                        }
                                    )
                                    if (hasClipboardContent) {
                                        DropdownMenuItem(
                                            text = { Text("Dán (${viewModel.clipboard.itemCount} mục)") },
                                            onClick = {
                                                log.d("=== Menu Dán clicked ===")
                                                log.d("DestPath: ${uiState.currentPath}")
                                                log.d("Clipboard items: ${viewModel.clipboard.itemCount}")
                                                showMoreMenu = false

                                                val pasteData = viewModel.pasteFromClipboard()
                                                if (pasteData != null) {
                                                    // Show conflict dialog
                                                    pasteConflictData = pasteData
                                                    showPasteConflictDialog = true
                                                    log.d("ConflictDialog will be shown")
                                                } else {
                                                    log.d("No conflicts — paste started directly")
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Thùng rác") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onTrashClick()
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )
                } else {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = { if (!viewModel.goBack()) onBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                            }
                        },
                        actions = {
                            IconButton(onClick = onSearchClick) {
                                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Thêm")
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Tạo thư mục mới") },
                                        onClick = {
                                            showMoreMenu = false
                                            showCreateFolderDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tạo file mới") },
                                        onClick = {
                                            showMoreMenu = false
                                            showCreateFileDialog = true
                                        }
                                    )
                                    if (hasClipboardContent) {
                                        DropdownMenuItem(
                                            text = { Text("Dán (${viewModel.clipboard.itemCount} mục)") },
                                            onClick = {
                                                log.d("=== Menu Dán clicked ===")
                                                log.d("DestPath: ${uiState.currentPath}")
                                                log.d("Clipboard items: ${viewModel.clipboard.itemCount}")
                                                showMoreMenu = false

                                                val pasteData = viewModel.pasteFromClipboard()
                                                if (pasteData != null) {
                                                    // Show conflict dialog
                                                    pasteConflictData = pasteData
                                                    showPasteConflictDialog = true
                                                    log.d("ConflictDialog will be shown")
                                                } else {
                                                    log.d("No conflicts — paste started directly")
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Thùng rác") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onTrashClick()
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Breadcrumb(
                    segments = uiState.pathSegments,
                    modifier = Modifier.padding(
                        horizontal = if (isLandscape) 12.dp else 20.dp,
                        vertical = if (isLandscape) 4.dp else 8.dp
                    ),
                    compact = isLandscape,
                    onHomeClick = onHomeClick,
                    onSegmentClick = viewModel::navigateToSegment
                )
                val listState = rememberLazyListState()
                val showSortBar by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
                LaunchedEffect(uiState.sortBy, uiState.ascending) {
                    listState.scrollToItem(0)
                }
                AnimatedVisibility(visible = showSortBar, enter = fadeIn(), exit = fadeOut()) {
                    SortBar(
                        sortBy = uiState.sortBy,
                        ascending = uiState.ascending,
                        compact = isLandscape,
                        showEssentialFilter = viewModel.isAtRoot(),
                        showEssentialOnly = uiState.showEssentialOnly,
                        onSortChanged = viewModel::onSortChanged,
                        onToggleDirection = viewModel::toggleSortDirection,
                        onFilterChanged = viewModel::onFilterChanged
                    )
                }
                if (uiState.isLoading) { LoadingIndicator() }

                if (isLandscape && uiState.columns.isNotEmpty()) {
                    ColumnBrowseView(
                        columns = uiState.columns,
                        selection = selection,
                        onFolderClick = { columnIndex, folder ->
                            viewModel.onColumnFolderClick(columnIndex, folder)
                        },
                        onFileClick = onFileClick,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PortraitListView(
                        uiState = uiState,
                        selection = selection,
                        viewModel = viewModel,
                        onFileClick = onFileClick,
                        isLandscape = isLandscape,
                        listState = listState
                    )
                }
            }
        }

        // ── FAB hiện lại dialog khi đang ẩn ─────────────────────────────
        AnimatedVisibility(
            visible = isDialogHidden && viewModel.isOperationRunning(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { viewModel.showOperationDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "Xem tiến trình",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    // ── Progress Dialog ───────────────────────────────────────────────────
    val currentState = operationState
    if (currentState != null && !isDialogHidden) {
        OperationProgressDialog(
            title = operationTitle,
            state = currentState,
            onCancel = { viewModel.cancelOperation() },
            onDismiss = { viewModel.hideOperationDialog() }
        )
    }

    // ── Password Dialog ───────────────────────────────────────────────────
    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = {
                showPasswordDialog = false
                passwordArchivePath = ""
                passwordDestPath = ""
            },
            onConfirm = { password ->
                showPasswordDialog = false
                // Retry extraction with password
                viewModel.extractArchive(passwordArchivePath, passwordDestPath, password)
                passwordArchivePath = ""
                passwordDestPath = ""
            }
        )
    }

    // ── Create Folder Dialog ──────────────────────────────────────────────
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            currentPath = uiState.currentPath,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                showCreateFolderDialog = false
                viewModel.createFolder(folderName)
            }
        )
    }

    // ── Create File Dialog ────────────────────────────────────────────────
    if (showCreateFileDialog) {
        CreateFileDialog(
            currentPath = uiState.currentPath,
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { fileName ->
                showCreateFileDialog = false
                viewModel.createFile(fileName)
            }
        )
    }

    // ── Paste Conflict Dialog (từ menu Dán) ─────────────────────────────
    if (showPasteConflictDialog && pasteConflictData != null) {
        val data = pasteConflictData!!
        log.d("=== Paste ConflictDialog rendered ===")
        log.d("Operation: ${data.operation}")
        log.d("Conflict count: ${data.conflictCount}")
        log.d("DestPath: ${data.destPath}")

        ConflictDialog(
            conflictCount = data.conflictCount,
            operationName = "dán",
            onDismiss = {
                log.d("User chose: Thoát (cancel paste)")
                log.d("Clipboard retained — user can retry later")
                showPasteConflictDialog = false
                pasteConflictData = null
            },
            onReplace = {
                log.d("User chose: Thay thế (replace)")
                showPasteConflictDialog = false
                pasteConflictData = null
                viewModel.executePaste(
                    data.sourcePaths,
                    data.destPath,
                    data.operation,
                    replaceConflicts = true
                )
            },
            onRename = {
                log.d("User chose: Đổi tên (auto-rename)")
                showPasteConflictDialog = false
                pasteConflictData = null
                viewModel.executePaste(
                    data.sourcePaths,
                    data.destPath,
                    data.operation,
                    replaceConflicts = false
                )
            }
        )
    }
}

// ── Portrait List ─────────────────────────────────────────────────────────────

@Composable
private fun PortraitListView(
    uiState: BrowseUiState,
    selection: SelectionState,
    viewModel: BrowseViewModel,
    onFileClick: (RecentFile) -> Unit,
    isLandscape: Boolean = false,
    listState: LazyListState = rememberLazyListState()
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        val pinned = uiState.folders.filter { it.isPinned }
        val normal = uiState.folders.filter { !it.isPinned }

        folderItems(pinned, selection, viewModel, isLandscape, hasNext = normal.isNotEmpty() || uiState.files.isNotEmpty())

        if (pinned.isNotEmpty() && normal.isNotEmpty()) {
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = if (isLandscape) 4.dp else 8.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFFE0E0E0)
                )
            }
        }

        folderItems(normal, selection, viewModel, isLandscape, hasNext = uiState.files.isNotEmpty())
        fileItems(uiState.files, selection, onFileClick, isLandscape)

        if (!uiState.isLoading && uiState.folders.isEmpty() && uiState.files.isEmpty()) {
            item { EmptyState("Thư mục trống") }
        }
    }
}

private fun LazyListScope.folderItems(
    folders: List<FolderItem>,
    selection: SelectionState,
    viewModel: BrowseViewModel,
    isLandscape: Boolean = false,
    hasNext: Boolean = false
) {
    itemsIndexed(folders) { index, folder ->
        val id = "folder:${folder.path}"
        FolderListItem(
            folder = folder,
            showDivider = index < folders.lastIndex || hasNext,
            isSelectionMode = selection.isSelectionMode,
            isSelected = selection.isSelected(id),
            isLandscape = isLandscape,
            onClick = { viewModel.openFolder(folder.path, folder.name) },
            onLongClick = { selection.enterSelectionMode(id) },
            onToggleSelect = { selection.toggle(id) }
        )
    }
}

private fun LazyListScope.fileItems(
    files: List<RecentFile>,
    selection: SelectionState,
    onFileClick: (RecentFile) -> Unit,
    isLandscape: Boolean = false
) {
    itemsIndexed(files) { index, file ->
        val id = "file:${file.path}"
        FileListItem(
            file = file,
            showDivider = index < files.lastIndex,
            isSelectionMode = selection.isSelectionMode,
            isSelected = selection.isSelected(id),
            isLandscape = isLandscape,
            onClick = { onFileClick(file) },
            onLongClick = { selection.enterSelectionMode(id) },
            onToggleSelect = { selection.toggle(id) }
        )
    }
}