package com.example.codevui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import com.example.codevui.data.DefaultAppManager
import com.example.codevui.model.FileType
import com.example.codevui.model.RecentFile
import com.example.codevui.service.FileOperationService
import com.example.codevui.ui.common.dialogs.OpenWithDialog
import com.example.codevui.ui.common.dialogs.openFileWithApp
import com.example.codevui.ui.common.dialogs.openFileWithSavedDefault
import com.example.codevui.ui.archive.ArchiveScreen
import com.example.codevui.ui.browse.BrowseScreen
import com.example.codevui.ui.common.AdaptiveLayout
import com.example.codevui.ui.filelist.FileListScreen
import com.example.codevui.ui.home.HomeScreen
import com.example.codevui.ui.recent.RecentFilesScreen
import com.example.codevui.ui.search.SearchScreen
import com.example.codevui.ui.duplicates.DuplicatesScreen
import com.example.codevui.ui.largefiles.LargeFilesScreen
import com.example.codevui.ui.unusedapps.UnusedAppsScreen
import com.example.codevui.ui.storage.StorageManagerScreen
import com.example.codevui.ui.recommend.RecommendScreen
import com.example.codevui.ui.favorites.FavoritesScreen
import com.example.codevui.ui.theme.CodeVuiTheme
import com.example.codevui.ui.texteditor.TextEditorScreen
import com.example.codevui.ui.trash.TrashScreen
import com.example.codevui.util.Logger
import java.io.File

private val log = Logger("MainActivity")

class MainActivity : ComponentActivity() {

    private var pendingFolderPath: String? = null
    private var pendingArchivePreview: Pair<String, String>? = null  // (archivePath, archiveName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Coil.setImageLoader(AppImageLoader.get(this))
        enableEdgeToEdge()

        // Xử lý deep link từ notification
        handleIntent(intent)

