# CodeVui — CLAUDE.md

> Đọc file này TRƯỚC TIÊN trước khi bắt đầu bất kỳ task nào.

**Companion files:**
- `.claude/CLAUDE.local.md` — ghi chú dev local (không commit)
- `.claude/memory.md` — long-term learning/context
- `.claude/TASKS.md` — lịch sử task + TODO
- `.claude/rules/` — convention rules
- `.claude/reference/` — chi tiết: [database.md](.claude/reference/database.md) · [dialogs.md](.claude/reference/dialogs.md) · [ui-components.md](.claude/reference/ui-components.md)
- `.claude/agents/` — agent personas
- `.claude/skills/` — step-by-step playbooks

- `.claude/skills/` — step-by-step playbooks (add-dialog, add-screen, add-file-operation, ...)

---

## 1. Project Overview

| Property | Value |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Min SDK | 24 |
| Target / Compile SDK | 36 |
| Architecture | MVVM |
| Database | Room 2.7.0 |
| Image Loading | Coil 2.6.0 |
| Archive | zip4j 2.11.5 |

**Key Permissions:** `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES/AUDIO/VIDEO`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_DATA_SYNC`, `PACKAGE_USAGE_STATS`

---

## 2. Package Structure

```
com.example.codevui/
├── AppImageLoader.kt           # Coil singleton + custom fetchers
├── MainActivity.kt             # Single Activity — deep-link routing
├── MainViewModel.kt            # Navigation stack (screenStack)
│
├── model/
│   └── Model.kt               # Tất cả data class + FileType enum
│
├── data/
│   ├── FileRepository.kt      # MediaStore queries (instantiate với Context)
│   ├── FileOperations.kt      # COPY/MOVE/COMPRESS Flow engine (singleton)
│   ├── TrashManager.kt        # Thùng rác .Trash + Room metadata
│   ├── ArchiveReader.kt       # zip4j wrapper (singleton)
│   ├── MediaStoreScanner.kt   # MediaScanner → notify other apps (singleton)
│   ├── MediaStoreObserver.kt  # ContentObserver → Flow (singleton)
│   ├── FavoriteManager.kt     # Yêu thích Room DB CRUD (singleton)
│   ├── RecommendRepository.kt # Recommend cards — Strategy pattern
│   ├── StorageVolumeManager.kt # Multi-volume Internal/SD/USB (singleton)
│   ├── DomainType.kt          # Domain constants (INTERNAL=1, SD=2, USB=3-8)
│   ├── StorageUsageManager.kt # used/total/free size per volume
│   ├── DefaultAppManager.kt   # Default app per extension
│   └── db/                    # AppDatabase, TrashDao, FavoriteDao, migrations
│
├── service/
│   └── FileOperationService.kt  # ForegroundService — multi-op (max 3)
│
└── ui/
    ├── MainActivity.kt + MainViewModel.kt   # Navigation
    │
    ├── common/
    │   ├── BaseFileOperationViewModel.kt    # bind FileOperationService
    │   ├── BaseMediaStoreViewModel.kt       # + MediaStoreObserver auto-reload
    │   ├── SelectableScaffold.kt            # Selection-mode scaffold
    │   ├── AdaptiveLayout.kt                # DrawerPane landscape / full portrait
    │   ├── Interfaces.kt                    # Sortable, Navigable, Reloadable
    │   ├── dialogs/DialogManager.kt         # Central sealed class state machine
    │   └── viewmodel/
    │       ├── OperationResultManager.kt   # Snackbar kết quả
    │       └── PasswordHandler.kt           # Password dialog state
    │
    ├── home/              # Categories grid, recent, storage
    ├── browse/            # File browser — Miller Columns landscape
    │   └── columnview/    # ColumnData, ColumnBrowseView, ColumnPanel
    ├── filelist/          # Files by MediaStore type
    ├── recent/            # Recent files grouped by day
    ├── search/            # Debounced search + type/time filters
    ├── archive/           # Browse inside ZIP + preview mode
    ├── duplicates/        # File trùng lặp
    ├── largefiles/        # File lớn
    ├── unusedapps/        # Ứng dụng không dùng
    ├── recommend/         # Recommend cards (MyFiles style)
    ├── favorites/         # Yêu thích — Room DB
    ├── trash/             # Thùng rác
    ├── storage/           # Quản lý lưu trữ — multi-volume
    ├── texteditor/        # Text editor với pinch-zoom-pan
    ├── clipboard/         # In-app COPY/MOVE clipboard (rotation-safe)
    ├── selection/         # SelectionState, FileActionState, SelectionActionHandler,
    │                      #   SelectionComponents, FolderPickerSheet, PreviewBottomBar
    ├── components/        # FileListItem, FolderListItem, ArchiveEntryItem,
    │                      #   Breadcrumb, SortBar, HighlightedText, CommonComponents
    ├── progress/          # OperationProgressDialog (Samsung My Files style)
    └── theme/             # Color, Theme, Type
