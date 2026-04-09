# CodeVui — CLAUDE.md

> Project documentation cho AI assistant. Đọc file này TRƯỚC TIÊN trước khi bắt đầu bất kỳ task nào.
> Last updated: 2026-04-08.
>
> **Companion files:**
> - `.claude/CLAUDE.local.md` — ghi chú dev local (không commit)
> - `.claude/memory.md` — long-term learning/context giữa sessions
> - `.claude/TASKS.md` — lịch sử task + TODO
> - `.claude/rules/` — convention rules (workflow, kotlin-style, compose-style, mvvm)
> - `.claude/agents/` — agent personas (code-reviewer, build-verifier, myfiles-reference, refactor-assistant)
> - `.claude/skills/` — step-by-step playbooks (add-screen, add-dialog, add-file-operation, add-thumbnail-fetcher, logging, mediastore, auto-build, task-tracking, updating-claude)
> - **Reference project:** `../MyFiles/MyFiles/` — Samsung My Files source để port pattern sang CodeVui

---

## 1. Project Overview

**CodeVui** là Android File Manager viết bằng Kotlin, dùng Jetpack Compose + Material 3.

| Property | Value |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Min SDK | 24 (Android 7.0) |
| Target / Compile SDK | 36 |
| Architecture | MVVM |
| Database | Room 2.7.0 |
| Image Loading | Coil 2.6.0 |
| Archive Library | zip4j 2.11.5 |
| Build System | Gradle Kotlin DSL |

**Key Permissions:**
- `MANAGE_EXTERNAL_STORAGE` — Android 11+ (full file access)
- `READ_MEDIA_IMAGES/AUDIO/VIDEO` — Android 13+
- `READ_EXTERNAL_STORAGE` — Android 12 and below
- `POST_NOTIFICATIONS` — hiển thị notification
- `FOREGROUND_SERVICE_DATA_SYNC` — chạy file operation background
- `PACKAGE_USAGE_STATS` — xem usage stats (unused apps detection)

---

## 2. Package Structure (Full Tree)