        setContent {
            CodeVuiTheme {
                MyFilesApp(
                    pendingFolderPath = pendingFolderPath,
                    pendingArchivePreview = pendingArchivePreview
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle khi app đã mở, user tap notification
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            FileOperationService.ACTION_OPEN_FOLDER -> {
                pendingFolderPath = intent.getStringExtra(FileOperationService.EXTRA_FOLDER_PATH)
            }
            FileOperationService.ACTION_PREVIEW_ARCHIVE -> {
                val archivePath = intent.getStringExtra(FileOperationService.EXTRA_ARCHIVE_PATH)
                val archiveName = intent.getStringExtra(FileOperationService.EXTRA_ARCHIVE_NAME)
                if (archivePath != null && archiveName != null) {
                    pendingArchivePreview = archivePath to archiveName
                }
            }
        }
    }
}

@Composable
fun MyFilesApp(
    mainViewModel: MainViewModel = viewModel(),
    pendingFolderPath: String? = null,
    pendingArchivePreview: Pair<String, String>? = null  // (archivePath, archiveName)
) {
    val currentScreen = mainViewModel.currentScreen
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // State cho dialog "Mở bằng" — null = không hiện
    var openWithFile by remember { mutableStateOf<RecentFile?>(null) }

    // Handle deep link từ notification - navigate đến folder đích
    LaunchedEffect(pendingFolderPath) {
        if (pendingFolderPath != null) {
            val folder = File(pendingFolderPath)
            if (folder.exists() && folder.isDirectory) {
                // Navigate đến Browse screen với initialPath
                mainViewModel.navigateReplace(Screen.Browse(initialPath = pendingFolderPath))
            }
        }
    }

    // Handle archive preview from notification
    LaunchedEffect(pendingArchivePreview) {
        if (pendingArchivePreview != null) {
            val (archivePath, archiveName) = pendingArchivePreview
            val file = File(archivePath)
            if (file.exists() && file.isFile) {
                // Navigate to Archive screen in preview mode
                mainViewModel.navigateReplace(
                    Screen.Archive(
                        filePath = archivePath,
                        fileName = archiveName,
                        fullPath = archivePath,
                        isPreviewMode = true
                    )
                )
            }
        }
    }

    BackHandler(enabled = mainViewModel.screenStack.size > 1) {
        mainViewModel.goBack()
    }

    // Capture setter để dùng trong lambda (MutableState stable qua recomposition)
    val setOpenWithFile: (RecentFile?) -> Unit = { openWithFile = it }

    // remember để tránh tạo lại lambda mỗi lần recompose
    // Chỉ tạo lại khi context hoặc mainViewModel thay đổi (hiếm khi xảy ra)
    val handleFileOpen = remember(context, mainViewModel) {
        { file: RecentFile ->
            val ext = file.path.substringAfterLast('.', "").lowercase()

            // Text files → mở trong TextEditorScreen
            val textExtensions = setOf("txt", "log", "md", "csv", "xml", "json", "yaml", "yml",
                                       "ini", "cfg", "conf", "properties", "toml", "sh", "bat",
                                       "html", "htm", "css", "js", "ts", "kt", "java", "py",
                                       "c", "cpp", "h", "gradle", "gitignore")
            if (ext in textExtensions) {
                mainViewModel.navigateTo(Screen.TextEditor(
                    filePath = file.path,
                    fileName = file.name
                ))
                return@remember
            }

            if (file.type == FileType.ARCHIVE || FileType.fromExtension(ext)) {
                // Mở archive trong preview mode
                mainViewModel.navigateTo(Screen.Archive(
                    filePath = file.path,
                    fileName = file.name,
                    fullPath = file.path,
                    isPreviewMode = true
                ))
                return@remember
            }

            // Kiểm tra default app đã được lưu cho extension này chưa
            val defaultApp = DefaultAppManager.getDefaultApp(context, ext)
            if (defaultApp != null) {
                // Có default app → mở thẳng, không hỏi
                openFileWithSavedDefault(context, file, defaultApp)
            } else {
                // Chưa có default → hiện dialog chọn app
                setOpenWithFile(file)
            }
        }
    }

    // Open a FavoriteItem file (convert to RecentFile then reuse handleFileOpen)
    val openFavoriteFile: (com.example.codevui.model.FavoriteItem) -> Unit = { item ->
        val file = com.example.codevui.model.RecentFile(
            name = item.name,
            date = "",
            type = com.example.codevui.model.FileType.fromMimeType(item.mimeType),
            uri = item.uri,
            path = item.path,
            size = item.size,
            dateModified = item.dateModified
        )
        handleFileOpen(file)
    }

    // remember để tránh tạo lại lambda mỗi lần recompose
    val onDrawerNavigate = remember(mainViewModel) {
        { screen: Screen ->
            mainViewModel.navigateReplace(screen)
        }
    }

    // ── Dialog "Mở bằng" ────────────────────────────────────────────────────
    val openWithFileSnapshot = openWithFile
    if (openWithFileSnapshot != null) {
        OpenWithDialog(
            file = openWithFileSnapshot,
            onDismiss = { openWithFile = null },
            onJustOnce = { appInfo ->
                // Mở file một lần, KHÔNG lưu preference
                openFileWithApp(context, openWithFileSnapshot, appInfo)
                openWithFile = null
            },
            onAlways = { appInfo ->
                // Lưu app này làm mặc định cho extension → mở file
                val ext = openWithFileSnapshot.path.substringAfterLast('.', "").lowercase()
                DefaultAppManager.setDefaultApp(
                    context, ext, appInfo.packageName, appInfo.activityName
                )
                openFileWithApp(context, openWithFileSnapshot, appInfo)
                openWithFile = null
            }
        )
    }

    AdaptiveLayout(
        currentScreen = currentScreen,
        isDrawerExpanded = mainViewModel.isDrawerExpanded,
        onToggleDrawer = { mainViewModel.isDrawerExpanded = !mainViewModel.isDrawerExpanded },
        storageUsed = mainViewModel.storageUsed,
        storageTotal = mainViewModel.storageTotal,
        onNavigate = onDrawerNavigate
    ) {
        when (val screen = currentScreen) {
            is Screen.Home -> {
                HomeScreen(
                    onCategoryClick = { category ->
                        mainViewModel.navigateTo(Screen.FileList(category.fileType, category.label))
                    },
                    onStorageClick = { storageItem ->
                        when (storageItem.title) {
                            "Bộ nhớ trong" -> mainViewModel.navigateTo(Screen.Browse())
                            "Thùng rác" -> mainViewModel.navigateTo(Screen.Trash)
                            "Quản lý lưu trữ" -> mainViewModel.navigateTo(Screen.StorageManager)
                        }
                    },
                    onRecentSectionClick = { mainViewModel.navigateTo(Screen.RecentFiles()) },
                    onRecentFileClick = handleFileOpen,
                    onRecentFileLongClick = { file ->
                        mainViewModel.navigateTo(Screen.RecentFiles(initialSelectedPath = file.path))
                    },
                    onSearchClick = { mainViewModel.navigateTo(Screen.Search) },
                    onTrashClick = { mainViewModel.navigateTo(Screen.Trash) },
                    onFavoritesClick = { mainViewModel.navigateTo(Screen.Favorites) },
                    onStorageInfoLoaded = { used, total ->
                        mainViewModel.storageUsed = used
                        mainViewModel.storageTotal = total
                    }
                )
            }
            is Screen.FileList -> {
                FileListScreen(
                    fileType = screen.fileType,
                    title = screen.title,
                    onBack = { mainViewModel.goBack() },
                    onFileClick = handleFileOpen,
                    onNavigateToFolder = { folderPath ->
                        mainViewModel.navigateTo(Screen.Browse(initialPath = folderPath))
                    },
                    onTrashClick = { mainViewModel.navigateTo(Screen.Trash) },
                    onFavoritesClick = { mainViewModel.navigateTo(Screen.Favorites) }
                )
            }
            is Screen.Browse -> {
                BrowseScreen(
                    sessionId = screen.sessionId,
                    initialPath = screen.initialPath,
                    onBack = { mainViewModel.goBack() },
                    onHomeClick = { mainViewModel.navigateReplace(Screen.Home) },
                    onFavoritesClick = { mainViewModel.navigateTo(Screen.Favorites) },
                    onFileClick = handleFileOpen,
                    onSearchClick = { mainViewModel.navigateTo(Screen.Search) },
                    onTrashClick = { mainViewModel.navigateTo(Screen.Trash) }
                )
            }
            is Screen.Archive -> {
                ArchiveScreen(
                    archivePath = screen.filePath,
                    archiveName = screen.fileName,
                    fullPath = screen.fullPath,
                    isPreviewMode = screen.isPreviewMode,
                    onBack = { mainViewModel.goBack() },
                    onNavigateToFolder = { folderPath ->
                        log.d("=== onNavigateToFolder from ArchiveScreen ===")
                        log.d("Received folderPath: '$folderPath'")
                        log.d("Current screen: ${mainViewModel.currentScreen}")
                        log.d("Navigating to Screen.Browse(initialPath='$folderPath')")
                        mainViewModel.navigateTo(Screen.Browse(initialPath = folderPath))
                        log.d("Navigation completed, new screen: ${mainViewModel.currentScreen}")
                    }
                )
            }
            is Screen.RecentFiles -> {
                RecentFilesScreen(
                    initialSelectedPath = screen.initialSelectedPath,
                    onBack = { mainViewModel.goBack() },
                    onFileClick = handleFileOpen,
                    onNavigateToFolder = { folderPath ->
                        mainViewModel.navigateTo(Screen.Browse(initialPath = folderPath))
                    },
                    onTrashClick = { mainViewModel.navigateTo(Screen.Trash) },
                    onFavoritesClick = { mainViewModel.navigateTo(Screen.Favorites) },
                    isLandscape = isLandscape
                )
            }
            is Screen.Search -> {
                SearchScreen(
                    onBack = { mainViewModel.goBack() },
                    onFileClick = handleFileOpen,
                    onNavigateToFolder = { folderPath ->
                        mainViewModel.navigateTo(Screen.Browse(initialPath = folderPath))
                    },
                    onFavoritesClick = { mainViewModel.navigateTo(Screen.Favorites) }
                )
            }
            is Screen.Trash -> {
                TrashScreen(
                    onBack = { mainViewModel.goBack() },
                    onNavigateToFolder = { folderPath ->
                        mainViewModel.navigateTo(Screen.Browse(initialPath = folderPath))
                    }
                )
            }
            is Screen.StorageManager -> {
                StorageManagerScreen(
                    onBack = { mainViewModel.goBack() },
                    onNavigateToTrash = { mainViewModel.navigateTo(Screen.Trash) },
                    onNavigateToLargeFiles = { mainViewModel.navigateTo(Screen.LargeFiles) },
                    onNavigateToDuplicates = { mainViewModel.navigateTo(Screen.Duplicates) },
                    onNavigateToUnusedApps = { mainViewModel.navigateTo(Screen.UnusedApps) },
                    onNavigateToOldScreenshots = { mainViewModel.navigateTo(Screen.Browse(initialPath = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots")) },
                    onNavigateToRecommend = { cardType ->
                        mainViewModel.navigateTo(Screen.Recommend(cardType))
                    },
                    onNavigateToCategory = { category ->
                        when (category) {
                            "Video" -> mainViewModel.navigateTo(Screen.FileList(FileType.VIDEO, "Video"))
                            "Ảnh" -> mainViewModel.navigateTo(Screen.FileList(FileType.IMAGE, "Ảnh"))
                            "Audio" -> mainViewModel.navigateTo(Screen.FileList(FileType.AUDIO, "File âm thanh"))
                            "Tài liệu" -> mainViewModel.navigateTo(Screen.FileList(FileType.DOC, "Tài liệu"))
                            "File đã nén" -> mainViewModel.navigateTo(Screen.FileList(FileType.ARCHIVE, "File đã nén"))
                            "Các file cài đặt" -> mainViewModel.navigateTo(Screen.FileList(FileType.APK, "Các file cài đặt"))
                            "Lượt tải về" -> mainViewModel.navigateTo(Screen.FileList(FileType.DOWNLOAD, "Lượt tải về"))
                            "Thùng rác" -> mainViewModel.navigateTo(Screen.Trash)
                            else -> mainViewModel.navigateTo(Screen.Browse())
                        }
                    },
                    onVolumeSelect = { domainType ->
                        // Navigate to browse with volume root path
                        val rootPath = com.example.codevui.data.StorageVolumeManager.getRootPath(domainType)
                        if (rootPath != null) {
                            mainViewModel.navigateTo(Screen.Browse(initialPath = rootPath))
                        }
                    }
                )
            }
            is Screen.TextEditor -> {
                TextEditorScreen(
                    filePath = screen.filePath,
                    fileName = screen.fileName,
                    onBack = { mainViewModel.goBack() }
                )
            }
            is Screen.Duplicates -> {
                DuplicatesScreen(
                    onBack = { mainViewModel.goBack() },
                    onFileClick = { file -> handleFileOpen(file) },
                    onNavigateToFolder = { folderPath -> mainViewModel.navigateTo(Screen.Browse(initialPath = folderPath)) },
                    onTrashClick = { mainViewModel.navigateTo(Screen.Trash) }
                )
            }
            is Screen.LargeFiles -> {
                LargeFilesScreen(
                    onBack = { mainViewModel.goBack() },
                    onFileClick = { file -> handleFileOpen(file) },
                    onNavigateToFolder = { folderPath -> mainViewModel.navigateTo(Screen.Browse(initialPath = folderPath)) },
                    onTrashClick = { mainViewModel.navigateTo(Screen.Trash) }
                )
            }
            is Screen.UnusedApps -> {
                UnusedAppsScreen(
                    onBack = { mainViewModel.goBack() }
                )
            }
            is Screen.Recommend -> {
                RecommendScreen(
                    initialCardType = screen.cardType,
                    onBack = { mainViewModel.goBack() }
                )
            }
            is Screen.Favorites -> {
                FavoritesScreen(
                    onBack = { mainViewModel.goBack() },
                    onFolderClick = { path ->
                        mainViewModel.navigateTo(Screen.Browse(initialPath = path))
                    },
                    onFileClick = { item ->
                        openFavoriteFile(item)
                    }
                )
            }
        }
    }
}