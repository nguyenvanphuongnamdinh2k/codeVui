# CodeVui — Ứng Dụng Quản Lý File Android

> **Mã nguồn mở** — Clone từ project `C:\Users\phuong\Desktop\codeMyFiles\CodeVui\CodeVui`
> **Người phát triển:** nguyen (giacatphuong2k@gmail.com)
> **Ngôn ngữ ưu tiên:** Tiếng Việt (UI + comment)

---

## Mục Lục

1. [Giới thiệu](#1-giới-thiệu)
2. [Cài đặt môi trường](#2-cài-đặt-môi-trường)
3. [Tổng quan kiến trúc](#3-tổng-quan-kiến-trúc)
4. [Package Structure](#4-package-structure)
5. [Công nghệ sử dụng](#5-công-nghệ-sử-dụng)
6. [Các màn hình chính](#6-các-màn-hình-chính)
7. [Hệ thống Dialog](#7-hệ-thống-dialog)
8. [Thao tác File](#8-thao-tác-file)
9. [Thumbnail System](#9-thumbnail-system)
10. [Hệ thống Storage](#10-hệ-thống-storage)
11. [Clipboard & Selection](#11-clipboard--selection)
12. [Trash System](#12-trash-system)
13. [Archive System](#13-archive-system-zip4j)
14. [Database (Room)](#14-database-room)
15. [Theme & UI](#15-theme--ui)
16. [Các file .md trong dự án](#16-các-file-md-trong-dự-án)
17. [Quy tắc quan trọng](#17-quy-tắc-quan-trọng)
18. [Commands hay dùng](#18-commands-hay-dùng)
19. [Debug Tips](#19-debug-tips)

---

## 1. Giới thiệu

**CodeVui** là ứng dụng quản lý file Android, được thiết kế theo phong cách **Samsung My Files** — giao diện quen thuộc, tính năng phong phú, và hiệu năng cao.

### Tính năng chính

| Tính năng | Mô tả |
|---|---|
| **Browse** | Duyệt file với Miller Columns (landscape) hoặc danh sách (portrait) |
| **Multi-volume** | Hỗ trợ Bộ nhớ trong, Thẻ SD, USB OTG |
| **Yêu thích** | Đánh dấu file/folder yêu thích, lưu trong Room DB |
| **Thùng rác** | Xóa an toàn 30 ngày, khôi phục được |
| **Archive** | Xem/nén/giải nén ZIP (hỗ trợ password) |
| **Selection** | Chọn nhiều file, thực hiện batch operations |
| **Clipboard** | Copy/Cut/Paste files trong app |
| **Thumbnails** | Hiển thị thumbnail cho Video/Audio/APK/Archive |
| **Tìm kiếm** | Tìm kiếm với bộ lọc theo loại file, ngày tháng |
| **Recent Files** | File gần đây, nhóm theo ngày |
| **Storage Manager** | Phân tích dung lượng chi tiết |
| **Duplicates** | Tìm file trùng lặp |
| **Large Files** | Tìm file lớn (>100MB) |
| **Unused Apps** | Gỡ ứng dụng ít dùng |
| **Text Editor** | Xem/sửa file text với pinch-zoom |
| **Recommend** | Gợi ý dọn dẹp (file cũ, screenshot, download...) |

### Screenshots style

- Giao diện **Material 3** với **Dynamic Color** (Android 12+)
- **Samsung My Files style** — quen thuộc với người dùng Samsung
- **Selection mode** — bottom bar với 4 nút chính: Di chuyển | Sao chép | Chia sẻ | Xóa

---

## 2. Cài đặt môi trường

### Yêu cầu

| Thành phần | Phiên bản |
|---|---|
| JDK | 17 |
| Android SDK | Android API 36 (compileSdk) |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| IDE | Android Studio Hedgehog (2023.1+) trở lên |
| Gradle | Wrapper (dùng `gradlew.bat` trên Windows) |

### Device test khuyến nghị

- **Chính:** Samsung Galaxy (Android 13+) — real device với `MANAGE_EXTERNAL_STORAGE`
- **Phụ:** Emulator Pixel 6 API 34 — test cho non-Samsung

### Cài đặt nhanh

```bash
# Di chuyển vào thư mục project
cd C:\Users\phuong\Desktop\codeMyFiles\CodeVui\CodeVui

# Build debug
.\gradlew assembleDebug

# Build + cài lên device
.\gradlew installDebug

# Clean + rebuild
.\gradlew clean assembleDebug

# Compile Kotlin nhanh (không build APK)
.\gradlew :app:compileDebugKotlin
```

---

## 3. Tổng quan kiến trúc

CodeVui sử dụng **MVVM + Repository + Service** pattern, tuân thủ Clean Architecture.

```
┌──────────────────────────────────────────────────────────┐
│                      MainActivity.kt                      │
│        Deep Links / Intent / Navigation Orchestration     │
└──────────────────────────┬───────────────────────────────┘
                           │ navigateTo(screen)
┌──────────────────────────▼───────────────────────────────┐
│                     MainViewModel.kt                      │
│              screenStack: MutableStateList<Screen>         │
└──────────────────────────┬───────────────────────────────┘
                           │ Screen Composable
         ┌─────────────────┼────────────────────┐
         │                 │                    │
    HomeScreen       BrowseScreen        ArchiveScreen  ...
         │                 │                    │
         ▼                 ▼                    ▼
   HomeViewModel   BrowseViewModel    ArchiveViewModel
         │                 │                    │
         └────────┬────────┴────────┬────────────┘
                  ▼                 ▼
       BaseMediaStoreViewModel   BaseFileOperationViewModel
                  │                 │
                  │                 ▼
                  │      FileOperationService (ForegroundService)
                  │                 │
                  └────────┬────────┘
                           ▼
              ┌────────────────────────┐
              │      Data Layer         │
              ├────────────────────────┤
              │ FileRepository          │ ← MediaStore queries
              │ MediaStoreScanner       │ ← Notify other apps
              │ FavoriteManager         │ ← Room DB
              │ TrashManager            │ ← .Trash + Room
              │ ArchiveReader           │ ← zip4j
              │ StorageVolumeManager    │ ← Multi-volume
              │ ClipboardManager        │ ← In-app clipboard
              └────────────────────────┘
```

### Luồng dữ liệu

```
User Action
    │
    ▼
Composable (Screen) ──observe──► ViewModel (StateFlow)
    │                                  │
    │ call                             │ call suspend fun
    ▼                                  ▼
────┘                          Repository / Manager
                                       │
                                       ▼
                              MediaStore / Room / File I/O
```

---

## 4. Package Structure

```
com.example.codevui/
│
├── MainActivity.kt          # Entry point, deep-link routing, navigation
├── MainViewModel.kt         # Navigation stack (screenStack), storage info
├── AppImageLoader.kt        # Coil singleton + thumbnail fetchers
│
├── model/
│   └── Model.kt            # Tất cả data class: RecentFile, FavoriteItem,
│                            #   ArchiveEntry, DuplicateGroup, RecommendCard,
│                            #   StorageVolume, FileType enum, RecommendType enum
│
├── data/
│   ├── FileRepository.kt    # MediaStore queries — KHÔNG query trực tiếp
│   ├── FileOperations.kt    # COPY/MOVE/COMPRESS Flow engine (singleton)
│   ├── TrashManager.kt      # .Trash + Room metadata (khởi tạo với Context)
│   ├── ArchiveReader.kt     # zip4j wrapper — read/extract/remove entries
│   ├── MediaStoreScanner.kt # MediaScanner → notify other apps (singleton)
│   ├── MediaStoreObserver.kt# ContentObserver → Flow (debounce 500ms)
│   ├── FavoriteManager.kt   # Room DB CRUD cho favorites (singleton)
│   ├── ClipboardManager.kt  # In-app clipboard (SavedStateHandle)
│   ├── StorageVolumeManager.kt # Multi-volume Internal/SD/USB (singleton)
│   ├── StorageUsageManager.kt  # used/total/free size per volume (singleton)
│   ├── DomainType.kt        # Constants: INTERNAL=1, SD=2, USB=3-8
│   ├── DefaultAppManager.kt # Default app per extension (SharedPreferences)
│   ├── RecommendRepository.kt # Recommend cards — Strategy pattern
│   └── db/
│       ├── AppDatabase.kt   # Room Database v3
│       ├── TrashDao.kt      # Trash CRUD
│       ├── FavoriteDao.kt   # Favorites CRUD
│       ├── TrashEntity.kt   # @Entity trash_items
│       └── FavoriteEntity.kt # @Entity favorites
│
├── service/
│   └── FileOperationService.kt # ForegroundService — multi-op (max 3 đồng thời)
│
└── ui/
    │
    ├── MainActivity.kt + MainViewModel.kt  # Navigation entry
    │
    ├── common/
    │   ├── BaseFileOperationViewModel.kt  # bind FileOperationService
    │   ├── BaseMediaStoreViewModel.kt     # + MediaStoreObserver auto-reload
    │   ├── SelectableScaffold.kt          # Selection-mode scaffold
    │   ├── AdaptiveLayout.kt              # DrawerPane landscape / full portrait
    │   ├── DrawerPane.kt                 # Navigation drawer cho landscape
    │   ├── Interfaces.kt                 # Sortable, Navigable, Reloadable
    │   ├── dialogs/
    │   │   ├── DialogManager.kt          # Central sealed class state machine
    │   │   ├── RenameDialog.kt           # Đổi tên 1 file
    │   │   ├── BatchRenameDialog.kt      # Đổi tên nhiều file
    │   │   ├── DeleteConfirmDialog.kt    # Xác nhận xóa
    │   │   ├── MoveToTrashDialog.kt      # Xác nhận chuyển vào thùng rác
    │   │   ├── DetailsDialog.kt          # Chi tiết file (size, date, path...)
    │   │   ├── PasswordDialog.kt          # Nhập mật khẩu archive
    │   │   ├── ExtractDialog.kt           # Giải nén
    │   │   ├── CompressDialog.kt          # Nén file/folder
    │   │   ├── CreateFolderDialog.kt      # Tạo folder mới
    │   │   ├── CreateFileDialog.kt        # Tạo file mới
    │   │   ├── OpenWithDialog.kt          # Chọn app để mở file
    │   │   └── PasteConflictDialog.kt     # Conflict khi paste
    │   └── viewmodel/
    │       ├── OperationResultManager.kt  # Snackbar kết quả operation
    │       └── PasswordHandler.kt         # Password dialog state
    │
    │                          # SCREENS
    ├── home/                  # Màn hình chính — categories, storage, recent
    │   ├── HomeScreen.kt
    │   ├── HomeViewModel.kt
    │   ├── HomeUiState.kt
    │   └── sections/HomeSections.kt
    │
    ├── browse/               # File browser — Miller Columns (landscape)
    │   ├── BrowseScreen.kt
    │   ├── BrowseViewModel.kt
    │   ├── BrowseUiState.kt
    │   └── columnview/
    │       ├── ColumnBrowseView.kt  # LazyRow of ColumnPanel
    │       ├── ColumnPanel.kt        # 1 column panel
    │       └── ColumnData.kt        # Data class cho 1 column
    │
    ├── filelist/             # Files by MediaStore type (Image/Video/Audio...)
    ├── recent/               # Recent files — nhóm theo ngày
    ├── search/              # Debounced search + type/time filters
    ├── archive/             # Browse inside ZIP + preview mode
    ├── duplicates/          # Tìm file trùng lặp (content hash)
    ├── largefiles/         # File lớn (>100MB)
    ├── unusedapps/         # Ứng dụng không dùng 30 ngày
    ├── recommend/          # Recommend cards (MyFiles style)
    ├── favorites/          # Yêu thích — Room DB
    ├── trash/              # Thùng rác
    ├── storage/           # Quản lý lưu trữ — multi-volume
    ├── texteditor/         # Text editor với pinch-zoom-pan
    │
    ├── selection/          # Selection system (rotation-safe)
    │   ├── SelectionState.kt        # isSelectionMode, selectedIds (SavedStateHandle)
    │   ├── FileActionState.kt       # picker state (SavedStateHandle)
    │   ├── SelectionComponents.kt   # SelectionTopBar, SelectionBottomBar,
    │   │                            #   SelectionCheckbox (tri-state circle)
    │   ├── SelectionActionHandler.kt # Tất cả callbacks cho selection
    │   ├── FolderPickerSheet.kt     # Dialog chọn folder đích
    │   └── PreviewBottomBar.kt      # Preview khi xem trước file
    │
    ├── components/         # Shared UI components
    │   ├── FileListItem.kt     # File row: thumbnail + name + size + date
    │   ├── FolderListItem.kt   # Folder row: emoji + name + item count
    │   ├── ArchiveEntryItem.kt  # Entry row inside ZIP
    │   ├── Breadcrumb.kt       # Breadcrumb navigation bar
    │   ├── SortBar.kt          # Sort: Name/Date/Size/Type, ↑/↓
    │   ├── HighlightedText.kt  # Text với search term highlighted
    │   └── CommonComponents.kt # EmptyState, LoadingIndicator, FileTypeIcon
    │
    ├── progress/          # OperationProgressDialog (Samsung My Files style)
    │
    ├── clipboard/         # ClipboardManager (SavedStateHandle, rotation-safe)
    │
    └── theme/             # Color, Theme, Typography
│
├── ui/thumbnail/         # Thumbnail system
│   ├── ThumbnailManager.kt         # Singleton đăng ký factories vào Coil
│   ├── ThumbnailData.kt            # Sealed class: Video/Audio/Apk/Archive
│   ├── VideoThumbnailFetcher.kt    # ContentResolver.loadThumbnail (Q+)
│   ├── AudioThumbnailFetcher.kt    # MediaMetadataRetriever.embeddedPicture
│   ├── ApkThumbnailFetcher.kt     # PackageManager.getApplicationIcon
│   └── ArchiveThumbnailFetcher.kt  # Extract image từ ZIP đầu tiên
│
└── util/
    ├── Logger.kt          # Structured logging (tự detect class/method/line)
    └── FormatUtils.kt     # formatFileSize, formatStorageSize, formatDateFull
```

---

## 5. Công nghệ sử dụng

| Thư viện | Version | Mục đích |
|---|---|---|
| **Kotlin** | 1.9.x | Ngôn ngữ chính |
| **Jetpack Compose** | BOM | UI Framework |
| **Material 3** | (from Compose BOM) | Design System |
| **Room** | 2.7.0 | Local database (favorites, trash metadata) |
| **Coil** | 2.6.0 | Image loading + custom thumbnail fetchers |
| **zip4j** | 2.11.5 | Đọc/ghi file ZIP, hỗ trợ password |
| **Coroutines** | (from Kotlin) | Async/Background operations |
| **ViewModel** | (from Lifecycle) | State management |
| **SavedStateHandle** | (from Lifecycle) | Survive config change (rotation) |

### Key Permissions

```xml
MANAGE_EXTERNAL_STORAGE    <!-- Full file access (Android 11+) -->
READ_MEDIA_IMAGES          <!-- Android 13+ granular permissions -->
READ_MEDIA_AUDIO
READ_MEDIA_VIDEO
POST_NOTIFICATIONS         <!-- Operation progress notifications -->
FOREGROUND_SERVICE_DATA_SYNC <!-- Background file operations -->
PACKAGE_USAGE_STATS        <!-- Unused apps detection -->
```

---

## 6. Các màn hình chính

### Navigation — Screen sealed class

```kotlin
sealed class Screen {
    object Home                          // Màn hình chính
    data class FileList(type: FileType, title: String)  // Ảnh/Video/Audio...
    data class Browse(sessionId: Int, initialPath: String?)  // Browser
    data class Archive(filePath, fileName, fullPath, isPreviewMode)  // ZIP
    data class RecentFiles(initialSelectedPath: String?)    // Gần đây
    object Favorites                     // Yêu thích
    object Search                        // Tìm kiếm
    object Trash                         // Thùng rác
    object StorageManager                // Quản lý lưu trữ
    object Duplicates                    // File trùng lặp
    object LargeFiles                    // File lớn
    object UnusedApps                   // App ít dùng
    data class TextEditor(filePath, fileName)  // Text editor
    data class Recommend(cardType: RecommendType?)  // Gợi ý
}
```

### Mô tả chi tiết từng màn hình

| Screen | ViewModel | Data Source | Mô tả |
|---|---|---|---|
| **HomeScreen** | HomeViewModel | FileRepository | Grid categories, storage overview, recent files |
| **BrowseScreen** | BrowseViewModel | java.io.File + MediaStore | Duyệt file, Miller Columns (landscape), FAB (create folder/file) |
| **FileListScreen** | FileListViewModel | MediaStore | File theo loại (Image/Video/Audio/Doc/APK/Archive/Download) |
| **RecentFilesScreen** | RecentFilesViewModel | MediaStore | File gần đây, nhóm theo ngày |
| **SearchScreen** | SearchViewModel | MediaStore | Tìm kiếm debounced, filter type/time |
| **ArchiveScreen** | ArchiveViewModel | ArchiveReader (zip4j) | Browse trong ZIP, preview, extract |
| **FavoritesScreen** | FavoritesViewModel | FavoriteManager (Room) | Danh sách yêu thích |
| **TrashScreen** | TrashViewModel | TrashManager (Room) | Thùng rác, countdown 30 ngày |
| **DuplicatesScreen** | DuplicatesViewModel | FileRepository | Tìm trùng lặp (hash) |
| **LargeFilesScreen** | LargeFilesViewModel | FileRepository | File >100MB |
| **UnusedAppsScreen** | UnusedAppsViewModel | PackageManager | App không dùng 30 ngày |
| **StorageManagerScreen** | StorageManagerViewModel | StorageVolumeManager | Phân tích storage chi tiết |
| **RecommendScreen** | RecommendViewModel | RecommendRepository | Cards gợi ý dọn dẹp |
| **TextEditorScreen** | TextEditorViewModel | FileInputStream | Text editor, pinch-zoom |

### Deep Links

| Intent Action | Target | Trigger |
|---|---|---|
| `ACTION_OPEN_FOLDER` | BrowseScreen at `EXTRA_FOLDER_PATH` | Notification tap sau copy/move |
| `ACTION_PREVIEW_ARCHIVE` | ArchiveScreen(isPreviewMode=true) | Notification tap sau compress |

---

## 7. Hệ thống Dialog

### DialogManager — Central State Machine

Tất cả dialog được quản lý qua `DialogManager.kt` — KHÔNG viết dialog độc lập trong screen.

```kotlin
sealed class DialogState {
    object None
    data class Rename(val path: String, val name: String)
    data class BatchRename(val items: List<RenameItem>)
    data class Details(val paths: List<String>)
    data class Compress(val data: CompressData)
    data class Extract(val data: ExtractData)
    data class Password(val data: PasswordData)
    data class DeleteConfirm(val data: DeleteConfirmData)
}

enum class DialogType {
    RENAME, BATCH_RENAME, DETAILS, COMPRESS, EXTRACT,
    PASSWORD, DELETE_CONFIRM
}
```

### Tất cả Dialog

| Dialog | Trigger | Key Behavior |
|---|---|
| **RenameDialog** | Selection → Đổi tên (1 file) | Auto-append extension |
| **BatchRenameDialog** | Selection → Đổi tên (N files) | TextField per item |
| **DeleteConfirmDialog** | Selection → Xóa | Gọi TrashManager |
| **MoveToTrashDialog** | Long-press | "File sẽ bị xóa sau 30 ngày" |
| **DetailsDialog** | Selection → Chi tiết | Name, size, path, modified, MIME, owner app |
| **PasswordDialog** | Archive encrypted | Visibility toggle |
| **ExtractDialog** | Archive → Giải nén | Auto-resolve conflict |
| **CompressDialog** | Selection → Nén | Auto-resolve conflict, custom ZIP name |
| **CreateFolderDialog** | FAB → Folder | Auto-generate "Thư mục N", validate |
| **CreateFileDialog** | FAB → File | Auto-add .txt, validate |
| **OpenWithDialog** | Click file (no default app) | List apps, "Chỉ một lần" / "Luôn luôn" |
| **PasteConflictDialog** | Paste conflict | Thay thế / Đổi tên / Thoát |

### Selection Menu Visibility Rules

| Menu | Điều kiện hiện |
|---|---|
| **Nén** | Chỉ khi KHÔNG có archive files trong selection |
| **Giải nén** | Chỉ khi CÓ archive files trong selection |
| **Thêm vào yêu thích** | Chỉ khi ít nhất 1 item chưa favorite |
| **Xóa khỏi yêu thích** | Chỉ khi có item đã favorite |

---

## 8. Thao tác File

### Tất cả các operation

| Operation | Engine | Scan sau | Long/Short |
|---|---|---|---|
| **COPY** | FileOperationService + FileOperations.execute(COPY) | scanSourceAndDest | Long |
| **MOVE** | FileOperationService + FileOperations.execute(MOVE) | scanSourceAndDest | Long |
| **COMPRESS** | FileOperationService + FileOperations.compressToZip | scanNewFile | Long |
| **EXTRACT** | FileOperationService + FileOperations.extractArchive | scanNewFile | Long |
| **DELETE → trash** | TrashManager.moveToTrash | scanPaths(parentDirs) | Medium |
| **RESTORE** | TrashManager.restore | scanPaths(restoredDirs) | Medium |
| **PERMANENT DELETE** | TrashManager.permanentlyDelete | scanPaths([trashDir]) | Medium |
| **CREATE FOLDER** | java.io.File.mkdirs() direct | scanNewFile | Short |
| **CREATE FILE** | java.io.File.createNewFile() direct | scanNewFile | Short |
| **RENAME** | java.io.File.renameTo() direct | scanPaths([old, new]) | Short |
| **ARCHIVE REMOVE** | ArchiveReader.removeEntries | N/A (in-place) | Medium |

### Quy tắc tuyệt đối

1. **Mọi file operation PHẢI scan MediaStore sau khi xong** — trừ `.Trash` internal ops
2. **Long operation (> 1s) PHẢI qua FileOperationService** — không block UI
3. **Short op (< 100ms) có thể chạy direct trong ViewModel** (mkdir, rename)
4. **Không dùng `DELETE` trực tiếp** — luôn qua Trash (30 ngày recover)
5. **Không copy vào chính mình** — FileOperations đã check

### FileOperationService — ForegroundService

- **Tối đa 3 operation đồng thời** (`MAX_OPERATION_COUNT = 3`)
- **WakeLock** giữ CPU alive trong operation
- **Notification** với progress bar realtime
- **2 nút:** "Thoát" (cancel) / "Ẩn cửa sổ pop-up" (minimize)

```kotlin
// Static methods để start operation
FileOperationService.startCopy(context, sources, destDir): Int  // operationId
FileOperationService.startMove(context, sources, destDir): Int
FileOperationService.startCompress(context, sources, zipName?): Int
FileOperationService.startTrash(context, paths): Int
FileOperationService.startExtract(context, archivePath, entryPaths, destPath, allEntries, password?): Int
FileOperationService.cancelOperation(opId: Int)

val operationsMap: StateFlow<Map<Int, OperationInfo>>  // multi-op tracking
val operationState: StateFlow<ProgressState?>           // backward-compatible single-op
```

### ProgressState — Flow response

```kotlin
sealed class ProgressState {
    object Counting                          // Đang đếm files
    data class Running(                      // Đang chạy
        currentFile: String,
        done: Int,
        total: Int,
        percent: Int
    )
    data class Done(                         // Hoàn thành
        success: Int,
        failed: Int,
        outputPath: String?
    )
    data class Error(message: String)        // Lỗi
}
```

### MediaStoreScanner — Notify other apps

Luôn gọi sau mỗi thao tác file để app khác (gallery, download manager) nhận biết thay đổi.

```kotlin
MediaStoreScanner.scanPaths(context, paths)           // Scan file/folder list
MediaStoreScanner.scanDirectory(context, dirPath)      // Scan toàn bộ directory
MediaStoreScanner.scanSourceAndDest(context, src, dest) // Sau MOVE
MediaStoreScanner.scanNewFile(context, parent, newPath) // Sau CREATE/COMPRESS
```

> **Lưu ý:** `.Trash` directory bị tự động loại trừ khỏi scan.

---

## 9. Thumbnail System

### Kiến trúc

```
AppImageLoader (singleton)
  └── Coil ImageLoader
        ├── MemoryCache: 15% RAM
        ├── DiskCache: 100MB tại cacheDir/thumbnail_cache
        └── Components (đăng ký qua ThumbnailManager.register)
              ├── VideoThumbnailFetcher.Factory
              ├── AudioThumbnailFetcher.Factory
              ├── ApkThumbnailFetcher.Factory
              └── ArchiveThumbnailFetcher.Factory
```

### ThumbnailData — Sealed class

```kotlin
sealed class ThumbnailData {
    data class Video(val uri: Uri, val path: String)
    data class Audio(val uri: Uri, val path: String)
    data class Apk(val uri: Uri, val path: String)
    data class Archive(val uri, val path, val entryPath, val password?)
}
```

### Cách hoạt động mỗi Fetcher

| Fetcher | Phương pháp | Lưu ý |
|---|---|---|
| **VideoThumbnailFetcher** | API 29+: `ContentResolver.loadThumbnail(uri, Size)` | Legacy: `ThumbnailUtils.createVideoThumbnail` |
| **AudioThumbnailFetcher** | `MediaMetadataRetriever.embeddedPicture` | Hỗ trợ cả `content://` và `file://` URIs |
| **ApkThumbnailFetcher** | `PackageManager.getPackageArchiveInfo()` → `loadIcon()` | Extract icon từ APK |
| **ArchiveThumbnailFetcher** | `ArchiveReader.extractToTemp()` → `BitmapFactory.decodeFile` | Cached trong `cacheDir/archive_thumbs/` |

### Lưu ý quan trọng

- **IMAGE files** dùng Uri trực tiếp, không cần custom fetcher
- **Luôn check `path.isNotEmpty()`** trước khi tạo ThumbnailData
- **`FavoriteItem` có thể có mimeType null** → phải resolve từ extension
- **OOM prevention:** dùng `BitmapFactory.Options.inSampleSize` cho file lớn

---

## 10. Hệ thống Storage

### DomainType — Volume Constants

```kotlin
DomainType.INTERNAL  = 1  // Bộ nhớ trong
DomainType.SD       = 2  // Thẻ SD
DomainType.USB_A    = 3  // USB OTG A
DomainType.USB_B    = 4  // USB OTG B
...                  // ...
DomainType.USB_F    = 8  // USB OTG F
DomainType.APP_CLONE= 9  // App clone (Samsung)
```

### StorageVolumeManager

Quản lý tất cả volumes (Internal/SD/USB). Tự động phát hiện khi mount/unmount.

```kotlin
data class StorageVolume(
    val domainType: Int,
    val path: String,           // VD: /storage/emulated/0
    val displayName: String,    // "Bộ nhớ trong", "Thẻ SD", "USB 0"
    val description: String,
    val isRemovable: Boolean,
    val isEmulated: Boolean,
    val isMounted: Boolean,
    val totalBytes: Long,
    val freeBytes: Long
) {
    val usedBytes = totalBytes - freeBytes
    val usedPercent = (usedBytes / totalBytes * 100).toInt()
}

val volumes: StateFlow<List<StorageVolume>>  // Tự động update khi refresh()
```

### Cách lấy thông tin

1. **Internal:** `Environment.getExternalStorageDirectory()` + `correctStorageSize()` (đơn vị thập phân)
2. **API 24+:** `StorageManager.getStorageVolumes()` → reflection cho API < R
3. **Legacy:** Scan `/storage/` directory

---

## 11. Clipboard & Selection

### In-app Clipboard (Tách biệt với system clipboard)

```kotlin
class ClipboardManager(private val savedStateHandle: SavedStateHandle) {
    // Survive rotation qua SavedStateHandle
    // KHÔNG dùng system clipboard cho file operations

    fun copyToClipboard(paths: List<String>)  // mode = COPY
    fun cutToClipboard(paths: List<String>)   // mode = MOVE
    fun clear()
    fun getClipboardData(): Pair<List<String>, OperationType>?

    val hasContent: Boolean
    val itemCount: Int
}
```

### Selection System

**SelectionState** — survive rotation qua SavedStateHandle:
```kotlin
data class SelectionState(
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val activeContextKey: String? = null
)
```

**Selection mode chỉ thoát khi user nhấn nút "Thoát":**
- `toggle(id)` → bỏ check, **giữ mode**
- `selectAll(ids)` → bỏ check all, **giữ mode**
- `exit()` → **CHỈ** gọi khi nhấn nút Thoát

**Bottom bar visibility:** `visible = isSelectionMode && selectedCount > 0`
- Khi selectedCount=0 → bottom bar ẩn, mode vẫn bật
- Header vẫn hiện "Đã chọn 0" + nút Thoát

### SelectionBottomBar Actions

```kotlin
data class SelectionActions(
    val onMove: () -> Unit,
    val onCopy: () -> Unit,
    val onDelete: () -> Unit,
    val onShare: () -> Unit,
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

---

## 12. Trash System

### Kiến trúc

```
TrashManager
├── TrashDao (Room)
│   └── trash_items table
│       ├── id (pk) = trashName
│       ├── originalPath (index)
│       ├── deleteTimeEpoch (index)
│       └── ...
└── .Trash/files/ (filesystem)
    └── {timestamp}_{sanitized_name}
```

### Thông số

| Thông số | Giá trị |
|---|---|
| **Thời hạn** | 30 ngày (`MAX_TRASH_DAYS = 30L`) |
| **Đường dẫn** | `/storage/emulated/0/.Trash/files/` |
| **Tên file trong trash** | `{timestamp}_{original_name}` (sanitized) |
| **Auto-cleanup** | Gọi trong `moveToTrash()`, `getTrashItems()` |

### API

```kotlin
moveToTrash(paths: List<String>): Pair<Int, Int>  // (success, failed)
restore(trashIds: List<String>): Triple<Int, Int, String>  // (success, failed, lastDestDir)
permanentlyDelete(trashIds: List<String>): Pair<Int, Int>
emptyTrash(): Pair<Int, Int>
observeTrashItems(): Flow<List<TrashItem>>
observeTrashCount(): Flow<Int>
getTrashCount(): Int
```

### TrashScreen UI

```kotlin
TrashUiState(
    items: List<TrashItem>,
    isEmpty: Boolean,
    totalCount: Int,
    oldestItemDaysLeft: Int,      // ngày còn lại (item cũ nhất)
    oldestItemProgress: Float     // 0f..1f (elapsed/30)
)
```

---

## 13. Archive System (zip4j)

### Thư viện: `net.lingala.zip4j.ZipFile` v2.11.5

### ArchiveReader API

```kotlin
fun readEntries(archivePath: String, password: String?): List<ArchiveEntry>
fun listLevel(allEntries: List<ArchiveEntry>, currentPath: String): Pair<List<ArchiveEntry>, List<ArchiveEntry>>
fun extractEntries(archivePath, entryPaths, destPath, allEntries, password): Pair<Int, Int>
fun removeEntries(archivePath, entryPaths, allEntries, password): Pair<Int, Int>
fun extractToTemp(archivePath, entryPath, tempDir, password): File?

// Exceptions
class PasswordRequiredException : Exception
class InvalidPasswordException : Exception
```

### Password Handling

```kotlin
// Check encrypted: zipFile.isEncrypted → throw PasswordRequiredException
// Verify password: đọc file đầu tiên → ZipException "Wrong Password"
// Set password: zipFile.setPassword(charArray)
```

### Extract với Byte-level Progress

```kotlin
// FileOperations.extractArchive() dùng cho ForegroundService
// Không dùng zipFile.extractFile() (không có progress)
// Thay vào đó: zipFile.getInputStream(header) + manual buffer read (64KB)

extractArchive(archivePath, entryPaths, destPath, allEntries, password?)
└→ emit ProgressState.Running(percent = handledBytes / totalSize)
```

### Remove entries in-place

zip4j hỗ trợ `zipFile.removeFile(entryPath)` — rewrite ZIP in-place, **nhanh gấp 10x** so với extract + re-archive.

---

## 14. Database (Room)

### Schema

**Version:** 3

| Table | Primary Key | Mô tả |
|---|---|---|
| `trash_items` | `id` (trashName) | Metadata của file trong thùng rác |
| `favorites` | `fileId` (SHA-256 hash) | File/folder yêu thích |

### trash_items

| Column | Type | Notes |
|---|:---:|---|
| id | TEXT PK | = trashName |
| originalName | TEXT | Tên gốc |
| trashName | TEXT | `{timestamp}_{safeName}` |
| originalPath | TEXT | INDEX v2 |
| size | INTEGER | bytes |
| deleteTimeEpoch | INTEGER | seconds, INDEX v2 |
| isDirectory | INTEGER | 0/1 |
| mimeType | TEXT | |
| extension_ | TEXT? | nullable |

### favorites

| Column | Type | Notes |
|---|:---:|---|
| fileId | TEXT PK | SHA-256 hash of path |
| name | TEXT | Display name |
| path | TEXT | Full path, UNIQUE index |
| size | INTEGER | bytes |
| mimeType | TEXT? | nullable |
| isDirectory | INTEGER | 0/1 |
| dateModified | INTEGER | seconds |
| addedAt | INTEGER | seconds |
| sortOrder | INTEGER | INDEX, for drag-drop reorder |

### Migrations

```kotlin
// v1 → v2: Thêm indexes
MIGRATION_1_2 = object : Migration(1, 2) {
    db.execSQL("CREATE INDEX idx_trash_deleteTime ON trash_items(deleteTimeEpoch)")
    db.execSQL("CREATE INDEX idx_trash_originalPath ON trash_items(originalPath)")
}

// v2 → v3: Thêm favorites table
MIGRATION_2_3 = object : Migration(2, 3) {
    db.execSQL("CREATE TABLE favorites (...)")
    db.execSQL("CREATE INDEX idx_favorites_sortOrder ON favorites(sortOrder)")
}
```

---

## 15. Theme & UI

### Color Palette

```kotlin
// Dark theme
Purple80 = Color(0xFFD0BCFF)
PurpleGrey80 = Color(0xFFCCC2DC)
Pink80 = Color(0xFFEFB8C8)

// Light theme
Purple40 = Color(0xFF6650a4)
PurpleGrey40 = Color(0xFF625b71)
Pink40 = Color(0xFF7D5260)
```

### CodeVuiTheme — 3 Theme Modes

```kotlin
@Composable
fun CodeVuiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // Android 12+ Material You
    content: @Composable () -> Unit
)
```

| Mode | Điều kiện | Nguồn màu |
|---|---|---|
| **Dynamic Color** | Android 12+ && dynamicColor=true | Wallpaper (Material You) |
| **Dark** | `darkTheme=true` | Purple/Pink palette |
| **Light** | Default | Purple/Pink palette |

### Typography

Material 3 Typography với font mặc định.

---

## 16. Các file .md trong dự án

### Cấu trúc thư mục .md

```
CodeVui/
├── README.md                          ← File bạn đang đọc
├── CLAUDE.md                          ← Tổng quan codebase (cho Claude AI)
├── CLAUDE.local.md                    ← Ghi chú dev local (KHÔNG commit)
├── TASKS.md                           ← Lịch sử task + TODO
├── SKILLS.md                          ← Quick reference cho skills
│
└── .claude/
    ├── memory.md                      ← Long-term learning/context
    ├── TASKS.md                        ← (trùng lặp với root)
    │
    ├── rules/                          # Convention rules
    │   ├── kotlin-style.md             ← Kotlin naming, nullability, coroutines
    │   ├── compose-style.md            ← Compose patterns, Modifier, Preview
    │   ├── mvvm.md                     ← ViewModel hierarchy, UiState pattern
    │   ├── file-operations.md          ← File op flow + MediaStoreScanner
    │   └── workflow.md                 ← Session workflow, checklist
    │
    ├── reference/                      # Chi tiết tham khảo
    │   ├── database.md                 ← Room schema đầy đủ
    │   ├── dialogs.md                  ← Dialog reference chi tiết
    │   └── ui-components.md            ← UI components chi tiết
    │
    ├── skills/                         # Step-by-step playbooks
    │   ├── add-dialog/SKILL.md        ← Thêm dialog mới
    │   ├── add-screen/SKILL.md         ← Thêm screen mới
    │   ├── add-file-operation/SKILL.md ← Thêm file operation
    │   ├── add-thumbnail-fetcher/SKILL.md ← Thêm thumbnail fetcher
    │   ├── logging/SKILL.md            ← Logging convention
    │   ├── mediastore/SKILL.md         ← MediaStore & File I/O guide
    │   ├── task-tracking/SKILL.md      ← Task tracking system
    │   ├── updating-claude/SKILL.md    ← Cập nhật CLAUDE.md
    │   └── auto-build/SKILL.md         ← Auto-build workflow
    │
    └── agents/                         # Claude agent personas
        ├── code-reviewer.md           ← Review code (MVVM, Compose, conventions)
        ├── build-verifier.md           ← Run gradle build + verify
        └── refactor-assistant.md       ← Hỗ trợ refactor code
```

### Mô tả chi tiết từng file

#### Root level

| File | Mục đích | Ai đọc |
|---|---|---|
| **README.md** | Tài liệu dự án, hướng dẫn cài đặt, tổng quan | **Người mới vào dự án lần đầu** |
| **CLAUDE.md** | Project spec đầy đủ, architecture, patterns, conventions | Claude AI khi bắt đầu session |
| **CLAUDE.local.md** | Ghi chú dev local (env, shortcuts, TODO cá nhân) | Chỉ developer trên máy này |
| **TASKS.md** | Lịch sử task đã làm + TODO list | Claude AI để hiểu context giữa sessions |
| **SKILLS.md** | Quick reference: invoke skills = `/skill-name`, conventions nhanh | Claude AI hoặc developer |

#### .claude/rules/

| File | Nội dung |
|---|---|
| **kotlin-style.md** | Naming conventions, nullability, coroutines, collections, scope functions, imports, comments, error handling |
| **compose-style.md** | Composable structure, state hoisting, side effects, Modifier order, Preview, Recomposition, Material 3, Performance |
| **mvvm.md** | Layer separation, ViewModel hierarchy, UiState pattern, Rotation Safety, Repository/Manager rules, Flow convention |
| **file-operations.md** | Tất cả operation types, engine, scan rules, Conflict Resolution, Clipboard, Progress, Background Safety |
| **workflow.md** | Session workflow (1.Start → 2.Work → 3.End), forbidden rules, recommendations |

#### .claude/reference/

| File | Nội dung |
|---|---|
| **database.md** | Room schema đầy đủ (trash_items, favorites), columns, indexes, DAO methods, migrations, AppDatabase |
| **dialogs.md** | Dialog reference: tất cả 12 dialog types, trigger, behavior, DialogManager sealed class |
| **ui-components.md** | UI components chi tiết: SelectionTopBar, SelectionBottomBar, SortBar, Breadcrumb, Thumbnail system |

#### .claude/skills/

| Skill | Khi nào dùng |
|---|---|
| **add-dialog** | Cần thêm dialog mới (xác nhận xóa, đổi tên, tạo folder...) |
| **add-screen** | Cần thêm màn hình mới (screen + ViewModel + UiState) |
| **add-file-operation** | Cần thêm thao tác file mới (COPY/MOVE/COMPRESS/EXTRACT/RENAME) |
| **add-thumbnail-fetcher** | Cần load thumbnail cho file type mới (PDF, EPUB, SVG...) |
| **logging** | Thêm log vào code mới, dùng `Logger("Tag")` |
| **mediastore** | Tương tác MediaStore/File I/O, khi nào dùng gì |
| **task-tracking** | Bắt đầu task mới, cập nhật TASKS.md |
| **updating-claude** | Sau khi build thành công, cập nhật CLAUDE.md |
| **auto-build** | Chạy gradle build, đọc lỗi, verify |

#### .claude/agents/

| Agent | Mục đích |
|---|---|
| **code-reviewer** | Review code theo conventions, đưa ra vấn đề + fix đề xuất |
| **build-verifier** | Chạy gradle build, parse lỗi, báo cáo |
| **refactor-assistant** | Hỗ trợ refactor (extract class, rename, move, convert pattern) |

### Thứ tự đọc cho người mới

```
1. README.md (file này)          ← Tổng quan dự án
2. CLAUDE.md                      ← Kiến trúc chi tiết
3. .claude/rules/kotlin-style.md ← Coding conventions
4. .claude/rules/mvvm.md         ← Architecture pattern
5. .claude/rules/compose-style.md← UI conventions
6. .claude/rules/file-operations.md ← Quan trọng! File operations
```

---

## 17. Quy tắc quan trọng

### Tuyệt đối tuân thủ

| # | Quy tắc | Lý do |
|---|---|---|
| 1 | **KHÔNG query MediaStore trực tiếp** — luôn qua `FileRepository` | Đảm bảo consistent caching + abstraction |
| 2 | **KHÔNG dùng `!!`** trong code mới | Tránh NPE crash |
| 3 | **KHÔNG block main thread** — mọi I/O phải `Dispatchers.IO` | UI không bị jank |
| 4 | **KHÔNG dùng system clipboard cho file ops** — dùng `ClipboardManager` | System clipboard không support MOVE semantics |
| 5 | **Mọi file operation PHẢI scan MediaStore sau xong** (trừ `.Trash`) | App khác nhận biết thay đổi |
| 6 | **Dialog LUÔN qua DialogManager** — không viết dialog độc lập | Centralized state, dễ quản lý |
| 7 | **Long op (>1s) LUÔN qua FileOperationService** | Android kill background nếu không foreground |
| 8 | **KHÔNG commit khi build fail** | Đảm bảo codebase luôn build được |

### Convention đặt tên

| Loại | Pattern | Ví dụ |
|---|---|---|
| Class | PascalCase | `FileRepository`, `BrowseViewModel` |
| Function | camelCase | `loadFiles()`, `moveToTrash()` |
| Constant | UPPER_SNAKE_CASE | `MAX_TRASH_DAYS`, `NOTIFICATION_ID` |
| Composable | PascalCase | `BrowseScreen`, `SelectionTopBar` |
| Private member | no prefix | `private val fileRepo` (không `_` hay `m`) |
| Backing field | `_state` + `state: StateFlow` | `_uiState` + `uiState: StateFlow` |
| Dialog | `*Dialog.kt` | `RenameDialog.kt` (không prefix) |
| Screen | `*Screen.kt` + `*ViewModel.kt` + `*UiState.kt` | 3 file nhóm |

### ViewModel Hierarchy

```
AndroidViewModel
└── BaseFileOperationViewModel
    ├── BaseMediaStoreViewModel          ← Dùng MediaStore → extend cái này
    │   ├── BrowseViewModel              ← Implements Sortable + Navigable + Reloadable
    │   ├── FileListViewModel
    │   ├── RecentFilesViewModel
    │   ├── SearchViewModel
    │   ├── DuplicatesViewModel
    │   ├── LargeFilesViewModel
    │   ├── UnusedAppsViewModel
    │   ├── RecommendViewModel
    │   └── StorageManagerViewModel
    ├── FavoritesViewModel               ← Room DB (không MediaStore)
    └── ArchiveViewModel                 ← Archive (không MediaStore) → extend BaseFileOperationViewModel
```

---

## 18. Commands hay dùng

```bash
# Di chuyển vào thư mục project
cd C:\Users\phuong\Desktop\codeMyFiles\CodeVui\CodeVui

# Build debug (nhanh nhất)
.\gradlew assembleDebug --no-daemon

# Build + cài lên device
.\gradlew installDebug

# Clean + rebuild
.\gradlew clean assembleDebug --no-daemon

# Compile Kotlin nhanh (không build APK)
.\gradlew :app:compileDebugKotlin

# Verify KSP (cho Room schema)
.\gradlew :app:kspDebugKotlin --stacktrace

# Full build với tất cả tasks
.\gradlew build --no-daemon
```

### Build thường fail do

- **Room schema mismatch** → tăng version + viết Migration
- **Coil API breaking change** → check bump lại Coil 2.6.0
- **zip4j exception mới** → catch thêm `ZipException`
- **Import thiếu** → thêm `import package.Xxx`

---

## 19. Debug Tips

### Logging

```kotlin
// Dùng Logger utility — tự detect class/method/line
private val log = Logger("BrowseScreen")
log.d("loadDirectory: path='$path'")
log.e("loadDirectory failed: $path", exception)  // ← TRUYỀN exception!
```

Filter logcat: `[ClassName.method:line]`

### Các vấn đề thường gặp

| Vấn đề | Cách debug |
|---|---|
| FileOperationService không chạy | Check notification permission + ForegroundServiceType |
| MediaStoreObserver không emit | Thường do `.Trash` bị observed — check excluded paths |
| Coil không load thumbnail | Check `ThumbnailManager.setup(context)` trong `AppImageLoader` |
| Trash debug | Xem `.Trash/files/` trên device |
| Room migration fail | Check schema export trong `app/schemas/` |

### Common Gotchas

- **Selection mode không thoát khi uncheck hết** — đúng behavior (giống MyFiles)
- **`getDataDirectory()` ≠ `getExternalStorageDirectory()`** — dùng cái sau cho user-facing
- **`getRecentFiles()` sort date_modified DESC** — file càng mới càng trên
- **`FavoriteItem.mimeType` có thể null** — luôn resolve từ extension
- **`AudioThumbnailFetcher` cần handle cả `content://` và `file://` URIs**
- **Intent không chứa được object lớn** → dùng static `ConcurrentHashMap` cache

---

## License

Mã nguồn mở. Clone và tùy chỉnh theo nhu cầu.

## Liên hệ

- **Developer:** nguyen (giacatphuong2k@gmail.com)
- **Device chính:** Samsung Android 13+
- **Style tham khảo:** Samsung My Files
