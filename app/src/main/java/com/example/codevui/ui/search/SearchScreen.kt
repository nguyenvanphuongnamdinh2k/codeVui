package com.example.codevui.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.example.codevui.data.FavoriteManager
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.model.FileType
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.common.SelectableScaffold
import com.example.codevui.ui.components.FileListItem
import com.example.codevui.ui.components.FileThumbnail
import com.example.codevui.ui.components.FolderListItem
import com.example.codevui.ui.components.HighlightedText
import com.example.codevui.ui.progress.OperationProgressDialog
import com.example.codevui.ui.selection.selectionActionHandler
import com.example.codevui.util.formatDateFull
import com.example.codevui.util.formatFileSize
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel(),
    onBack: () -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onFavoritesClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection = viewModel.selection
    val snackbarHostState = remember { SnackbarHostState() }
    val operationResult by viewModel.operationResult.collectAsState()

    // Observe favorite paths → overlay star icon lên file/folder rows
    val context = LocalContext.current
    val favoritePaths by remember(context) {
        FavoriteManager.observeFavoritePaths(context)
    }.collectAsState(initial = emptySet())
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // ── Operation progress state ──────────────────────────────────────────
    val operationState by viewModel.operationState.collectAsState()
    val operationTitle by viewModel.operationTitle.collectAsState()
    val isDialogHidden by viewModel.isDialogHidden.collectAsState()

    val actions = selectionActionHandler(
        selectionState = selection,
        fileActionState = viewModel.fileAction,
        onOperationComplete = { viewModel.reload() },
        onCopyFiles = { paths, dest, _ -> viewModel.copyFiles(paths, dest, null) },
        onMoveFiles = { paths, dest, _ -> viewModel.moveFiles(paths, dest, null) },
        onCompressFiles = { paths, zipName -> viewModel.compressFiles(paths, zipName) }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Tự dismiss dialog 1.5s sau khi Done/Error
    LaunchedEffect(operationState) {
        val state = operationState
        if (state is ProgressState.Done || state is ProgressState.Error) {
            delay(1500)
            viewModel.dismissOperationResult()
        }
    }

    // Hiển thị Snackbar khi operation hoàn thành
    LaunchedEffect(operationResult) {
        operationResult?.let { result ->
            val message = when {
                result.failed == 0 -> "${result.actionName} ${result.success} mục thành công"
                result.success == 0 -> "${result.actionName} thất bại (${result.failed} mục lỗi)"
                else -> "${result.actionName} ${result.success} thành công, ${result.failed} lỗi"
            }
            val snackbarResult = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Mở folder đích",
                duration = SnackbarDuration.Long
            )
            if (snackbarResult == SnackbarResult.ActionPerformed) {
                onNavigateToFolder(result.destPath)
            }
            viewModel.clearOperationResult()
        }
    }

    val allIds = remember(uiState.folders, uiState.files) {
        uiState.folders.map { "folder:${it.path}" } + uiState.files.map { "file:${it.path}" }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SelectableScaffold(
            selection = selection,
            actions = actions,
            totalCount = allIds.size,
            allIds = allIds,
            onBack = onBack,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            normalTopBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    },
                    title = { },
                    actions = {
                        IconButton(onClick = onFavoritesClick) {
                            Icon(
                                Icons.Outlined.Star,
                                contentDescription = "Yêu thích",
                                tint = Color(0xFFFFB300)
                            )
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
        ) {
            // Filter header — "Bộ lọc" expandable
            FilterSection(
                uiState = uiState,
                onToggleFilter = { viewModel.toggleFilter() },
                onTimeFilterClick = { viewModel.setTimeFilter(it) },
                onTypeFilterClick = { viewModel.toggleTypeFilter(it) },
                onSearchInContentToggle = { viewModel.toggleSearchInContent() }
            )

            // Search bar
            SearchBar(
                query = uiState.query,
                onQueryChanged = { viewModel.onQueryChanged(it) },
                onSearch = {
                    keyboardController?.hide()
                    viewModel.search()
                },
                focusRequester = focusRequester
            )

            Spacer(Modifier.height(8.dp))

            // Loading
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            // Results
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Results count header: "Bộ nhớ trong (19 mục)"
                if (uiState.hasSearched && !uiState.isLoading && uiState.totalResults > 0) {
                    item(key = "header") {
                        ResultsHeader(
                            count = uiState.totalResults,
                            isExpanded = true
                        )
                    }
                }

                // Folders
                itemsIndexed(
                    items = uiState.folders,
                    key = { _, folder -> "folder:${folder.path}" }
                ) { index, folder ->
                    val id = "folder:${folder.path}"
                    FolderListItem(
                        folder = folder,
                        showDivider = index < uiState.folders.lastIndex || uiState.files.isNotEmpty(),
                        isSelectionMode = selection.isSelectionMode,
                        isSelected = selection.isSelected(id),
                        isLandscape = isLandscape,
                        isFavorite = favoritePaths.contains(folder.path),
                        onClick = { onNavigateToFolder(folder.path) },
                        onLongClick = { selection.enterSelectionMode(id) },
                        onToggleSelect = { selection.toggle(id) }
                    )
                }

                // Files
                itemsIndexed(
                    items = uiState.files,
                    key = { _, file -> "file:${file.path}" }
                ) { index, file ->
                    val id = "file:${file.path}"
                    FileListItem(
                        file = file,
                        showDivider = index < uiState.files.lastIndex,
                        isSelectionMode = selection.isSelectionMode,
                        isSelected = selection.isSelected(id),
                        isLandscape = isLandscape,
                        isFavorite = favoritePaths.contains(file.path),
                        onClick = { onFileClick(file) },
                        onLongClick = { selection.enterSelectionMode(id) },
                        onToggleSelect = { selection.toggle(id) }
                    )
                }

                // Empty state
                if (uiState.hasSearched && !uiState.isLoading && uiState.totalResults == 0) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Không tìm thấy kết quả", color = Color(0xFF999999))
                        }
                    }
                }
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
}

