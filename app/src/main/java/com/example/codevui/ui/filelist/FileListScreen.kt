package com.example.codevui.ui.filelist

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.data.FavoriteManager
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.model.FileType
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.common.EmptyState
import com.example.codevui.ui.common.LoadingIndicator
import com.example.codevui.ui.common.SelectableScaffold
import com.example.codevui.ui.components.Breadcrumb
import com.example.codevui.ui.components.FileListItem
import com.example.codevui.ui.components.SortBar
import com.example.codevui.ui.progress.OperationProgressDialog
import com.example.codevui.ui.selection.selectionActionHandler
import com.example.codevui.util.Logger
import kotlinx.coroutines.delay

private val log = Logger("FileListScreen")

@Composable
fun FileListScreen(
    fileType: FileType,
    title: String,
    viewModel: FileListViewModel = viewModel(),
    onBack: () -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onTrashClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selection = viewModel.selection
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe favorite paths → overlay star icon lên file rows (giống BrowseScreen)
    val context = LocalContext.current
    val favoritePaths by remember(context) {
        FavoriteManager.observeFavoritePaths(context)
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val operationResult by viewModel.operationResult.collectAsStateWithLifecycle()

    // ── Operation progress state ──────────────────────────────────────────
    val operationState by viewModel.operationState.collectAsStateWithLifecycle()
    val operationTitle by viewModel.operationTitle.collectAsStateWithLifecycle()
    val isDialogHidden by viewModel.isDialogHidden.collectAsStateWithLifecycle()

    val actions = selectionActionHandler(
        selectionState = selection,
        fileActionState = viewModel.fileAction,
        onOperationComplete = { viewModel.reload() },
        onCopyFiles = { paths, dest, _ -> viewModel.copyFiles(paths, dest, null) },
        onMoveFiles = { paths, dest, _ -> viewModel.moveFiles(paths, dest, null) },
        onCompressFiles = { paths, zipName -> viewModel.compressFiles(paths, zipName) }
    )
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(fileType) { viewModel.loadFiles(fileType, title) }

    // Tự dismiss dialog 1.5s sau khi Done/Error
    LaunchedEffect(operationState) {
        val state = operationState
        if (state is ProgressState.Done || state is ProgressState.Error) {
            delay(500)
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

    Box(modifier = Modifier.fillMaxSize()) {
        SelectableScaffold(
            selection = selection,
            actions = actions,
            totalCount = uiState.files.size,
            allIds = uiState.files.map { it.path },
            onBack = onBack,
            onTrashClick = onTrashClick,
            onFavoritesClick = onFavoritesClick,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Breadcrumb(
                segments = listOf(uiState.title),
                trailing = uiState.totalSize,
                count = uiState.files.size,
                modifier = Modifier.padding(
                    horizontal = if (isLandscape) 12.dp else 20.dp,
                    vertical = if (isLandscape) 4.dp else 8.dp
                ),
                compact = isLandscape,
                onHomeClick = onBack
            )
            val listState = rememberLazyListState()
            val showSortBar by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
            LaunchedEffect(uiState.sortBy, uiState.ascending) {
                log.d("sort changed: sortBy=${uiState.sortBy} ascending=${uiState.ascending} firstVisibleIndex=${listState.firstVisibleItemIndex} firstVisibleOffset=${listState.firstVisibleItemScrollOffset}")
                listState.scrollToItem(0)
                log.d("sort scrollToItem(0) done: firstVisibleIndex=${listState.firstVisibleItemIndex}")
            }
            AnimatedVisibility(visible = showSortBar, enter = fadeIn(), exit = fadeOut()) {
                SortBar(
                    sortBy = uiState.sortBy,
                    ascending = uiState.ascending,
                    compact = isLandscape,
                    onSortChanged = viewModel::onSortChanged,
                    onToggleDirection = viewModel::toggleSortDirection
                )
            }
            if (uiState.isLoading) { LoadingIndicator() }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(uiState.files, key = { _, file -> file.path }) { index, file ->
                    FileListItem(
                        file = file,
                        showDivider = index < uiState.files.lastIndex,
                        isSelectionMode = selection.isSelectionMode,
                        isSelected = selection.isSelected(file.path),
                        isLandscape = isLandscape,
                        isFavorite = favoritePaths.contains(file.path),
                        onClick = { onFileClick(file) },
                        onLongClick = { selection.enterSelectionMode(file.path) },
                        onToggleSelect = { selection.toggle(file.path) }
                    )
                }
                if (!uiState.isLoading && uiState.files.isEmpty()) {
                    item { EmptyState("Không có file nào") }
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