│
├── ui/thumbnail/         # Thumbnail system
│   ├── ThumbnailManager.kt
│   ├── VideoThumbnailFetcher.kt
│   ├── AudioThumbnailFetcher.kt
│   ├── ApkThumbnailFetcher.kt
│   └── ArchiveThumbnailFetcher.kt
│
└── util/
    ├── Logger.kt          # Structured logging (tự detect class/method/line)
    └── FormatUtils.kt     # formatFileSize, formatStorageSize, formatDateFull
```

**Quy tắc:**
- ViewModel extend `BaseMediaStoreViewModel` TRỪ `ArchiveViewModel` (archive không dùng MediaStore)
- File ops LUÔN qua `FileOperationService`, không block main thread
- MediaStore change: DÙNG `FileRepository`, không query trực tiếp
- Clipboard: DÙNG `ClipboardManager`, KHÔNG dùng system clipboard
- Dialog: LUÔN qua `DialogManager`, không viết dialog độc lập trong screen

---

## 3. Data Classes (model/Model.kt)

```kotlin
enum class FileType { IMAGE, VIDEO, AUDIO, DOC, APK, ARCHIVE, DOWNLOAD, OTHER }

data class RecentFile(val name, date, type: FileType, uri: Uri, path: String,
    val size: Long, val dateModified: Long, val ownerApp: String? = null)

data class FolderItem(val name, path: String, val dateModified: Long,
    val itemCount: Int, val isPinned: Boolean)

data class ArchiveEntry(val name, path: String, val size, val compressedSize: Long,
    val isDirectory: Boolean, val lastModified: Long)

data class StorageVolume(
    val domainType: Int, val path: String, val displayName: String,
    val isRemovable: Boolean, val isEmulated: Boolean, val isMounted: Boolean,
    val totalBytes: Long, val freeBytes: Long
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedPercent: Int get() = ((usedBytes.toDouble() / totalBytes) * 100).toInt()
}

enum class RecommendType(val value: Int) {
    OLD_MEDIA_FILES(0), UNNECESSARY_FILES(1), SCREENSHOT_FILES(2),
    DOWNLOAD_FILES(3), COMPRESSED_FILES(8)
}

data class StorageAnalysis(
    val videoBytes, imageBytes, audioBytes: Long,
    val archiveBytes, apkBytes, docBytes: Long,
    val trashBytes, unusedAppsBytes: Long,
    val duplicateBytes, largeFilesBytes, oldScreenshotsBytes: Long
)
```

Chi tiết data class đầy đủ: [reference/database.md](.claude/reference/database.md)

---

## 4. Data Layer — Key APIs

### FileRepository.kt
Instantiate với Context.

| API | Mô tả |
|---|---|
| `getRecentFiles(limit)` | Query MediaStore, date_modified DESC |
| `getFilesByType(type, sortBy, ascending)` | Files theo MIME type |
| `listDirectory(path, sortBy, ascending)` | Pair(folders, files) từ java.io.File |
| `searchFiles(query, fileTypes?, afterTimestamp?)` | Full-text search |
| `findLargeFiles(minBytes)` | Files > minBytes, sorted SIZE DESC, exclude .Trash |
| `getUnusedApps()` | Apps không dùng 30 ngày, user apps only |
| `getVolumeFileBreakdown(path)` | File breakdown cho SD/USB |

### FileOperations.kt (singleton)
Tất cả trả về `Flow<ProgressState>`.

```kotlin
object FileOperations {
    fun execute(sources, destDir, COPY|MOVE): Flow<ProgressState>
    fun compressToZip(sources, customName?): Flow<ProgressState>
    fun extractArchive(archivePath, entryPaths, destPath, allEntries, password?): Flow<ProgressState>
}

