package com.example.codevui.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.ui.components.SectionHeader
import com.example.codevui.ui.components.SectionTitle
import com.example.codevui.ui.home.sections.CategoriesGrid
import com.example.codevui.ui.home.sections.RecentFilesRow
import com.example.codevui.ui.home.sections.StorageList
import androidx.core.net.toUri

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onCategoryClick: (com.example.codevui.model.CategoryItem) -> Unit = {},
    onStorageClick: (com.example.codevui.model.StorageItem) -> Unit = {},
    onRecentSectionClick: () -> Unit = {},
    onRecentFileClick: (com.example.codevui.model.RecentFile) -> Unit = {},
    onRecentFileLongClick: (com.example.codevui.model.RecentFile) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onStorageInfoLoaded: (used: String, total: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    // Permission handling
    val context = LocalContext.current
    val storagePermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // MANAGE_EXTERNAL_STORAGE launcher cho Android 11+ (cần cho write/copy/move)
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        ) {
            viewModel.onPermissionGranted()
        }
    }

    // POST_NOTIFICATIONS launcher cho Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted hay không cũng không cần làm gì */ }

    LaunchedEffect(Unit) {
        // Xin notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PermissionChecker.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: cần MANAGE_EXTERNAL_STORAGE cho full file access
            if (Environment.isExternalStorageManager()) {
                viewModel.onPermissionGranted()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                }
                manageStorageLauncher.launch(intent)
            }
        } else {
            // Android 10 trở xuống
            val allGranted = storagePermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) ==
                        PermissionChecker.PERMISSION_GRANTED
            }
            if (allGranted) {
                viewModel.onPermissionGranted()
            } else {
                permissionLauncher.launch(storagePermissions)
            }
        }
    }

    // Report storage info lên MainViewModel cho DrawerPane
    LaunchedEffect(uiState.storageItems) {
        val storage = uiState.storageItems.find { it.title == "Bộ nhớ trong" }
        if (storage?.used != null && storage.total != null) {
            onStorageInfoLoaded(storage.used, storage.total)
        }
    }

    HomeScreenContent(
        uiState = uiState,
        onCategoryClick = onCategoryClick,
        onStorageClick = onStorageClick,
        onRecentSectionClick = onRecentSectionClick,
        onRecentFileClick = onRecentFileClick,
        onRecentFileLongClick = onRecentFileLongClick,
        onSearchClick = onSearchClick,
        onTrashClick = onTrashClick,
        onFavoritesClick = onFavoritesClick,
        onRetryPermission = { permissionLauncher.launch(storagePermissions) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onCategoryClick: (com.example.codevui.model.CategoryItem) -> Unit = {},
    onStorageClick: (com.example.codevui.model.StorageItem) -> Unit = {},
    onRecentFileClick: (com.example.codevui.model.RecentFile) -> Unit = {},
    onRecentFileLongClick: (com.example.codevui.model.RecentFile) -> Unit = {},
    onRecentSectionClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onRetryPermission: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
                    }
                    IconButton(onClick = onFavoritesClick) {
                        Icon(Icons.Outlined.Star, contentDescription = "Yêu thích", tint = Color(0xFFFFB300))
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
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
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onTrashClick()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Categories
            CategoriesGrid(
                categories = uiState.categories,
                onClick = { item -> onCategoryClick(item) }
            )

            // Permission denied banner
            if (uiState.isPermissionDenied) {
                PermissionBanner(onRetryPermission = onRetryPermission)
            }

            // Loading indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            // Recent Files
            if (uiState.recentFiles.isNotEmpty()) {
                SectionHeader(
                    text = if (uiState.newFileCount > 0)
                        "Đã thêm ${uiState.newFileCount} file mới gần đây"
                    else
                        "Gần đây",
                    showBadge = uiState.newFileCount > 0,
                    onClick = onRecentSectionClick
                )
                RecentFilesRow(
                    files = uiState.recentFiles,
                    onClick = { file -> onRecentFileClick(file) },
                    onLongClick = { file -> onRecentFileLongClick(file) }
                )
            }

            // Storage
            if (uiState.storageItems.isNotEmpty()) {
                SectionTitle("Lưu trữ")
                StorageList(
                    items = uiState.storageItems,
                    onClick = { item -> onStorageClick(item) }
                )
            }

            // Utilities
            if (uiState.utilityItems.isNotEmpty()) {
                SectionTitle("Tiện ích")
                StorageList(
                    items = uiState.utilityItems,
                    onClick = { item -> onStorageClick(item) }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionBanner(
    onRetryPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cần cấp quyền truy cập bộ nhớ để hiển thị file",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRetryPermission) {
                Text("Cấp quyền")
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen()
    }
}