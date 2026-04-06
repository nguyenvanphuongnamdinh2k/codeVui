package com.example.codevui.ui.unusedapps

import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.util.formatFileSize
import com.example.codevui.util.Logger

private val log = Logger("UnusedAppsScreen")

// ══════════════════════════════════════════════════════
// UnusedAppsScreen — Main screen
// ══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnusedAppsScreen(
    viewModel: UnusedAppsViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selection = viewModel.selection
    val context = LocalContext.current

    // Check usage stats permission
    var hasUsagePermission by remember {
        val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mutableStateOf(mode == AppOpsManager.MODE_ALLOWED)
    }

    // Nếu chưa có permission, hiện dialog hướng dẫn
    if (!hasUsagePermission) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Cần quyền truy cập") },
            text = {
                Text("Để xem ứng dụng không dùng, cần bật quyền 'Quyền truy cập dữ liệu sử dụng' cho CodeVui trong Cài đặt.")
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    // Re-check khi quay lại
                    hasUsagePermission = true  // sẽ re-check khi recompose
                }) {
                    Text("Mở Cài đặt")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("Hủy")
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selection.isSelectionMode) {
                        // "Tất cả" checkbox + "Chọn mục" title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(4.dp))
                            val allIds = uiState.apps.map { it.packageName }
                            SelectionCheckbox(
                                isSelected = selection.selectedIds.size == allIds.size && allIds.isNotEmpty(),
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
                        Text("Chọn mục", fontWeight = FontWeight.Bold)
                    } else {
                        // Empty — title hiển thị trong body
                    }
                },
                actions = {
                    if (selection.isSelectionMode) {
                        TextButton(onClick = { selection.exit() }) {
                            Text("Thoát", color = Color(0xFF1A73E8))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            // Bottom bar khi có selection
            if (selection.isSelectionMode && selection.selectedIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                // Mở App Info cho từng app đã chọn
                                selection.selectedIds.forEach { pkgName ->
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:$pkgName")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) { }
                                }
                                selection.exit()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A73E8)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Thông tin ứng dụng (${selection.selectedIds.size})")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Đang quét ứng dụng...", color = Color.Gray)
                }
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.error ?: "", color = Color.Red)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header: "N ứng dụng không dùng (X GB)"
                if (!selection.isSelectionMode) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${uiState.totalApps} ứng dụng không\ndùng",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 32.sp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "(${formatFileSize(uiState.totalSize)})",
                                    fontSize = 18.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Giải phóng một số dung lượng bằng cách gỡ cài đặt hoặc lưu trữ các ứng dụng mà bạn không dùng trong 30 ngày qua.",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Tab row: Tất cả | Cho phép lưu trữ
                item {
                    Spacer(Modifier.height(12.dp))
                    TabSelector(
                        currentTab = uiState.tab,
                        onTabChange = { viewModel.setTab(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Sort bar
                item {
                    SortRow(
                        sortBy = uiState.sortBy,
                        ascending = uiState.sortAscending,
                        onSortChange = { viewModel.setSortBy(it) }
                    )
                }

                // App list
                if (uiState.apps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Không có ứng dụng không dùng", color = Color.Gray)
                        }
                    }
                } else {
                    itemsIndexed(uiState.apps) { _, app ->
                        UnusedAppItem(
                            app = app,
                            isSelected = selection.isSelected(app.packageName),
                            isSelectionMode = selection.isSelectionMode,
                            selection = selection,
                            onSettingsClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            }
                        )
                    }
                }

                // Bottom spacing
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// TabSelector — Tất cả / Cho phép lưu trữ
// ══════════════════════════════════════════════════════

@Composable
private fun TabSelector(
    currentTab: UnusedAppsTab,
    onTabChange: (UnusedAppsTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF0F0F0))
    ) {
        UnusedAppsTab.entries.forEach { tab ->
            val isActive = tab == currentTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (isActive) Modifier.background(Color.White)
                        else Modifier
                    )
                    .clickable { onTabChange(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    color = if (isActive) Color.Black else Color.Gray
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════
// SortRow — Sort by Kích thước ↓
// ══════════════════════════════════════════════════════

@Composable
private fun SortRow(
    sortBy: UnusedAppsSortBy,
    ascending: Boolean,
    onSortChange: (UnusedAppsSortBy) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort icon
        Icon(
            imageVector = Icons.Default.Settings,  // placeholder — dùng filter icon
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.Gray
        )
        Spacer(Modifier.width(4.dp))

        // Sort label (clickable)
        Text(
            text = sortBy.label,
            fontSize = 13.sp,
            color = Color(0xFF555555),
            modifier = Modifier.clickable {
                // Cycle qua các sort types
                val next = when (sortBy) {
                    UnusedAppsSortBy.SIZE -> UnusedAppsSortBy.NAME
                    UnusedAppsSortBy.NAME -> UnusedAppsSortBy.LAST_USED
                    UnusedAppsSortBy.LAST_USED -> UnusedAppsSortBy.SIZE
                }
                onSortChange(next)
            }
        )
        Spacer(Modifier.width(4.dp))

        // Direction arrow
        Text(
            text = "|",
            fontSize = 13.sp,
            color = Color(0xFFCCCCCC)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (ascending) "↑" else "↓",
            fontSize = 16.sp,
            color = Color(0xFF555555),
            modifier = Modifier.clickable { onSortChange(sortBy) }
        )
    }
}

// ══════════════════════════════════════════════════════
// UnusedAppItem — App row
// ══════════════════════════════════════════════════════

@Composable
private fun UnusedAppItem(
    app: UnusedAppInfo,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    selection: com.example.codevui.ui.selection.SelectionState,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (isSelectionMode && isSelected) {
                    mod.background(Color(0xFFE8F0FE))
                } else mod
            }
            .clickable {
                if (isSelectionMode) {
                    selection.toggle(app.packageName)
                } else {
                    // Long press enter selection, hoặc click mở settings
                    onSettingsClick()
                }
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox (luôn hiện, giống screenshot)
        SelectionCheckbox(
            isSelected = isSelected,
            onClick = {
                if (!isSelectionMode) {
                    selection.enterSelectionMode(app.packageName)
                } else {
                    selection.toggle(app.packageName)
                }
            }
        )

        Spacer(Modifier.width(12.dp))

        // App icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = remember(app.packageName) {
                try {
                    app.icon?.toBitmap(96, 96)
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                AppIconPlaceholder(app.appName)
            }
        }

        Spacer(Modifier.width(12.dp))

        // Name + size
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatFileSize(app.sizeBytes),
                fontSize = 13.sp,
                color = Color.Gray
            )
        }

        // Settings gear icon
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Cài đặt ứng dụng",
                tint = Color(0xFF999999),
                modifier = Modifier.size(22.dp)
            )
        }
    }

    // Divider
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp, end = 20.dp),
        thickness = 0.5.dp,
        color = Color(0xFFEEEEEE)
    )
}

@Composable
private fun AppIconPlaceholder(name: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color(0xFFE0E0E0), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF666666)
        )
    }
}
