package com.example.codevui.ui.browse.columnview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.selection.SelectionState

@Composable
fun ColumnBrowseView(
    columns: List<ColumnData>,
    selection: SelectionState,
    onFolderClick: (columnIndex: Int, folder: FolderItem) -> Unit,
    onFileClick: (RecentFile) -> Unit,
    favoritePaths: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val columnWidth = maxOf(280.dp, screenWidth * 0.38f)

    // Active column = column có path khớp với SelectionState.activeContextKey.
    // Nếu chưa có activeContextKey (chưa enter selection mode) → tất cả columns
    // đều cho phép long-press để bắt đầu selection.
    // Nếu activeContextKey đã set nhưng không match column nào (VD column đã bị
    // navigate khỏi) → không column nào active để tránh thao tác nhầm.
    val activeContextKey = selection.activeContextKey

    LaunchedEffect(columns.size) {
        if (columns.isNotEmpty()) {
            listState.animateScrollToItem(columns.lastIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        itemsIndexed(
            items = columns,
            key = { _, col -> col.path }
        ) { index, column ->
            Row {
                val isActive = activeContextKey == null || activeContextKey == column.path
                ColumnPanel(
                    column = column,
                    selection = selection,
                    columnWidth = columnWidth,
                    isActiveColumn = isActive,
                    favoritePaths = favoritePaths,
                    onFolderClick = { folder -> onFolderClick(index, folder) },
                    onFileClick = onFileClick
                )
                if (index < columns.lastIndex) {
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = 0.5.dp,
                        color = Color(0xFFE0E0E0)
                    )
                }
            }
        }
    }
}