```
com.example.codevui/
│
├── AppImageLoader.kt              # Coil singleton với custom fetchers
├── MainActivity.kt                # Single Activity — deep-link routing
├── MainViewModel.kt               # Navigation stack (screenStack)
│
├── model/
│   └── Model.kt                   # Tất cả data class + FileType enum
│
├── data/
│   ├── FileRepository.kt          # MediaStore query (CRUD MediaStore)
│   ├── FileOperations.kt          # COPY/MOVE/COMPRESS Flow engine
│   ├── TrashManager.kt            # Thùng rác (.Trash + Room metadata)
│   ├── ArchiveReader.kt           # zip4j wrapper (read/extract/remove)
│   ├── MediaStoreScanner.kt       # MediaScannerConnection → notify other apps
│   ├── MediaStoreObserver.kt      # ContentObserver → Flow (auto-reload)
│   ├── RecommendRepository.kt     # Recommend cards (MyFiles style) — Strategy pattern
│   ├── FavoriteManager.kt         # Yêu thích — Room DB CRUD + MIME/path resolution
│   ├── FavoriteAction.kt          # Object chứa suspend fun: add/remove/toggle favorites cho file và folder
│   ├── StorageVolumeManager.kt    # Quản lý nhiều ổ lưu trữ (Internal/SD/USB) — MyFiles pattern
│   ├── DomainType.kt              # Constants cho storage domain (INTERNAL=1, SD=2, USB=3-8)
│   ├── StorageUsageManager.kt     # Lấy used/total/free size của từng volume
│   ├── StorageTypeForTrash.kt     # Trash type theo storage domain (Internal/SD)
│   └── db/
│       ├── AppDatabase.kt         # Room singleton, v1→v2→v3 migration
│       ├── TrashDao.kt            # Room DAO cho bảng trash_items
│       ├── TrashEntity.kt         # Room entity (trash_items table)
│       ├── FavoriteDao.kt         # Room DAO cho bảng favorites
│       └── FavoriteEntity.kt      # Room entity (favorites table)
│
├── service/
│   └── FileOperationService.kt   # ForegroundService copy/move/compress
│
├── ui/
│   ├── MainActivity.kt            # Single Activity, deep-link routing
│   ├── MainViewModel.kt          # Navigation stack (screenStack)
│   │
│   ├── common/
│   │   ├── BaseFileOperationViewModel.kt  # Binds FileOperationService
│   │   ├── BaseMediaStoreViewModel.kt     # +MediaStoreObserver auto-reload
│   │   ├── SelectableScaffold.kt         # Selection-mode scaffold
│   │   ├── AdaptiveLayout.kt              # DrawerPane landscape / full portrait
│   │   ├── DrawerPane.kt                 # Sidebar navigation (landscape)
│   │   ├── Interfaces.kt                  # Sortable, Navigable, Reloadable
│   │   │
│   │   ├── dialogs/
│   │   │   ├── DialogManager.kt           # Central dialog state machine
│   │   │   ├── RenameDialog.kt            # Đổi tên file/folder
│   │   │   ├── DeleteConfirmDialog.kt     # Xác nhận xóa (chuyển trash)
│   │   │   ├── MoveToTrashDialog.kt       # Warning 30-day expiry
│   │   │   ├── DetailsDialog.kt           # Chi tiết file (size, path, MIME)
│   │   │   ├── PasswordDialog.kt          # Archive password input
│   │   │   ├── ExtractDialog.kt           # Giải nén archive
│   │   │   ├── CompressDialog.kt          # Nén file (custom name)
│   │   │   ├── CreateFolderDialog.kt      # Tạo thư mục mới
│   │   │   ├── CreateFileDialog.kt        # Tạo file mới
│   │   │   └── PasteConflictDialog.kt     # Conflict → Replace/Rename/Cancel
│   │   │
│   │   └── viewmodel/
│   │       ├── OperationResultManager.kt  # Snackbar kết quả operation
│   │       └── PasswordHandler.kt        # Password dialog state + composable
│   │
│   ├── home/
│   │   ├── HomeScreen.kt         # Categories grid, recent files, storage
│   │   ├── HomeViewModel.kt      # Load categories, recent, storage info
│   │   ├── HomeUiState.kt        # categories + recentFiles + storageItems
│   │   └── sections/
│   │       ├── HomeSections.kt   # CategoriesGrid, RecentFilesRow, StorageList
│   │
│   ├── browse/
│   │   ├── BrowseScreen.kt       # File browser UI (Miller Columns)
│   │   ├── BrowseViewModel.kt    # Directory listing + Miller Columns
│   │   ├── BrowseUiState.kt      # currentPath, folders, files, columns
│   │   └── columnview/
│   │       ├── ColumnData.kt     # path, items, scrollTo per column
│   │       ├── ColumnBrowseView.kt  # LazyRow of ColumnPanels
│   │       └── ColumnPanel.kt   # Single column, rightmost = interactive
│   │
│   ├── filelist/
│   │   ├── FileListScreen.kt     # Files by MediaStore type
│   │   ├── FileListViewModel.kt  # load(type), totalSize
│   │   └── FileListUiState.kt
│   │
│   ├── recent/
│   │   ├── RecentFilesScreen.kt  # Recent files grouped by day
│   │   ├── RecentFilesViewModel.kt
│   │   └── RecentFilesUiState.kt
│   │
│   ├── search/
│   │   ├── SearchScreen.kt       # Debounced search + type/time filters
│   │   ├── SearchViewModel.kt    # 300ms debounce, TimeFilter, TypeFilter
│   │   └── SearchUiState.kt
│   │
│   ├── archive/
│   │   ├── ArchiveScreen.kt      # Browse inside ZIP (normal + preview mode)
│   │   ├── ArchiveViewModel.kt   # open/extract/move/delete inside ZIP
│   │   ├── ArchiveUiState.kt     # archivePath, folders, files, previewMode
│   │   ├── ArchivePreviewActions.kt  # Preview mode action handler
│   │   └── OperationProgressDialog.kt  # Extract progress dialog
│   │
│   ├── duplicates/
│   │   ├── DuplicatesScreen.kt     # File trùng lặp — scan, group display, selection
│   │   ├── DuplicatesViewModel.kt   # SavedStateHandle + SelectionState + scanDuplicates()
│   │   └── DuplicatesUiState.kt     # groups, isScanning, totalWasted
│   │
│   ├── largefiles/
│   │   ├── LargeFilesScreen.kt      # File lớn — grouped by size, threshold settings, type filter
│   │   ├── LargeFilesViewModel.kt   # SavedStateHandle + SelectionState + findLargeFiles()
│   │   └── LargeFilesUiState.kt     # SizeThreshold, LargeFileTypeFilter, SizeGroup, groups
│   │
│   ├── unusedapps/
│   │   ├── UnusedAppsScreen.kt      # Ứng dụng không dùng — list, select, settings link
│   │   ├── UnusedAppsViewModel.kt   # SavedStateHandle + SelectionState + getUnusedApps()
│   │   └── UnusedAppsUiState.kt     # UnusedAppInfo, UnusedAppsTab, UnusedAppsSortBy
│   │
│   ├── recommend/
│   │   ├── RecommendScreen.kt         # Recommend cards — card list + file list view
│   │   ├── RecommendViewModel.kt      # loadCards, selectCard, deleteSelectedFiles
│   │   └── RecommendUiState.kt       # RecommendCard, RecommendFile, RecommendType
│   │
│   ├── favorites/
│   │   ├── FavoritesScreen.kt   # Yêu thích — list, selection mode, thumbnail (Coil), FavoriteThumbnail với imageLoaded state. Hỗ trợ folder: click folder → navigate vào BrowseScreen, click file → mở file
│   │   ├── FavoritesViewModel.kt  # observeFavorites (Room Flow) + SelectionState + BaseFileOperationViewModel (Copy/Move/Compress delegate)
│   │   ├── FavoritesUiState.kt
│   │
│   ├── trash/
│   │   ├── TrashScreen.kt        # Trash list, restore, permanently delete
│   │   └── TrashViewModel.kt     # observeTrashItems (Room Flow)
│   │
│   ├── storage/
│   │   ├── StorageManagerScreen.kt  # Quản lý lưu trữ (Samsung My Files style) — multi-volume
│   │   ├── StorageManagerViewModel.kt  # Load volumes (Internal/SD/USB), per-volume analysis
│   │   └── StorageManagerUiState.kt  # VolumeStorageState, VolumeFileBreakdown, multi-volume
│   │
│   ├── texteditor/
│   │   ├── TextEditorScreen.kt   # TextField + pinch-zoom-pan-fling
│   │   └── TextEditorViewModel.kt  # Load/save, rotation-safe SavedStateHandle
│   │
│   ├── clipboard/
│   │   └── ClipboardManager.kt   # In-app COPY/MOVE clipboard (rotation-safe)
│   │
│   ├── selection/
│   │   ├── SelectionState.kt    # Multi-select (rotation-safe SavedStateHandle)
│   │   ├── FileActionState.kt    # Pending COPY/MOVE picker state
│   │   ├── SelectionActionHandler.kt  # Action dispatcher (di chuyen/sao chep/...)
│   │   ├── SelectionComponents.kt # SelectionTopBar, SelectionBottomBar, SelectionCheckbox
│   │   ├── FolderPickerSheet.kt # Full-screen destination picker (copy/move)
│   │   └── PreviewBottomBar.kt   # Archive preview bottom bar
│   │
│   ├── components/
│   │   ├── FileListItem.kt       # File row: thumbnail + name + size + date
│   │   ├── FolderListItem.kt     # Folder row: name + item count
│   │   ├── ArchiveEntryItem.kt   # Entry row inside ZIP
│   │   ├── Breadcrumb.kt          # Multi-segment path navigation
│   │   ├── SortBar.kt            # Sort (Ngày/Tên/Kích thước) + Cần thiết filter
│   │   ├── HighlightedText.kt     # Search term highlighting
│   │   ├── CommonComponents.kt   # IconBox, ListRow, CategoryCard, StorageBadge
│   │
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt              # Material 3 theme
│       └── Type.kt
│
├── ui/thumbnail/                # Thumbnail system - có ThumbnailManager base class
│   ├── ThumbnailManager.kt       # ThumbnailFetcher base + ThumbnailData sealed class + ThumbnailManager singleton
│   ├── VideoThumbnailFetcher.kt  # Video thumbnail (ContentResolver.loadThumbnail)
│   ├── AudioThumbnailFetcher.kt  # Audio thumbnail (MediaMetadataRetriever embeddedPicture)
│   ├── ApkThumbnailFetcher.kt    # APK thumbnail (PackageManager app icon)
│   └── ArchiveThumbnailFetcher.kt # Archive thumbnail (extract from ZIP → temp → decode)
│
├── util/
│   ├── Logger.kt                # Structured logging (class/method/line)
│   └── FormatUtils.kt           # formatFileSize, formatStorageSize, formatDateFull
│
```

