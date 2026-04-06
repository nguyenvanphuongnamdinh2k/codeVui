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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val columnWidth = maxOf(280.dp, screenWidth * 0.38f)

    // Tìm column nào đang có items được selected
    // Trả về -1 nếu chưa select gì (cho phép tất cả columns long-press)
    val activeSelectionColumnIndex = remember(columns, selection.selectedIds) {
        if (selection.selectedIds.isEmpty()) {
            -1  // Chưa select gì → cho phép tất cả columns
        } else {
            // Tìm column nào có item được selected
            columns.indexOfFirst { column ->
                val columnItemIds = column.folders.map { "folder:${it.path}" } +
                                  column.files.map { "file:${it.path}" }
                selection.selectedIds.any { it in columnItemIds }
            }
        }
    }

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
                ColumnPanel(
                    column = column,
                    selection = selection,
                    columnWidth = columnWidth,
                    isActiveColumn = activeSelectionColumnIndex == -1 || index == activeSelectionColumnIndex,  // -1 = cho phép tất cả, otherwise chỉ column có selected items
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
