package com.example.codevui.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.codevui.ui.selection.SelectionActions
import com.example.codevui.ui.selection.SelectionBottomBar
import com.example.codevui.ui.selection.SelectionState
import com.example.codevui.ui.selection.SelectionTopBar

/**
 * SelectableScaffold — reusable Scaffold wrapper with selection mode support
 *
 * Eliminates ~50 lines of duplicate Scaffold/TopBar/BottomBar/BackHandler code
 * that was copy-pasted across FileListScreen, BrowseScreen, RecentFilesScreen.
 *
 * Usage:
 * ```
 * SelectableScaffold(
 *     selection = selection,
 *     actions = actions,
 *     totalCount = items.size,
 *     allIds = items.map { it.path },
 *     onBack = onBack
 * ) { padding ->
 *     // Your content here
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectableScaffold(
    selection: SelectionState,
    actions: SelectionActions,
    totalCount: Int,
    allIds: List<String>,
    onBack: () -> Unit,
    onSearchClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onFavoritesClick: (() -> Unit)? = null,
    normalTopBar: (@Composable () -> Unit)? = null,
    snackbarHost: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    // Back exits selection mode first
    BackHandler(enabled = selection.isSelectionMode) {
        selection.exit()
    }

    Scaffold(
        topBar = {
            if (selection.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = selection.selectedCount,
                    totalCount = totalCount,
                    onSelectAll = { selection.selectAll(allIds) },
                    onExit = { selection.exit() }
                )
            } else {
                normalTopBar?.invoke() ?: DefaultTopBar(
                    onBack = onBack,
                    onSearchClick = onSearchClick,
                    onTrashClick = onTrashClick,
                    onFavoritesClick = onFavoritesClick
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selection.isSelectionMode && selection.selectedCount > 0,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                SelectionBottomBar(
                    onMove = actions.onMove,
                    onCopy = actions.onCopy,
                    onDelete = actions.onDelete,
                    onShare = actions.onShare,
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
        },
        snackbarHost = snackbarHost ?: {},
        containerColor = Color.White,
        content = content
    )
}

/**
 * DefaultTopBar — default back + search + more top bar
 * Reused when normalTopBar is not provided
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultTopBar(
    onBack: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onFavoritesClick: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }
        },
        actions = {
            if (onFavoritesClick != null) {
                IconButton(onClick = onFavoritesClick) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = "Yêu thích",
                        tint = Color(0xFFFFB300)
                    )
                }
            }
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Thêm")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Thùng rác") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onTrashClick()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
    )
}

/**
 * EmptyState — reusable empty state message
 * Was duplicated in FileListScreen, BrowseScreen, RecentFilesScreen
 */
@Composable
fun EmptyState(
    message: String = "Không có file nào",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(message, color = Color(0xFF999999))
    }
}

/**
 * LoadingIndicator — reusable loading spinner
 * Was duplicated across all screens
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
    }
}