/**
 * ResultsHeader — "Bộ nhớ trong (X mục)" with expand/collapse arrow
 */
@Composable
private fun ResultsHeader(
    count: Int,
    isExpanded: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Bộ nhớ trong",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A)
        )
        Text(
            text = " ($count mục)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF888888)
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = Color(0xFF888888),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * SearchFileItem — file row with highlighted name, reuses FileThumbnail
 */
@Composable
private fun SearchFileItem(
    file: RecentFile,
    query: String,
    showDivider: Boolean = true,
    onClick: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnail(
                file = file,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                HighlightedText(
                    text = file.name,
                    query = query,
                    fontSize = 15.sp
                )
                if (file.ownerApp != null) {
                    Text(
                        text = file.ownerApp,
                        fontSize = 12.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        text = formatDateFull(file.dateModified),
                        fontSize = 13.sp,
                        color = Color(0xFF999999)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatFileSize(file.size),
                        fontSize = 13.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 92.dp, end = 20.dp),
                thickness = 0.5.dp,
                color = Color(0xFFF0F0F0)
            )
        }
    }
}

/**
 * SearchFolderItem — folder row with highlighted name
 */
@Composable
private fun SearchFolderItem(
    folder: com.example.codevui.model.FolderItem,
    query: String,
    showDivider: Boolean = true,
    onClick: () -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📁", fontSize = 36.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                HighlightedText(
                    text = folder.name,
                    query = query,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = formatDateFull(folder.dateModified),
                        fontSize = 13.sp,
                        color = Color(0xFF999999)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${folder.itemCount} mục",
                        fontSize = 13.sp,
                        color = Color(0xFF999999)
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 92.dp, end = 20.dp),
                thickness = 0.5.dp,
                color = Color(0xFFF0F0F0)
            )
        }
    }
}

@Composable
private fun FilterSection(
    uiState: SearchUiState,
    onToggleFilter: () -> Unit,
    onTimeFilterClick: (TimeFilter) -> Unit,
    onTypeFilterClick: (FileType) -> Unit,
    onSearchInContentToggle: () -> Unit
) {
    // "Bộ lọc" header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Bộ lọc",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A1A),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleFilter) {
            Icon(
                imageVector = if (uiState.isFilterExpanded) Icons.Default.ExpandLess
                else Icons.Default.ExpandMore,
                contentDescription = "Toggle filter",
                tint = Color(0xFF666666)
            )
        }
    }

    // Expandable filter content
    AnimatedVisibility(
        visible = uiState.isFilterExpanded,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // "Tìm kiếm file bên trong" toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tìm kiếm file bên trong",
                    fontSize = 14.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.searchInContent,
                    onCheckedChange = { onSearchInContentToggle() }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Filter card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Thời gian
                    Text(
                        text = "Thời gian",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = uiState.selectedTimeFilter == filter,
                                onClick = { onTimeFilterClick(filter) },
                                label = { Text(filter.label, fontSize = 13.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Loại
                    Text(
                        text = "Loại",
                        fontSize = 13.sp,
                        color = Color(0xFF888888)
                    )
                    Spacer(Modifier.height(8.dp))

                    val typeFilters = listOf(
                        FileType.IMAGE to "Ảnh",
                        FileType.VIDEO to "Video",
                        FileType.AUDIO to "Âm thanh",
                        FileType.DOC to "Tài liệu",
                        FileType.APK to "File cài đặt",
                        FileType.ARCHIVE to "Đã nén"
                    )

                    // Row 1
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        typeFilters.take(4).forEach { (type, label) ->
                            FilterChip(
                                selected = type in uiState.selectedTypes,
                                onClick = { onTypeFilterClick(type) },
                                label = { Text(label, fontSize = 13.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Row 2
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        typeFilters.drop(4).forEach { (type, label) ->
                            FilterChip(
                                selected = type in uiState.selectedTypes,
                                onClick = { onTypeFilterClick(type) },
                                label = { Text(label, fontSize = 13.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .focusRequester(focusRequester),
        placeholder = {
            Text("Tìm kiếm", color = Color(0xFFAAAAAA))
        },
        leadingIcon = null,
        trailingIcon = {
            Row {
                // Mic icon placeholder
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF888888)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF5F5F5),
            unfocusedContainerColor = Color(0xFFF5F5F5),
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() })
    )
}