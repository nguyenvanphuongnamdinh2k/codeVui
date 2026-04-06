package com.example.codevui.ui.selection

import android.content.res.Configuration
import android.os.Environment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.codevui.data.FileOperations
import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile
import com.example.codevui.ui.components.Breadcrumb
import com.example.codevui.ui.components.FolderListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Folder picker bottom sheet replacement using Dialog.
 * Uses Dialog instead of ModalBottomSheet to avoid drag-to-dismiss
 * interfering with the folder list scroll.
 */
@Composable
fun FolderPickerSheet(
    operationType: FileOperations.OperationType,
    selectionState: SelectionState? = null,
    onDismiss: () -> Unit,
    onConfirm: (destPath: String) -> Unit
) {
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }

    var currentPath by remember { mutableStateOf(rootPath) }
    var pathSegments by remember { mutableStateOf(listOf("Bộ nhớ trong")) }
    var pathStack by remember { mutableStateOf(listOf(rootPath)) }
    var folders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var files by remember { mutableStateOf<List<RecentFile>>(emptyList()) }

    // Load folder contents
    LaunchedEffect(currentPath) {
        val loadedFolders = withContext(Dispatchers.IO) {
            val dir = File(currentPath)
            val children = dir.listFiles()
                ?.filter { !it.name.startsWith(".") && it.isDirectory }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()

            children.map { child ->
                FolderItem(
                    name = child.name,
                    path = child.absolutePath,
                    dateModified = child.lastModified() / 1000,
                    itemCount = child.listFiles()?.size ?: 0
                )
            }
        }
        folders = loadedFolders
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val compact = isLandscape

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { } // consume outside clicks — only dismiss via Thoát button
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(if (compact) 1f else 0.9f)
                    .clip(RoundedCornerShape(topStart = if (compact) 12.dp else 20.dp, topEnd = if (compact) 12.dp else 20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { } // consume inner clicks so they don't bubble to overlay
                    ),
                color = Color.White,
                shape = RoundedCornerShape(topStart = if (compact) 12.dp else 20.dp, topEnd = if (compact) 12.dp else 20.dp),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // Header: "File của bạn" + icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (compact) 12.dp else 20.dp,
                                vertical = if (compact) 2.dp else 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "File của bạn",
                            fontSize = if (compact) 16.sp else 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { }, modifier = if (compact) Modifier.size(32.dp) else Modifier) {
                            Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = Color(0xFF444444),
                                modifier = if (compact) Modifier.size(18.dp) else Modifier)
                        }
                        IconButton(onClick = { }, modifier = if (compact) Modifier.size(32.dp) else Modifier) {
                            Icon(Icons.Default.Add, contentDescription = "Tạo thư mục", tint = Color(0xFF444444),
                                modifier = if (compact) Modifier.size(18.dp) else Modifier)
                        }
                        IconButton(onClick = { }, modifier = if (compact) Modifier.size(32.dp) else Modifier) {
                            Icon(Icons.Outlined.FilterList, contentDescription = "Lọc", tint = Color(0xFF444444),
                                modifier = if (compact) Modifier.size(18.dp) else Modifier)
                        }
                    }

                    // Storage tab
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (compact) 12.dp else 20.dp,
                            vertical = if (compact) 2.dp else 4.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = { },
                            label = { Text("Bộ nhớ trong", fontSize = if (compact) 11.sp else 13.sp) }
                        )
                    }

                    // Breadcrumb
                    Breadcrumb(
                        segments = pathSegments,
                        modifier = Modifier.padding(
                            horizontal = if (compact) 12.dp else 20.dp,
                            vertical = if (compact) 4.dp else 8.dp
                        ),
                        compact = compact,
                        onHomeClick = {
                            currentPath = rootPath
                            pathSegments = listOf("Bộ nhớ trong")
                            pathStack = listOf(rootPath)
                        },
                        onSegmentClick = { index ->
                            if (index < pathStack.size) {
                                currentPath = pathStack[index]
                                pathSegments = pathSegments.subList(0, index + 1)
                                pathStack = pathStack.subList(0, index + 1)
                            }
                        }
                    )

                    // Sort bar (simplified)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (compact) 12.dp else 20.dp,
                                vertical = if (compact) 2.dp else 4.dp
                            ),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tên", fontSize = if (compact) 11.sp else 13.sp, color = Color(0xFF888888))
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))

                    // Folder list — reuse FolderListItem
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(
                            items = folders,
                            key = { _, folder -> folder.path }
                        ) { index, folder ->
                            FolderListItem(
                                folder = folder,
                                showDivider = index < folders.lastIndex,
                                isLandscape = isLandscape,
                                onClick = {
                                    currentPath = folder.path
                                    pathSegments = pathSegments + folder.name
                                    pathStack = pathStack + folder.path
                                }
                            )
                        }

                        if (folders.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Không có thư mục con", color = Color(0xFF999999), fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Bottom buttons
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFE0E0E0))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (compact) 12.dp else 20.dp,
                                vertical = if (compact) 6.dp else 12.dp
                            ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "Thoát",
                                fontSize = if (compact) 13.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A1A)
                            )
                        }
                        TextButton(
                            onClick = { onConfirm(currentPath) }
                        ) {
                            Text(
                                text = if (operationType == FileOperations.OperationType.MOVE)
                                    "Chuyển đến đây" else "Sao chép đến đây",
                                fontSize = if (compact) 13.sp else 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1A73E8)
                            )
                        }
                    }
                }
            }
        }
    }
}