sealed class ProgressState {
    object Counting
    data class Running(currentFile, done, total, percent)
    data class Done(success, failed, outputPath?)
    data class Error(message)
}
```

### FileOperationService.kt
ForegroundService multi-operation (max 3 đồng thời).

```kotlin
fun startCopy(context, sources, destDir): Int
fun startMove(context, sources, destDir): Int
fun startCompress(context, sources, zipName?): Int
fun startExtract(context, archivePath, entryPaths, destPath, allEntries, password?): Int
fun cancelOperation(opId: Int)
val operationsMap: StateFlow<Map<Int, OperationInfo>>
```

Chi tiết file operations + MediaStoreScanner: xem `.claude/rules/file-operations.md`

### TrashManager.kt
Khởi tạo với Context. `.Trash/files/<ts>_<safeName>`, MAX_TRASH_DAYS = 30.

| API | Mô tả |
|---|---|
| `moveToTrash(paths)` | Copy → delete → Room metadata → scan parent dirs |
| `restore(ids)` | Copy từ trash về originalPath → scan dest dirs |
| `permanentlyDelete(ids)` | Xóa vĩnh viễn → scan .Trash |
| `observeTrashItems()` | Room Flow → auto-update UI |

### ArchiveReader.kt (singleton)
Dùng zip4j.

| API | Mô tả |
|---|---|
| `readEntries(path, password?)` | Đọc tất cả entries trong ZIP |
| `listLevel(entries, currentPath)` | Miller-columns listing theo level |
| `extractEntries(archive, paths, dest, all, password?)` | Giải nén có chọn lọc |
| `removeEntries(archive, paths, all, password?)` | Xóa khỏi ZIP (in-place, dùng zipFile.removeFile()) |

### MediaStoreScanner.kt (singleton)
Luôn gọi sau mỗi thao tác file (TRỪ `.Trash` internal ops).

```kotlin
scanPaths(paths)           // Scan file/folder, tự expand folder → files
scanSourceAndDest(src, dest)  // Sau MOVE
scanNewFile(parent, newPath)  // Sau CREATE/COMPRESS
```

### MediaStoreObserver.kt (singleton)
ContentObserver → Flow, debounce 500ms. Dùng trong `BaseMediaStoreViewModel` để auto-reload khi MediaStore thay đổi.

### FavoriteManager.kt (singleton)
Room DB CRUD.

| API | Mô tả |
|---|---|
| `observeFavorites(context)` | Flow → auto-update UI khi DB thay đổi |
| `addFavorite(...)` / `removeFavorite(path)` | Thêm/xóa 1 file |
| `removeFavorites(paths)` | Xóa nhiều file |
| `toggleFavorite(...)` | Toggle thêm/xóa |
| `validateFavorites(context)` | Xóa favorites không tồn tại (lazy) |

---

## 5. ViewModel Hierarchy

```
AndroidViewModel
└── BaseFileOperationViewModel          # bind FileOperationService
    ├── BaseMediaStoreViewModel          # + MediaStoreObserver auto-reload
    │   ├── BrowseViewModel              # + Sortable + Navigable + Reloadable
    │   │   └── (LruCache 50, AtomicBoolean lock)
    │   ├── FileListViewModel
    │   ├── RecentFilesViewModel
    │   ├── SearchViewModel
    │   ├── DuplicatesViewModel
    │   ├── LargeFilesViewModel
    │   ├── UnusedAppsViewModel
    │   ├── RecommendViewModel
    │   └── StorageManagerViewModel
    ├── FavoritesViewModel               # + SelectionState (Room DB, không MediaStore)
    └── ArchiveViewModel                  # extract/move qua service, không MediaStore
```

---

## 6. Navigation

```kotlin
sealed class Screen {
    object Home
    data class FileList(type: FileType, title: String)
    data class Browse(path: String = "")       // "" = root
    data class Archive(path: String, name: String = "")
    object RecentFiles
    object Favorites
    object Search
    object Trash
    object StorageManager
    object Duplicates
    object LargeFiles
    object UnusedApps
    data class TextEditor(path: String)
    data class Recommend(cardType: RecommendType? = null)
}
```

**Deep links:**

| Intent Action | Target |
|---|---|
| `ACTION_OPEN_FOLDER` | BrowseScreen at `EXTRA_FOLDER_PATH` |
| `ACTION_PREVIEW_ARCHIVE` | ArchiveScreen(previewMode=true) |
| `android.intent.action.VIEW` text/* | TextEditorScreen |
| `android.intent.action.VIEW` archive | ArchiveScreen |
| `android.intent.action.VIEW` other | External Intent (FileProvider) |

**MainViewModel:** `screenStack: MutableStateList<Screen>` — survives recomposition
- `navigateTo(screen)` → push
- `navigateReplace(screen)` → replace top
- `goBack()` → pop

---

## 7. Clipboard System

In-app clipboard, **tách biệt** với system clipboard. Survives rotation (SavedStateHandle).

```kotlin
data class ClipboardData(
    val items: List<FileItem>,
    val mode: CLIPBOARD_MODE,    // COPY | MOVE | null
    val sourceDir: String        // check dest == source cho MOVE
)

