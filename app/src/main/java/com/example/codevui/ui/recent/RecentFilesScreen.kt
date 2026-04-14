package com.example.codevui.ui.recent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.example.codevui.data.FavoriteManager
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.common.EmptyState
import com.example.codevui.ui.common.LoadingIndicator
import com.example.codevui.ui.common.SelectableScaffold
import com.example.codevui.ui.components.FileListItem
import com.example.codevui.ui.progress.OperationProgressDialog
import com.example.codevui.ui.selection.selectionActionHandler
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecentFilesScreen(
    viewModel: RecentFilesViewModel = viewModel(),
    initialSelectedPath: String? = null,
    onBack: () -> Unit = {},
    onFileClick: (RecentFile) -> Unit = {},
    onNavigateToFolder: (String) -> Unit = {},
    onTrashClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    isLandscape: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selection = viewModel.selection

    // Observe favorite paths → overlay star icon lên file rows
    val context = LocalContext.current
    val favoritePaths by remember(context) {
        FavoriteManager.observeFavoritePaths(context)
    }.collectAsStateWithLifecycle(initialValue = emptySet())

    // Khi navigate từ Home với long click — chờ data load xong rồi select
    LaunchedEffect(initialSelectedPath, uiState.files) {
        if (initialSelectedPath != null && uiState.files.any { it.path == initialSelectedPath }) {
            selection.enterSelectionMode(initialSelectedPath)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val operationResult by viewModel.operationResult.collectAsStateWithLifecycle()
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

    // Tự dismiss dialog sau 1.5s khi Done/Error
    LaunchedEffect(operationState) {
        val state = operationState
        if (state is ProgressState.Done || state is ProgressState.Error) {
            kotlinx.coroutines.delay(1500)
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

    // Landscape → load 100 files, Portrait → load 20
    LaunchedEffect(isLandscape) {
        viewModel.load(limit = if (isLandscape) 100 else 20)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                FilterTabsRow(
                    tabs = uiState.tabs,
                    selectedTab = uiState.selectedTab,
                    isLandscape = isLandscape,
                    onTabClick = { viewModel.selectTab(it) }
                )

                if (uiState.isLoading) { LoadingIndicator() }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    val grouped = groupByDate(uiState.files)

                    grouped.forEach { (dateLabel, files) ->
                        item(key = "header:$dateLabel") { DateHeader(dateLabel) }

                        itemsIndexed(
                            items = files,
                            key = { _, file -> file.path }
                        ) { index, file ->
                            FileListItem(
                                file = file,
                                showDivider = index < files.lastIndex,
                                isSelectionMode = selection.isSelectionMode,
                                isSelected = selection.isSelected(file.path),
                                isLandscape = isLandscape,
                                isFavorite = favoritePaths.contains(file.path),
                                onClick = { onFileClick(file) },
                                onLongClick = { selection.enterSelectionMode(file.path) },
                                onToggleSelect = { selection.toggle(file.path) }
                            )
                        }
                    }

                    if (!uiState.isLoading && uiState.files.isEmpty()) {
                        item { EmptyState("Không có file gần đây") }
                    }
                }
            }
        }

        // FAB hiện lại dialog khi đang ẩn
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
                Icon(Icons.Default.Sync, contentDescription = "Xem tiến trình", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    // Progress dialog
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
 * FilterTabsRow — horizontal scroll tabs: 📋 | Tất cả | Claude | Samsung Notes | ...
 */
@Composable
private fun FilterTabsRow(
    tabs: List<String>,
    selectedTab: Int,
    isLandscape: Boolean = false,
    onTabClick: (Int) -> Unit
) {
    val horizontalPadding = if (isLandscape) 12.dp else 20.dp
    val verticalPadding = if (isLandscape) 4.dp else 8.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.FilterList,
            contentDescription = "Filter",
            tint = Color(0xFF888888),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(4.dp))

        tabs.forEachIndexed { index, tab ->
            FilterChip(
                selected = index == selectedTab,
                onClick = { onTabClick(index) },
                label = {
                    Text(
                        text = tab,
                        fontSize = 13.sp,
                        fontWeight = if (index == selectedTab) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

/**
 * DateHeader — "Hôm nay", "Hôm qua", "25 Th3 2026"
 */
@Composable
private fun DateHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF888888)
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = Color(0xFFE0E0E0)
        )
    }
}

/**
 * Group files by date label: "Hôm nay", "Hôm qua", "dd Th MM yyyy"
 */
private fun groupByDate(files: List<RecentFile>): List<Pair<String, List<RecentFile>>> {
    val now = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000

    val sdf = SimpleDateFormat("dd 'Th'M yyyy", Locale.getDefault())

    val grouped = files.groupBy { file ->
        val fileMillis = file.dateModified * 1000
        when {
            fileMillis >= todayStart -> "Hôm nay"
            fileMillis >= yesterdayStart -> "Hôm qua"
            else -> sdf.format(Date(fileMillis))
        }
    }

    // Preserve order: Hôm nay → Hôm qua → older dates
    return grouped.entries.toList().map { it.key to it.value }
}