**Note:** `OperationProgressDialog` hiện tồn tại ở 2 nơi:
- `ui/progress/OperationProgressDialog.kt` — Samsung My Files style dialog dùng chung cho copy/move/compress
- `ui/archive/OperationProgressDialog.kt` — dialog riêng cho extract trong ArchiveScreen

---

## 3. Data Classes (model/Model.kt)

```kotlin
// Tất cả data class dùng trong app

data class RecentFile(
    val name, date, type: FileType, uri: Uri,
    val path: String, val size: Long, val dateModified: Long,
    val ownerApp: String? = null  // app tạo file
)

data class RecommendCard(
    val type: RecommendType,
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val fileCount: Int = 0
)

data class RecommendFile(
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String?,
    val bucketId: String,
    val isDirectory: Boolean = false
)

enum class RecommendType(val value: Int) {
    OLD_MEDIA_FILES(0),    // File ảnh/video/audio > 1 tháng, ngoài Download/Screenshot
    UNNECESSARY_FILES(1),  // APK + file nén
    SCREENSHOT_FILES(2),   // Ảnh chụp màn hình
    DOWNLOAD_FILES(3),     // File trong thư mục Download
    COMPRESSED_FILES(8);  // File đã giải nén hoàn toàn (từ operation history)
}

data class FolderItem(
    val name, path: String, val dateModified: Long,
    val itemCount: Int, val isPinned: Boolean
)

data class ArchiveEntry(
    val name, path: String,  // path bên trong archive
    val size, compressedSize: Long,
    val isDirectory: Boolean, val lastModified: Long
)

data class StorageAnalysis(  // Chi tiết dung lượng cho Quản lý lưu trữ
    val videoBytes, imageBytes, audioBytes: Long,
    val archiveBytes, apkBytes, docBytes: Long,
    val trashBytes, unusedAppsBytes: Long,
    val duplicateBytes, largeFilesBytes, oldScreenshotsBytes: Long
)

data class CategoryItem(
    val icon: ImageVector, val tint, bg: Color,
    val label: String, val fileType: FileType
)

data class StorageItem(
    val icon: ImageVector, val iconTint, iconBg: Color,
    val title: String, val subtitle: String? = null,
    val used: String? = null, val total: String? = null
)

data class StorageInfo(val totalBytes, usedBytes: Long) {
    val freeBytes: Long get() = totalBytes - usedBytes
}

data class StorageVolume(  // Thông tin 1 ổ lưu trữ (Internal/SD/USB)
    val domainType: Int,    // DomainType constant
    val path: String,       // Root path
    val displayName: String,
    val isRemovable: Boolean,
    val isEmulated: Boolean,
    val isMounted: Boolean,
    val totalBytes: Long,
    val freeBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedPercent: Int get() = ...
}

data class VolumeStorageState(  // Per-volume state cho StorageManager
    val domainType: Int,
    val displayName: String,
    val totalBytes, usedBytes, freeBytes: Long,
    val usedPercent: Int,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class VolumeFileBreakdown(  // Per-volume file category breakdown
    val domainType: Int = 0,
    val videoBytes, imageBytes, audioBytes, archiveBytes, apkBytes, docBytes: Long,
    val downloadBytes, trashBytes: Long,
    val otherBytes, systemBytes: Long = 0L
)

data class StorageUsageInfo(
    val usedByteSize: Long = 0L,
    val totalByteSize: Long = 0L,
    val displayText: String? = null
) {
    val freeByteSize: Long get() = (totalByteSize - usedByteSize).coerceAtLeast(0L)
    val usedPercent: Int get() = ...
}

enum class FileType {
    IMAGE, VIDEO, AUDIO, DOC, APK, ARCHIVE, DOWNLOAD, OTHER
}
```

---

## 4. Data Layer — Chi tiết từng class

### `FileRepository.kt`
MediaStore query layer. **Không dùng singleton** — instantiate với Context.

| API | Mô tả |
|---|---|
| `getRecentFiles(limit)` | Query MediaStore, sắp xếp date_modified DESC |
| `getFilesByType(type, sortBy, ascending)` | Files theo MIME type |
| `listDirectory(path, sortBy, ascending)` | Pair(folders, files) từ `java.io.File` |
| `searchFiles(query, fileTypes?, afterTimestamp?)` | Full-text search |
| `searchFoldersSuspend(query)` | Cancellable recursive folder search |
| `getFileCount(type)` | Đếm file theo type |
| `getTotalSizeByType(type)` | Tổng size theo type |
| `getInternalStorageInfo()` | StatFs cho bộ nhớ trong |
| `getStorageAnalysis()` | Chi tiết dung lượng (suspend) — video/image/audio/archive/apk/doc/apps/duplicates/large/trash |
| `findLargeFiles(minBytes)` | Files > minBytes từ MediaStore, sorted SIZE DESC, exclude .Trash |
| `getUnusedApps()` | Apps không dùng 30 ngày (UsageStatsManager), user apps only, sorted SIZE DESC |
| `getVolumeFileBreakdown(path)` | File breakdown cho SD/USB (video/image/audio/archive/apk/doc/download/trash) |

- LruCache(100) cho app labels (PackageManager)
- `essentialFolderNames`: DCIM, Pictures, Download, Documents, Android, Alarms, Music, Movies, Podcasts, Ringtones, Notifications, Audiobooks

### `FileOperations.kt`
Singleton object. Tất cả hàm trả về `Flow<ProgressState>`.

```kotlin
object FileOperations {
    fun execute(sources, destDir, COPY|MOVE): Flow<ProgressState>
    fun compressToZip(sources, customName?): Flow<ProgressState>
}

sealed class ProgressState {
    object Counting
    data class Running(currentFile, done, total, percent)
    data class Done(success, failed, outputPath?)
    data class Error(message)
}
```

- Dùng `channelFlow` + `async/awaitAll` để song song
- Buffer 64KB, `isActive` cancellation
- Kiểm tra `destDir.startsWith(source.path)` → không copy vào chính mình

### `TrashManager.kt`
Khởi tạo với Context. `.Trash/files/<ts>_<safeName>`.