ClipboardManager.getClipboardData()  // null if empty
ClipboardManager.clear()
```

**`pasteFromClipboard()` flow:**
1. Check clipboard empty → return null
2. Check dest == source (MOVE) → error snackbar
3. Detect name conflicts
4. ConflictDialog → user chooses
5. executePaste() → resolve conflicts
6. copyFiles / moveFiles → service
7. clear clipboard

---

## 8. Key Design Patterns

### Rotation Safety
`SelectionState`, `ClipboardManager`, `FileActionState`, `TextEditorViewModel` đều dùng `SavedStateHandle`.

### Miller Columns (BrowseScreen landscape)
- `LazyRow` of `ColumnPanel`
- Rightmost column: interactive (selection enabled)
- Navigate into folder → thêm column
- Go back → xóa columns bên phải

### zip4j In-Place Rewrite
`ArchiveReader.removeEntries()` dùng `zipFile.removeFile()` — không cần extract + re-archive toàn bộ.

### MediaStoreScanner Pattern
```
Operation → java.io.File API → MediaStoreScanner.scan*() → MediaStore update
```
Luôn gọi sau mỗi thao tác file. Excluded: `.Trash` directory.

---

## 9. Utility Classes

### util/Logger.kt
```kotlin
val log = Logger("TagName")  // tự detect class/method/line
log.d("message")
log.e("message", exception)
```

### util/FormatUtils.kt
```kotlin
formatFileSize(bytes: Long)        // "4,2 MB"
formatStorageSize(bytes: Long)     // "50 GB"
formatDateFull(epochSeconds: Long) // "01/03/2024"
```

---

## 10. Workflow Conventions

### Mỗi lần bắt đầu task mới

1. **Đọc CLAUDE.md này**
2. **Đọc TASKS.md** (`.claude/TASKS.md`)
3. **Đọc skill liên quan** (`.claude/skills/`)
4. **Xác nhận đã đọc đủ** trước khi sửa code

### Convention quan trọng

- **File operations:** LUÔN qua `FileOperationService` (long) hoặc direct (create folder < 100ms), LUÔN scan sau đó
- **Không `!!`** trong code mới
- **Block main thread cấm** — mọi I/O phải `Dispatchers.IO`
- **Không query MediaStore trực tiếp** — qua `FileRepository`
- **Dialog:** LUÔN qua `DialogManager`, không viết dialog độc lập

### Checklist trước khi kết thúc task

- [ ] Build thành công (`./gradlew assembleDebug`)
- [ ] CLAUDE.md reflect đúng thay đổi
- [ ] TASKS.md ghi task vừa làm
- [ ] Skills liên quan update nếu cần
- [ ] Báo cáo cho user theo format bên dưới

### Báo cáo khi hoàn thành task

```
✅ Task: [tên task]
📝 Code đã thay đổi: [file + mô tả]
🛠️ Skills đã dùng: [tên skill]
🔧 Build: ✅ SUCCESS / ❌ FAILED
```

### Khi cần chi tiết thêm

| Chi tiết về | Xem file |
|---|---|
| Thêm dialog | `.claude/skills/add-dialog/SKILL.md` |
| Thêm screen | `.claude/skills/add-screen/SKILL.md` |
| Thêm file operation | `.claude/skills/add-file-operation/SKILL.md` |
| Thêm thumbnail fetcher | `.claude/skills/add-thumbnail-fetcher/SKILL.md` |
| File operations + progress | `.claude/rules/file-operations.md` |
| Kotlin style | `.claude/rules/kotlin-style.md` |
| Compose style | `.claude/rules/compose-style.md` |
| MVVM convention | `.claude/rules/mvvm.md` |
| Workflow | `.claude/rules/workflow.md` |

---

## 11. MyFiles Reference

| MyFiles | CodeVui | Ghi chú |
|---|---|---|
| `presenter/managers/EnvManager.kt` | `StorageVolumeManager.kt` | Port multi-volume |
| `ui/dialog/DialogManager.kt` | `dialogs/DialogManager.kt` | sealed class state machine |
| `ui/menu/MenuManager.kt` | `SelectionComponents.kt` | nullable callbacks |
| `ui/manager/ColumnViewManager.kt` | `browse/columnview/*` | LazyRow of ColumnPanel |
| `presenter/feature/Sep*` | *(không dùng)* | Samsung-only, bỏ qua |

**Quy tắc port:**
1. Không port View/XML → tái viết UI theo Material 3
2. Giữ business logic
3. Convert callback → Kotlin Flow/StateFlow
4. Skip Samsung proprietary (`SemFloatingFeatureWrapper`, `KnoxManager`, etc.)