| API | Mô tả |
|---|---|
| `moveToTrash(paths)` | Copy → delete → Room metadata → scan parent dirs |
| `restore(ids)` | Copy từ trash về originalPath → scan dest dirs |
| `permanentlyDelete(ids)` | Xóa vĩnh viễn → scan .Trash |
| `emptyTrash()` | Gọi permanentlyDelete |
| `observeTrashItems()` | Room Flow → tự động cập nhật |
| `getTrashItems()` | Lấy danh sách (kèm cleanExpired) |

- MAX_TRASH_DAYS = 30
- `cleanExpired()` chạy tự động sau mỗi `moveToTrash`
- `generateTrashName`: `${timestamp}_${safeName}`

### `ArchiveReader.kt`
Singleton. Dùng zip4j.

| API | Mô tả |
|---|---|
| `readEntries(path, password?)` | Đọc tất cả entries trong ZIP |
| `listLevel(entries, currentPath)` | Miller-columns listing theo level |
| `extractEntries(archive, paths, dest, all, password?)` | Giải nén có chọn lọc |
| `removeEntries(archive, paths, all, password?)` | Xóa khỏi ZIP (in-place rewrite) |
| `extractToTemp(archive, entry, tempDir, password?)` | Extract 1 file ra temp để preview |

- `PasswordRequiredException`, `InvalidPasswordException`
- `removeEntries` dùng `zipFile.removeFile()` — không cần extract + re-archive

### `MediaStoreScanner.kt`
Singleton. Dùng `MediaScannerConnection.scanFile()`.

| API | Mô tả |
|---|---|
| `scanPaths(paths)` | Scan file/folder, tự expand folder → files |
| `scanDirectory(dir)` | Scan đệ quy toàn bộ thư mục |
| `scanSourceAndDest(src, dest)` | Scan cả nguồn + đích (sau MOVE) |
| `scanNewFile(parent, newPath)` | Scan thư mục cha + file mới (sau CREATE) |

- Tự động loại trừ `.Trash`
- MIME type tự detect từ extension
- Callback `onComplete` được gọi sau scan

### `MediaStoreObserver.kt`
Singleton. Dùng `ContentObserver` → `Flow<Unit>`.

```kotlin
object MediaStoreObserver {
    fun observe(context): Flow<Unit>  // debounce 500ms, conflate
}
```

- Register `MediaStore.Files.getContentUri("external")` với `notifyForDescendants=true`
- Tự `unregisterContentObserver` khi flow bị cancel
- Dùng trong `BaseMediaStoreViewModel` để auto-reload khi MediaStore thay đổi

### `FavoriteManager.kt`
Singleton quản lý favorites (Room DB).

| API | Mô tả |
|---|---|
| `observeFavorites(context)` | Flow → auto-update UI khi DB thay đổi |
| `addFavorite(...)` | Thêm file/folder vào favorites |
| `removeFavorite(path)` | Xóa 1 file khỏi favorites |
| `removeFavorites(paths)` | Xóa nhiều file |
| `toggleFavorite(...)` | Toggle thêm/xóa |
| `validateFavorites(context)` | Xóa favorites không tồn tại (lazy validation) |
| `reorderFavorites(paths)` | Cập nhật sortOrder sau kéo thả |

**Lưu ý quan trọng:**
- `toFavoriteItem()` resolve mimeType từ extension khi stored value là null → đảm bảo FileType đúng cho thumbnail
- `resolveContentUri()` query MediaStore để lấy content:// Uri cho thumbnail loading
- `resolveMimeType(path)` fallback khi DB mimeType = null

### `RecommendRepository.kt`
Strategy pattern cho recommendation cards (MyFiles style). Instantiate với Context.

| API | Mô tả |
|---|---|
| `getAllCards()` | Lấy thông tin tổng hợp (title, size) cho tất cả 4 card types |
| `getCardInfo(type)` | Lấy thông tin cho 1 card type cụ thể |
| `getCardFiles(type)` | Lấy danh sách file cho 1 card type (với thông tin đầy đủ) |

**4 Strategy types:**
- `OLD_MEDIA_FILES` — file ảnh/video/audio > 1 tháng (ngoài Download/Screenshot)
- `UNNECESSARY_FILES` — APK + file nén (.zip, .rar, .7z, .tar, .gz)
- `SCREENSHOT_FILES` — file trong thư mục Screenshots
- `DOWNLOAD_FILES` — file trong thư mục Downloads

### `StorageVolumeManager.kt`
Singleton quản lý nhiều ổ lưu trữ (Internal/SD/USB). Mirror từ MyFiles.

| API | Mô tả |
|---|---|
| `refresh(context)` | Load danh sách ổ từ StorageManager + legacy scan |
| `getMountedVolumes(context)` | Lấy tất cả ổ đang mounted |
| `getInternalStorage(context)` | Lấy Internal Storage |
| `getSdCard(context)` | Lấy SD Card (nếu có) |
| `getUsbDrives(context)` | Lấy danh sách USB drives |
| `mounted(domainType)` | Kiểm tra ổ có đang mounted không |
| `connected(domainType)` | Kiểm tra ổ có đang connected không |
| `getVolume(domainType)` | Lấy StorageVolume theo domainType |
| `getRootPath(domainType)` | Lấy root path của ổ |

**DomainType constants:**
```kotlin
const val UNKNOWN = 0
const val INTERNAL_STORAGE = 1
const val EXTERNAL_SD = 2
const val EXTERNAL_USB_DRIVE_A = 3  // ... F = 8
const val INTERNAL_APP_CLONE = 9
```

### `DomainType.kt`
Object chứa constants và helper functions cho storage domain.

```kotlin
DomainType.isLocalStorage(domainType)  // domainType in INTERNAL..INTERNAL_APP_CLONE
DomainType.isInternalStorage(domainType)  // INTERNAL or APP_CLONE
DomainType.isSd(domainType)  // EXTERNAL_SD
DomainType.isUsb(domainType)  // EXTERNAL_USB_DRIVE_*
DomainType.isRemovable(domainType)  // SD or USB
DomainType.mounted(domainType)  // delegate to StorageVolumeManager
DomainType.connected(domainType)
DomainType.getVolume(domainType): StorageVolume?
DomainType.getRootPath(domainType): String?
```

### `StorageUsageManager.kt`
Singleton lấy used/total/free size của từng volume.

```kotlin
StorageUsageManager.getStorageUsedSize(context, domainType): Long
StorageUsageManager.getStorageTotalSize(domainType): Long
StorageUsageManager.getStorageFreeSpace(domainType): Long
StorageUsageManager.getStorageUsageInfo(context, domainType): StorageUsageInfo
StorageUsageManager.getStorageVolumeInfo(context, domainType): StorageVolume?
```

### `StorageTypeForTrash.kt`
Xác định trash type dựa trên storage domain. Mirror từ MyFiles.

```kotlin
StorageTypeForTrash.NONE = 0
StorageTypeForTrash.INTERNAL = 1       // Thùng rác Internal
StorageTypeForTrash.EXTERNAL_SD = 2    // Thùng rác SD Card
StorageTypeForTrash.INTERNAL_AND_SD = 3

StorageTypeForTrash.getStorageTypeForTrash(domainType): Int
StorageTypeForTrash.isInternalTrash(storageTypeForTrash): Boolean
StorageTypeForTrash.isSDTrash(storageTypeForTrash): Boolean
StorageTypeForTrash.isInternalAndSDTrash(storageTypeForTrash): Boolean
StorageTypeForTrash.isFullOnlySdOrInternal(selected, noSpace): Boolean
```

---

## 5. Database — Room

### Schema: `trash_items`

| Column | Type | Notes |
|---|:---:|---|
| id | TEXT PK | = trashName |
| originalName | TEXT | |
| trashName | TEXT | `<timestamp>_<safeName>` |
| originalPath | TEXT | INDEX (v2) |
| size | INTEGER | bytes |
| deleteTimeEpoch | INTEGER | seconds, INDEX (v2) |
| isDirectory | INTEGER | 0/1 |
| mimeType | TEXT | |
| extension_ | TEXT? | nullable |

**Migration v1→v2**: Thêm indexes trên `deleteTimeEpoch` và `originalPath`.

### Schema: `favorites`

| Column | Type | Notes |
|---|:---:|---|
| fileId | TEXT PK | SHA-256 hash of path |
| name | TEXT | Display name |
| path | TEXT | Full file path, UNIQUE index |
| size | INTEGER | bytes |
| mimeType | TEXT? | nullable |
| isDirectory | INTEGER | 0/1 |
| dateModified | INTEGER | seconds |
| addedAt | INTEGER | seconds |
| sortOrder | INTEGER | INDEX, for drag-drop reorder |

**Migration v2→v3**: Thêm bảng `favorites` (với `MIGRATION_2_3`).

---

## 6. Service Layer

### `FileOperationService.kt`
ForegroundService cho copy/move/compress.

```kotlin
class FileOperationService : Service() {
    inner class LocalBinder { fun getService(): FileOperationService }

    // State
    val operationState: StateFlow<ProgressState?>
    val operationTitle: StateFlow<String>

    // Static launchers
    fun startCopy(context, sources, destDir)
    fun startMove(context, sources, destDir)
    fun startCompress(context, sources, zipName?)
}

// Notification deep-links
const val ACTION_OPEN_FOLDER      // tap notification → open folder
const val ACTION_PREVIEW_ARCHIVE  // tap "Xem trước" → ArchiveScreen preview
const val EXTRA_FOLDER_PATH
const val EXTRA_ARCHIVE_PATH
const val EXTRA_ARCHIVE_NAME
```

**Flow hoạt động:**
1. `onStartCommand` → parse intent → chọn `flow` phù hợp
2. `startForeground(NOTIFICATION_ID, progress notification)`
3. `flow.collect` → update notification mỗi state change
4. `Done/Error` → hủy notification → **MediaStoreScanner** → show result notification (5s timeout)
5. `stopSelf()`

**Notification:**
- Channel: `file_operation_channel`, IMPORTANCE_LOW
- Progress bar (determinate khi biết total)
- Nút "Thoát" → cancel intent
- Result notification: tap → open folder, "Xem trước" action cho archive

---

## 7. ViewModel Hierarchy

```
AndroidViewModel
└── BaseFileOperationViewModel          # bind FileOperationService
    ├── BaseMediaStoreViewModel          # + MediaStoreObserver auto-reload
    │   ├── BrowseViewModel               # + Sortable + Navigable + Reloadable
    │   │   └── (LruCache 50, AtomicBoolean lock)
    │   ├── FileListViewModel
    │   ├── RecentFilesViewModel
    │   └── SearchViewModel
    ├── FavoritesViewModel                # + SelectionState (copy/move/delete/share)
    │   # Favorites không dùng MediaStore → extend trực tiếp BaseFileOperationViewModel
    │   # Room Flow auto-reload khi DB thay đổi
    └── ArchiveViewModel                  # (extends AndroidViewModel directly)
        # Archive không dùng MediaStore → không cần BaseMediaStoreViewModel
```

**BaseMediaStoreViewModel:**
```kotlin
abstract class BaseMediaStoreViewModel(app) : BaseFileOperationViewModel(app) {
    private fun observeMediaStore()  // MediaStoreObserver → reload when changed
    abstract fun load()
    override fun reload()           // calls load()
    override fun isOperationRunning() // blocks reload during operation
}
```

---

## 8. Navigation

```kotlin
sealed class Screen {
    object Home
    data class FileList(type: FileType, title: String)
    data class Browse(path: String = "")    // "" = root
    data class Archive(path: String, name: String = "")
    object RecentFiles
    object Favorites                # Yêu thích (Room DB)
    object Search
    object Trash
    object StorageManager               # Quản lý lưu trữ
    object Duplicates                  # File trùng lặp
    object LargeFiles                  # File lớn
    object UnusedApps                  # Ứng dụng không dùng
    data class TextEditor(path: String)
    data class Recommend(cardType: RecommendType? = null)  # Recommend cards (MyFiles style)
}
```

**MainActivity deep links:**
| Intent Action | Target |
|---|---|
| `ACTION_OPEN_FOLDER` | Navigate to `EXTRA_FOLDER_PATH` in BrowseScreen |
| `ACTION_PREVIEW_ARCHIVE` | ArchiveScreen(previewMode=true) |
| `android.intent.action.VIEW` text/* | TextEditorScreen |
| `android.intent.action.VIEW` archive | ArchiveScreen |
| `android.intent.action.VIEW` other | External Intent (FileProvider) |

**MainViewModel:**
- `screenStack: MutableStateList<Screen>` — MutableStateList survives recomposition
- `navigateTo(screen)` → push
- `navigateReplace(screen)` → replace top
- `goBack()` → pop (returns bool)

---

## 9. File Operations — End-to-End

### Copy / Move

```
1. Long-press → SelectionState.enterSelectionMode()
2. Tap "Di chuyển" / "Sao chép"
3. SelectionActionHandler → FileActionState.pendingAction = MOVE/COPY
4. FolderPickerSheet → user picks destination
5. BrowseViewModel.pasteFromClipboard()
   → ConflictDialog if name exists → PasteData(conflictCount)
6. BrowseViewModel.executePaste(sourcePaths, destPath, operation, replaceConflicts)
   → BrowseViewModel.copyFiles / moveFiles
   → BaseFileOperationViewModel → FileOperationService.startCopy/Move()
7. FileOperationService:
   startForeground → flow.collect → update notification
8. Done/Error:
   MediaStoreScanner.scanSourceAndDest(src, dest)
   notificationManager.cancel(NOTIFICATION_ID)
   showResultNotification → tap → open folder
9. operationState collected
   → MediaStoreObserver emits (sau scan)
   → BrowseViewModel.reload()
```

### Compress

```
1. Selection → More → Nén
2. CompressDialog (optional custom name)
3. BrowseViewModel.compressFiles(sources, customName)
   → FileOperationService.startCompress()
4. Done:
   MediaStoreScanner.scanNewFile(parentPath, zipPath)
   showResultNotification(isCompressOperation=true)
   → notification has "Xem trước" action
5. User taps "Xem trước" → MainActivity
   → ArchiveScreen(previewMode=true, autoSelect=true)
```

### Trash (Move to Trash)

```
1. Selection → Xóa → DeleteConfirmDialog
2. TrashViewModel.moveToTrash(paths)
   → TrashManager.moveToTrash()
   → copy to .Trash/files/ with generated name
   → delete original (java.io.File.delete)
   → insert TrashEntity into Room
   → MediaStoreScanner.scanPaths(parentDirs) ← SCAN HERE
   → cleanExpired() (items > 30 days)
```

### Restore from Trash

```
1. TrashScreen → select items → tap restore
2. TrashViewModel.restore(ids)
   → TrashManager.restore()
   → copy from .Trash/files/ to originalPath (resolve conflict)
   → delete from .Trash/files/
   → delete TrashEntity from Room
   → MediaStoreScanner.scanPaths(restoredDirs) ← SCAN HERE
```

---

## 10. Archive System

**Open:** `ArchiveViewModel.openArchive(path)` → `ArchiveReader.readEntries()`

**Browse (Miller Columns inside ZIP):**
```kotlin
ArchiveReader.listLevel(entries, currentPath) → Pair(folders, files)
// Breadcrumb: Bộ nhớ trong > Download > archive.zip > folder1 > file.txt
```

**Preview mode:** `ArchiveScreen(previewMode=true, autoSelect=true)`
→ `PreviewBottomBar`: Di chuyển | Xóa | Giai nen | Thoat

**Extract:** `ArchiveViewModel.extractSelected(destPath)` → `ArchiveReader.extractEntries()`
→ MediaStoreScanner.scanNewFile(parentPath, null) ← SCAN HERE

**Move inside ZIP:** `extractSelected()` → `removeEntries()`
→ MediaStoreScanner.scanNewFile(parentPath, null) ← SCAN HERE

---

## 11. Clipboard System

In-app clipboard, **tách biệt** với system clipboard. Survives rotation (SavedStateHandle).

```kotlin
data class ClipboardData(
    val items: List<FileItem>,       // file items
    val mode: CLIPBOARD_MODE,         // COPY | MOVE | null
    val sourceDir: String             // thư mục nguồn (check MOVE)
)

ClipboardManager.getClipboardData() // null if empty
ClipboardManager.clear()
```

**`pasteFromClipboard()` flow:**
```
1. Check clipboard empty? → return null
2. Check dest == source (for MOVE)? → error snackbar
3. Detect name conflicts
4. ConflictDialog → user chooses
5. executePaste() → resolve conflicts
6. copyFiles / moveFiles → service
7. clear clipboard
```

---

## 12. Dialog Reference

| Dialog | Trigger | Key Behavior |
|---|:---|---|
| `RenameDialog` | Selection → Đổi tên | Auto-append extension cho file không có extension |
| `DeleteConfirmDialog` | Selection → Xóa | Gọi `TrashViewModel.moveToTrash` |
| `MoveToTrashDialog` | Long-press file | Warning: "File sẽ bị xóa sau 30 ngày" |
| `DetailsDialog` | Selection → Chi tiết | Name, size, path, modified, MIME, owner app |
| `PasswordDialog` | Archive encrypted | Visibility toggle, ok/cancel |
| `ExtractDialog` | Archive → Giải nén | Auto-resolve name conflict (rename) |
| `CompressDialog` | Selection → Nén | Auto-resolve conflict, custom ZIP name input |
| `CreateFolderDialog` | FAB → Folder | Auto-generate "Thư mục N", validate empty/duplicate |
| `CreateFileDialog` | FAB → File | Auto-add .txt, validate empty/duplicate |
| `PasteConflictDialog` | Paste conflict | Thay thế / Đổi tên / Thoát |

---

## 13. UI Components

### `SelectionTopBar`
Thay TopAppBar khi selection mode. "Tất cả" checkbox | "Đã chọn N" | Thoát.

### `SelectionBottomBar`
4 nút chính: Di chuyển | Sao chép | Chia sẻ | Xóa
+ "N.hơn" dropdown: Copy vào bộ nhớ tạm, Chi tiết, Đổi tên, Nén, Giải nén, Yêu thích, Màn hình chờ.

**Menu visibility (MyFiles pattern — menu chỉ hiện khi hành vi hợp lệ):**

| Menu | Điều kiện hiện |
|---|---|
| Nén | `onCompress != null` → chỉ khi **không có** archive files được chọn |
| Giải nén | `onExtract != null` → chỉ khi **có** archive files được chọn |
| Thêm vào yêu thích | `onAddToFavorites != null` → chỉ khi **chưa favorite đủ** |
| Xóa khỏi yêu thích | `onRemoveFromFavorites != null` → chỉ khi **có** item đã favorite |

**`SelectionActions` data class:**
```kotlin
data class SelectionActions(
    val onMove: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onCopyToClipboard: () -> Unit = {},
    val onDetails: () -> Unit = {},
    val onRename: () -> Unit = {},
    val onCompress: (() -> Unit)? = null,              // null = ẩn
    val onExtract: (() -> Unit)? = null,               // null = ẩn
    val onAddToFavorites: (() -> Unit)? = null,       // null = ẩn
    val onRemoveFromFavorites: (() -> Unit)? = null,  // null = ẩn
    val onAddToHomeScreen: () -> Unit = {}
)
```

### `SelectionCheckbox`
Circular checkbox (Samsung style). 28dp, blue fill when selected.

### `SortBar`
Left: "Cần thiết / Tất cả" filter dropdown (chỉ hiện ở root).
Right: Sort type (Ngày/Tên/Kích thước) + direction (↑/↓).

### `Breadcrumb`
Single: 🏠 > Tên | Size
Multi: 🏠 > Bộ nhớ trong > DCIM > Camera
Last segment: màu xanh, không clickable.

### `OperationProgressDialog` (ui/progress)
Samsung My Files style.
- Title: "Đang sao chép...", "Đang di chuyển...", "Đang nén mục..."
- Linear progress bar
- Counter: "1/8" trái, "4%" phải
- 2 buttons: "Thoát" (cancel) | "Ẩn cửa sổ pop-up" (dismiss but continue)

### Coil Custom Fetchers (ui/thumbnail/)
| Fetcher | Method |
|---|---|
| `VideoThumbnailFetcher` | ContentResolver.loadThumbnail (Q+) / ThumbnailUtils (legacy) |
| `AudioThumbnailFetcher` | MediaMetadataRetriever.embeddedPicture (xử lý cả file:// và content:// Uri) |
| `ApkThumbnailFetcher` | PackageManager.getApplicationIcon() → Bitmap (xử lý cả file:// và content:// Uri) |
| `ArchiveThumbnailFetcher` | ArchiveReader.extractToTemp → BitmapFactory.decodeFile |

**Architecture:**
- `ThumbnailFetcher` (abstract base): chứa `fetch()` method + abstract `extractBitmap()`
- `ThumbnailData` (sealed class): Video/Audio/Apk/Archive data classes
- `ThumbnailManager`: singleton đăng ký factories vào ImageLoader

**Lưu ý quan trọng khi dùng ThumbnailData:**
- Luôn check `path.isNotEmpty()` trước khi tạo ThumbnailData.Video/Audio/Apk
- IMAGE dùng Uri trực tiếp, không cần custom fetcher
- `FavoriteThumbnail` track `imageLoaded` state — chỉ có background khi chưa load xong

### Selection Mode Pattern
**Chỉ thoát selection mode khi user nhấn nút Thoát.**

| Hàm | Hành vi |
|---|---|
| `toggle(id)` | Bỏ check → xóa khỏi selectedIds, **giữ mode** |
| `selectAll(ids)` | Bỏ check all → empty set, **giữ mode** |
| `exit()` | Thoát mode — CHỈ gọi khi nhấn nút Thoát |

**Menu visibility logic (MyFiles `ContextualMenuUpdateOperator` pattern):**
- `Nén`: chỉ hiện khi **không có** archive files (`zip/rar/7z/tar/gz`)
- `Giải nén`: chỉ hiện khi **có** archive files được chọn
- `Thêm vào yêu thích`: chỉ hiện khi **ít nhất 1 item chưa favorite** (`favoriteCount < selectedCount`)
- `Xóa khỏi yêu thích`: chỉ hiện khi **có item đã favorite** (`favoriteCount > 0`)

**Bottom bar:** `visible = isSelectionMode && selectedCount > 0`
- Khi selectedCount=0 → bottom bar ẩn, nhưng mode vẫn bật
- Header vẫn hiện "Đã chọn 0" + nút Thoát

---

## 14. Key Design Patterns

### Rotation Safety
`SelectionState`, `ClipboardManager`, `FileActionState`, `TextEditorViewModel` đều dùng `SavedStateHandle`.

### Miller Columns (BrowseScreen landscape)
- `LazyRow` of `ColumnPanel`
- Rightmost column: interactive (selection enabled)
- Navigate into folder → thêm column
- Go back → xóa columns bên phải

### MediaStoreObserver → Auto-reload
`BaseMediaStoreViewModel` auto-reload khi `MediaStoreObserver` emit:
```kotlin
private fun observeMediaStore() {
    viewModelScope.launch {
        MediaStoreObserver.observe(getApplication())
            .collect {
                if (!isOperationRunning()) reload()
            }
    }
}
```
→ Keep UI in sync khi app khác thay đổi file.

### zip4j In-Place Rewrite
`ArchiveReader.removeEntries()` dùng `zipFile.removeFile()` — không cần extract + re-archive toàn bộ.

### MediaStoreScanner Pattern
**Luôn gọi sau mỗi thao tác file** để app khác nhận biết:
```
Operation → java.io.File API → MediaStoreScanner.scan*() → MediaStore update
```
Excluded: `.Trash` directory (tránh lặp vô hạn).

---

## 15. Utility Classes

### `util/Logger.kt`
```kotlin
val log = Logger("TagName")  // tự detect class/method/line
log.d("message")            // Log.d(TAG, "[Class.method:line] message")
log.e("message", exception)
log.w("message")
```

### `util/FormatUtils.kt`
```kotlin
formatFileSize(bytes: Long): String  // "4,2 MB"
formatStorageSize(bytes: Long): String  // "50 GB"
formatDateFull(epochSeconds: Long): String  // "01/03/2024"
```

---

## 16. Khi bắt đầu một task mới

**Đọc CLAUDE.md này trước.** Nếu cần hiểu chi tiết một class cụ thể:
1. Search trong CLAUDE.md → tìm class name
2. Đọc source file đó
3. Nếu cần hiểu luồng: xem section 4 (Data Layer), 7 (File Ops E2E), 9 (Navigation)

**Convention quan trọng:**
- ViewModel luôn extend `BaseMediaStoreViewModel` TRỪ `ArchiveViewModel` (vì archive không dùng MediaStore)
- File operations: LUÔN dùng `FileOperationService` (ForegroundService), không block main thread
- MediaStore change: DÙNG `FileRepository` (MediaStore queries), không query trực tiếp
- Direct file I/O: CHỈ dùng khi cần thao tác nhanh (create folder, create file), LUÔN scan sau đó
- Clipboard: DÙNG `ClipboardManager`, KHÔNG dùng system clipboard

---

## 17. Workflow Conventions

### Mỗi lần bắt đầu session (khi được gọi cho một task mới)

1. **Đọc CLAUDE.md** — đây là nguồn truth cho cấu trúc project và convention
2. **Đọc TASKS.md** (`/.claude/TASKS.md`) — xem lịch sử task đã làm, cập nhật task mới
3. **Đọc các skill liên quan** (trong `/.claude/skills/`) — mỗi skill có `SKILL.md` riêng, đọc trước khi thực hiện action liên quan đến skill đó
4. **Xác nhận đã đọc đủ** — trước khi bắt đầu bất kỳ thay đổi nào, phải confirm đã đọc đủ `CLAUDE.md`, `TASKS.md`, và các skill liên quan. Nếu chưa đọc đủ, đọc ngay trước khi tiến hành.

### Sau khi thực hiện bất kỳ thay đổi nào

**Luôn update các file sau theo thứ tự ưu tiên:**

| Thứ tự | File | Khi nào cập nhật |
|:---:|---|---|
| 1 | `CLAUDE.md` | Thay đổi cấu trúc project (thêm/xóa file, class, screen, dependency mới), thay đổi convention, thay đổi API/data layer |
| 2 | `TASKS.md` | Sau mỗi task hoàn thành, ghi lại: task gì, làm gì, kết quả |
| 3 | `Skills/*.md` liên quan | Thay đổi liên quan đến skill (VD: thêm dialog mới → update `add-dialog/SKILL.md`) |

**Không commit trong quá trình làm** — chỉ commit khi:
- Build thành công (`BUILD SUCCESSFUL`)
- Không có lỗi compile
- Không có warning mới liên quan đến code vừa viết

### Checklist trước khi kết thúc task

- [ ] Build thành công (`./gradlew assembleDebug`)
- [ ] CLAUDE.md đã reflect đúng thay đổi
- [ ] TASKS.md đã ghi task vừa làm
- [ ] Skills liên quan đã update nếu cần
- [ ] (Tuỳ chọn) Commit với message mô tả rõ ràng

### Khi hoàn thành task — báo cáo cho người dùng

**Sau mỗi task, phải hiển thị rõ ràng cho người dùng:**

```
✅ Task: [tên task]
📁 Files đã xóa: [danh sách file]
📝 Code đã thay đổi: [file + mô tả thay đổi]
🛠️ Skills đã dùng: [tên skill 1, skill 2, ...]
📋 Cấu trúc đã update:
   - [file A]: +dòng X, -dòng Y
   - [file B]: +dòng X, -dòng Y
🔧 Build: ✅ SUCCESS / ❌ FAILED
```

**Ví dụ:**
```
✅ Task: Xóa Google Drive
📁 Files đã xóa: GoogleDriveItem.kt, GoogleDriveRepository.kt, googledrive/
📝 Code đã thay đổi:
   - MainActivity.kt: xóa GoogleDriveScreen import + is GoogleDrive branch
   - MainViewModel.kt: xóa Screen.GoogleDrive entry
   - DrawerPane.kt: xóa Google Drive drawer item
   - HomeViewModel.kt: xóa OneDrive + Google Drive storage items
   - FolderPickerSheet.kt: xóa Google Drive filter chip
   - build.gradle.kts: xóa 4 Google Drive API dependencies
🛠️ Skills đã dùng: logging
📋 Cấu trúc đã update:
   - CLAUDE.md: xóa GoogleDriveRepository, GoogleDriveItem, googledrive/, Screen.GoogleDrive
🔧 Build: ✅ SUCCESS
```

**Quy tắc:**
- **Báo cáo luôn có mặt** — kể cả task nhỏ nhất
- **Skills ghi rõ tên** — không ghi chung chung "không có skill"
- **Cấu trúc update** — ghi rõ file nào thay đổi, thêm hoặc bớt gì
- **Build result bắt buộc** — luôn ghi rõ thành công hay thất bại

---

## 18. MyFiles Reference — Pattern Mapping

**Source:** `../MyFiles/MyFiles/app/src/main/java/com/sec/android/app/myfiles/` (Samsung My Files — Kotlin + View system, không dùng Compose)

Dùng MyFiles làm reference khi port pattern sang CodeVui. Bảng mapping chính:

| MyFiles (Samsung) | CodeVui (Compose) | Ghi chú |
|---|---|---|
| `presenter/managers/EnvManager.kt` | `data/StorageVolumeManager.kt` | Port multi-volume logic (Internal/SD/USB) |
| `presenter/managers/ExternalStorageSupporter.kt` | `data/DomainType.kt` + `StorageUsageManager.kt` | Port domain type constants + usage APIs |
| `ui/dialog/DialogManager.kt` | `ui/common/dialogs/DialogManager.kt` | CodeVui: central sealed class state machine thay cho builder pattern |
| `ui/menu/MenuManager.kt` + `MenuManagerInterface.kt` | `ui/selection/SelectionComponents.kt` (`SelectionActions` nullable callbacks) | Port ContextualMenuUpdateOperator rules: hide menu khi invalid |
| `ui/menu/operator/OperationEventManager.kt` | `ui/selection/SelectionActionHandler.kt` | Dispatcher cho actions |
| `ui/manager/AppBarManager.kt` | `ui/selection/SelectionComponents.kt` (`SelectionTopBar`) | Selection top bar |
| `ui/manager/BottomViewManager.kt` | `ui/selection/SelectionComponents.kt` (`SelectionBottomBar`) | Selection bottom bar |
| `ui/manager/ColumnViewManager.kt` | `ui/browse/columnview/*` | Miller columns — CodeVui dùng LazyRow of ColumnPanel |
| `ui/pages/filelist/` | `ui/filelist/` + `ui/browse/` | Pages → Screens |
| `ui/pages/home/` | `ui/home/` | HomeScreen |
| `ui/pages/managestorage/` | `ui/storage/StorageManagerScreen.kt` | Quản lý lưu trữ |
| `presenter/feature/Sep*` | *(không dùng)* | Samsung-only features, bỏ qua |
| `sec/android/app/myfiles/...` package root | `com.example.codevui` | App package |

**Quy tắc port từ MyFiles sang CodeVui:**
1. **Không port View/XML** — CodeVui dùng Compose, tái viết UI theo Material 3
2. **Giữ business logic** — copy over file operations, trash, duplicate detection, domain type
3. **Convert Java-style callback → Kotlin Flow/StateFlow** — MyFiles nhiều chỗ còn dùng callback, CodeVui phải refactor
4. **Skip Samsung proprietary** — tất cả `SemFloatingFeatureWrapper`, `KnoxManager`, `AfwManager`, `DesktopManager` không liên quan
5. **Tên class Việt hoá dễ đọc** — CodeVui ưu tiên naming dễ hiểu bằng tiếng Việt trong comment

Khi không rõ pattern nào đó MyFiles làm ra sao, đọc trực tiếp file tương ứng (dùng agent `myfiles-reference`) rồi port sang CodeVui convention.
