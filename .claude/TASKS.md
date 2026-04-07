# TASKS — CodeVui

> Project memory giữa các Claude sessions.
> **Quan trọng:** TASKS.md là persistent memory duy nhất. Đọc file này khi bắt đầu session mới.

---

## 🔄 Update Log

| Ngày | Người cập nhật | Ghi chú |
|---|---|---|
| 2026-03-31 | Claude | Hoàn thành Task #001: Google Drive screen (đăng nhập + browse + upload/download) |
| 2026-04-02 | Claude | Hoàn thành Task #002: StorageManager screen (cơ bản) |
| 2026-04-02 | Claude | Hoàn thành Task #003: Nâng cấp StorageManager — chi tiết đầy đủ (Ứng dụng/Hệ thống/File khác trong storage bar) |
| 2026-04-03 | Claude | Hoàn thành Task #007: Fix DuplicatesScreen build + selection + delete dialog |
| 2026-04-03 | Claude | Hoàn thành Task #008: Tạo LargeFilesScreen (Samsung My Files style) |
| 2026-04-03 | Claude | Hoàn thành Task #009: Fix dung lượng sai trong StorageManager (duplicate/large/trash) |
| 2026-04-03 | Claude | Hoàn thành Task #010: Fix duplicate detection không nhận file đổi tên |
| 2026-04-03 | Claude | Hoàn thành Task #011: Fix SelectionCheckbox không hiện dấu tích toàn app |
| 2026-04-03 | Claude | Hoàn thành Task #012: Tạo UnusedAppsScreen (Samsung My Files style) |
| 2026-04-04 | Claude | Hoàn thành Task #013: Triển khai Favorites (MyFiles style) — Room DB, FavoritesScreen, DrawerPane, HomeScreen |
| 2026-04-06 | Claude | Hoàn thành Task #014: Fix Favorites thumbnail không load (mimeType null + AudioThumbnailFetcher API lỗi) |
| 2026-04-06 | Claude | Hoàn thành Task #015: Sửa selection mode — giữ mode khi uncheck hết, chỉ thoát khi nhấn Thoát |

---

## 📌 TODO — Tasks Sắp Làm

### Priority: Cao
- [ ] **Thêm OAuth consent screen config** — Cấu hình Google Cloud Console để app có thể đăng nhập Drive thực sự

### Priority: Trung bình
- [ ] **Thêm Google Drive folder picker dialog** — Thay vì chỉ move/copy đến root, cần dialog chọn folder đích

---

## ✅ Tasks Đã Hoàn Thành

### Task #001 — Google Drive Screen
**Ngày:** 2026-03-31
**Mô tả:** Tạo màn hình Google Drive đầy đủ chức năng: đăng nhập Google, browse files/folders, upload, download, copy, move, delete, đổi tên.

### Task #003 — Nâng cấp Storage Manager: chi tiết đầy đủ
**Ngày:** 2026-04-02
**Mô tả:** Mở rộng màn hình Quản lý lưu trữ — thêm Ứng dụng/Hệ thống/File khác vào storage bar + danh sách chi tiết, dùng PackageManager getPackageSizeInfo cho app sizes, residual computation cho system/other.

**Files đã sửa:**
- `ui/storage/StorageManagerUiState.kt`: Thêm fields `appsBytes`, `systemBytes`, `otherBytes`, `totalMediaStoreBytes`
- `data/FileRepository.kt`: Thêm `getInstalledAppsSize(): suspend` với IPackageDataObserver reflection + `StorageAnalysis.appsBytes`
- `ui/storage/StorageManagerViewModel.kt`: Compute residuals `systemBytes = total - used - other`, `otherBytes = used - apps - mediaStore - trash`
- `ui/storage/StorageManagerScreen.kt`: 9-segment storage bar, expandable list (Hiện ít hơn/Hiện thêm), AnimatedVisibility
- `data/db/TrashDao.kt`: Thêm `totalSize(): suspend`

**Patterns đã triển khai:**
- `suspendCoroutine` để bridge async IPackageDataObserver
- Residual computation: other = used - apps - mediaStore - trash; system = total - used - other
- AnimatedVisibility expand/collapse cho danh sách chi tiết
- 9-segment storage bar với 10 màu (Samsung My Files style)

**Files đã tạo:**
- `app/src/main/java/com/example/codevui/data/GoogleDriveItem.kt` — Data class cho Drive item
- `app/src/main/java/com/example/codevui/ui/googledrive/GoogleDriveUiState.kt` — UiState + GoogleDriveSortBy enum
- `app/src/main/java/com/example/codevui/ui/googledrive/GoogleDriveViewModel.kt` — ViewModel với sign-in, browse, CRUD
- `app/src/main/java/com/example/codevui/ui/googledrive/GoogleDriveScreen.kt` — Screen UI (giống BrowseScreen)
- `.claude/skills/auto-build/SKILL.md` — Skill auto-build

**Files đã sửa:**
- `app/src/main/java/com/example/codevui/data/GoogleDriveRepository.kt` — Viết lại hoàn toàn (fix type conflicts)
- `app/build.gradle.kts` — Thêm Google Drive API dependencies + packaging exclusion
- `app/src/main/java/com/example/codevui/ui/common/DrawerPane.kt` — Xóa duplicate Google Drive item
- `app/src/main/java/com/example/codevui/service/FileOperationService.kt` — Fix when exhaustiveness
- `CLAUDE.md` — Cập nhật package structure, Navigation

**Patterns đã triển khai:**
- GoogleSignIn OAuth flow với `ActivityResultContracts.StartActivityForResult`
- Google Drive API v3: list/copy/move/delete/rename/download/upload
- Drive folder navigation với breadcrumb
- Drive file icons theo MIME type
- `packaging { resources { excludes += ... } }` để fix JAR conflict

---

## 📋 Tasks Đang Làm

_(Không có task nào đang làm)_

---

## ✅ Tasks Đã Hoàn Thành

### Task #004 — Fix breadcrumb home icon navigation
**Ngày:** 2026-04-03
**Mô tả:** Khi giải nén file xong, click snackbar → mở BrowseScreen ở folder đích. Nhấn icon home trong breadcrumb → trước đây gọi `onBack` (pop screen stack). Sửa: thêm `onHomeClick` callback → `mainViewModel.navigateReplace(Screen.Home)` để về Home screen.

**Files đã sửa:**
- `ui/browse/BrowseScreen.kt`: Thêm param `onHomeClick: () -> Unit`, truyền vào `Breadcrumb`
- `MainActivity.kt`: Thêm `onHomeClick = { mainViewModel.navigateReplace(Screen.Home) }` khi gọi BrowseScreen

### Task #006 — Fix duplicate files navigation + create DuplicatesScreen
**Ngày:** 2026-04-03
**Mô tả:** Sửa 2 bug copy-paste trong StorageManagerScreen: (1) "File trùng lặp" card dùng `onUnusedAppsClick` thay vì `onNavigateToDuplicates`, (2) "File lớn" card cũng dùng `onUnusedAppsClick`. Tạo màn hình DuplicatesScreen hiển thị danh sách file trùng lặp với group header, badge "Gốc", selection mode.

**Files đã tạo:**
- `ui/duplicates/DuplicatesUiState.kt` — UiState data class
- `ui/duplicates/DuplicatesViewModel.kt` — ViewModel với SavedStateHandle + SelectionState + scanDuplicates()
- `ui/duplicates/DuplicatesScreen.kt` — Full UI: scan loading, empty state, group header, DuplicateFileItem với badge Gốc, selection mode

**Files đã sửa:**
- `MainViewModel.kt`: Thêm `Screen.Duplicates`
- `MainActivity.kt`: Thêm `is Screen.Duplicates` case, fix `onNavigateToDuplicates` → `Screen.Duplicates`
- `ui/storage/StorageManagerScreen.kt`: Thêm param `onNavigateToDuplicates` và `onNavigateToLargeFiles` vào `SuggestionsSection`, fix internal onClick references

### Task #005 — Archive selection cascade (folder ↔ children) — Tri-state fix
**Ngày:** 2026-04-03
**Mô tả:** Khi chọn 1 folder bên trong ZIP → cascade chọn/bỏ chọn tất cả descendants. Khi chọn/bỏ chọn 1 file → cập nhật parent folder (tri-state). Folder hiển thị tri-state checkbox: ALL (tất cả con chọn), PARTIAL (một số con chọn), NONE (không con nào chọn).

**Files đã sửa:**
- `ui/selection/SelectionComponents.kt`: Thêm `TriState` enum + `triState` param cho `SelectionCheckbox` (NONE/PARTIAL/ALL với indeterminate box)
- `ui/components/ArchiveEntryItem.kt`: Thêm `folderTriState: TriState` param, folder dùng tri-state checkbox thay vì boolean
- `ui/archive/ArchiveViewModel.kt`: Thêm `getParentId()`, `getDirectChildrenIds()`, `getVisibleChildrenIds()`, `getFolderTriState()`
- `ui/archive/ArchiveScreen.kt`: Folder toggle → cascade chỉ visible children. File toggle → cập nhật parent folder tri-state. Folder hiển thị effective tri-state thay vì raw selection state.

---

### Task #013 — Triển khai Favorites (MyFiles style)
**Ngày:** 2026-04-04
**Mô tả:** Triển khai tính năng Yêu thích giống Samsung MyFiles — Room DB để lưu favorites, FavoritesScreen để hiển thị danh sách, DrawerPane và HomeScreen để truy cập.

**Files đã tạo:**
- `data/db/FavoriteEntity.kt` — Room Entity (fileId=SHA-256 hash, path, name, size, mimeType, isDirectory, dateModified, addedAt, sortOrder)
- `data/db/FavoriteDao.kt` — Room DAO (Flow, CRUD, updateSortOrder, deleteByPaths)
- `data/db/AppDatabase.kt` — Thêm v3 migration, favoriteDao()
- `data/FavoriteManager.kt` — Singleton: observeFavorites, addFavorite, removeFavorite, toggleFavorite, removeFavorites, reorderFavorites, validateFavorites, updateFileInfo
- `data/FavoriteAction.kt` — Utility: addToFavorites, removeFromFavorites, toggleFavorite cho RecentFile items
- `ui/favorites/FavoritesUiState.kt` — FavoritesUiState data class
- `ui/favorites/FavoritesViewModel.kt` — AndroidViewModel: observeFavorites (Flow), deleteFavorite, reorderFavorites
- `ui/favorites/FavoritesScreen.kt` — Full Compose UI: header selection mode, FavoriteListItem thumbnail (Coil), FavoritesBottomBar

**Files đã sửa:**
- `model/Model.kt`: Thêm `FavoriteItem` data class (có uri cho thumbnail)
- `MainViewModel.kt`: Thêm `Screen.Favorites`
- `MainActivity.kt`: Thêm FavoritesScreen routing + import
- `ui/common/DrawerPane.kt`: Thêm Yêu thích drawer item với Star icon
- `ui/home/HomeScreen.kt`: Thêm Star icon trong TopAppBar
- `CLAUDE.md`: Thêm favorites package, Screen.Favorites, favorites schema

**Patterns đã triển khai:**
- Room v3 migration: CREATE TABLE IF NOT EXISTS favorites
- SHA-256 hash cho fileId (MessageDigest + Base64.NO_WRAP)
- MediaStore content URI resolution cho thumbnails
- Flow observer từ FavoriteManager.observeFavorites() → auto-update UI
- Selection mode: mutableStateOf + isSelectionMode boolean


### Task #014 — Fix Favorites thumbnail không load
**Ngày:** 2026-04-06
**Mô tả:** Thumbnail không hiển thị trên FavoritesScreen. Root cause: (1) mimeType null khi thêm vào favorites → FileType.fromMimeType(null)=OTHER → không vào nhánh IMAGE, (2) AudioThumbnailFetcher dùng API không tồn tại `setDataSource(context, uri)`, (3) FavoriteThumbnail tạo ThumbnailData mà không check path.isNotEmpty(), (4) ApkThumbnailFetcher không resolve content:// Uri.

**Files đã sửa:**
- `data/FavoriteManager.kt`: Thêm resolveMimeType(path) fallback + dùng trong toFavoriteItem() + thêm log debug
- `data/FavoriteManager.kt`: resolveContentUri() thêm log + xử lý Uri.EMPTY
- `ui/selection/SelectionActionHandler.kt`: Dùng MimeTypeMap.getMimeTypeFromExtension() khi gọi addFavorite()
- `ui/thumbnail/AudioThumbnailFetcher.kt`: Viết lại hoàn toàn — xử lý file:// và content:// Uri, dùng ParcelFileDescriptor
- `ui/thumbnail/ApkThumbnailFetcher.kt`: Viết lại — xử lý content:// Uri, query MediaStore bằng tên file
- `ui/favorites/FavoritesScreen.kt`: FavoriteThumbnail — check path.isNotEmpty() trước khi tạo ThumbnailData, IMAGE dùng Uri trực tiếp, track imageLoaded state, thêm log

**Patterns đã triển khai:**
- ThumbnailData: check path.isNotEmpty() trước khi tạo
- Audio: ParcelFileDescriptor.open() → setDataSource(pfd.fd)
- APK: MediaStore query bằng tên file để resolve content:// → path
- Logging trong FavoriteManager: resolveContentUri, resolveMimeType, toFavoriteItem


### Task #015 — Sửa selection mode giữ mode khi uncheck hết
**Ngày:** 2026-04-06
**Mô tả:** Khi uncheck hết item trong selection mode → tự động thoát mode → bottom bar ẩn. Sửa: giữ selection mode khi uncheck, chỉ thoát khi user nhấn nút Thoát. Bottom bar ẩn khi selectedCount=0 nhưng mode vẫn bật.

**Files đã sửa:**
- `ui/selection/SelectionState.kt`: toggle() bỏ isSelectionMode=false, selectAll() bỏ isSelectionMode=false
- `ui/favorites/FavoritesScreen.kt`: toggle() bỏ isSelectionMode=false, selectAll() bỏ isSelectionMode=false
- `ui/trash/TrashScreen.kt`: toggle() bỏ isSelectionMode=false, selectAll() bỏ isSelectionMode=false
- `ui/common/SelectableScaffold.kt`: BottomBar visible = selection.isSelectionMode && selection.selectedCount > 0


### Task #012 — Tạo UnusedAppsScreen (Samsung My Files style)
**Ngày:** 2026-04-03
**Mô tả:** Tạo màn hình "Ứng dụng không dùng" hiển thị danh sách app không dùng trong 30 ngày. Dùng UsageStatsManager để detect last used time. UI giống Samsung My Files: header "N ứng dụng không dùng (X GB)", tab Tất cả/Cho phép lưu trữ, sort by size/name/last_used, selection mode, icon settings → mở App Info hệ thống.

**Files đã tạo:**
- `ui/unusedapps/UnusedAppsUiState.kt` — UnusedAppInfo, UnusedAppsTab, UnusedAppsSortBy, UnusedAppsUiState
- `ui/unusedapps/UnusedAppsViewModel.kt` — AndroidViewModel + SavedStateHandle + SelectionState, sort, tab filter
- `ui/unusedapps/UnusedAppsScreen.kt` — Full UI: permission check dialog, header, tab selector, sort row, app list with icon/name/size, settings gear → App Info intent

**Files đã sửa:**
- `data/FileRepository.kt`: Thêm `getUnusedApps()` — UsageStatsManager query 30 days, filter user apps, calculate size
- `MainViewModel.kt`: Thêm `Screen.UnusedApps`
- `MainActivity.kt`: Thêm import + `is Screen.UnusedApps` case, fix `onNavigateToUnusedApps` → `Screen.UnusedApps`
- `AndroidManifest.xml`: Thêm `PACKAGE_USAGE_STATS` permission
- `CLAUDE.md`: Thêm unusedapps package, Screen.UnusedApps, getUnusedApps API, PACKAGE_USAGE_STATS permission

### Task #011 — Fix SelectionCheckbox không hiện dấu tích toàn app
**Ngày:** 2026-04-03
**Mô tả:** Checkbox selection hiển thị empty circle (không có dấu tích) khi chọn item ở tất cả các màn hình. Nguyên nhân gốc: `SelectionCheckbox` render dựa trên `triState` param (default `TriState.NONE`) chứ không dùng `isSelected`. 8/13 call sites không truyền `triState` → luôn hiện empty circle.

**Fix gốc:** Đổi `triState: TriState = TriState.NONE` thành `triState: TriState? = null`. Khi `null`, tự suy từ `isSelected`: `true → ALL`, `false → NONE`. Tất cả call sites cũ tự động hoạt động đúng mà không cần sửa.

**Affected screens:** BrowseScreen (files + folders), FileListScreen, RecentFilesScreen, TrashScreen, ArchiveScreen (file entries), ColumnPanel (Miller Columns), SelectionTopBar ("Tất cả" checkbox)

**Files đã sửa:**
- `ui/selection/SelectionComponents.kt`: `SelectionCheckbox` — `triState` nullable + `effectiveState` fallback

### Task #010 — Fix duplicate detection không nhận file đổi tên
**Ngày:** 2026-04-03
**Mô tả:** Copy file rồi đổi tên thì không hiển thị là duplicate. Nguyên nhân: `computeQuickHash()` bao gồm `file.name` trong hash → tên khác = hash khác = không phải duplicate. Sửa: hash chỉ dựa trên nội dung file (size + first 4KB + last 4KB), dùng `RandomAccessFile` thay vì `FileInputStream`. Cũng sửa `getDuplicateFilesSizeEstimate()` trong StorageManager: group by size only thay vì (name, size).

**Files đã sửa:**
- `data/FileRepository.kt`:
  - `computeQuickHash()`: bỏ `file.name` khỏi MD5, dùng `RandomAccessFile` đọc first+last 4KB
  - `getDuplicateFilesSizeEstimate()`: group by size only (không dùng tên), filter image/video/audio

### Task #009 — Fix dung lượng sai trong StorageManager (duplicate/large/trash)
**Ngày:** 2026-04-03
**Mô tả:** Sửa 3 hàm tính dung lượng sai trong `FileRepository.getStorageAnalysis()`:
1. `getDuplicateFilesSizeEstimate()` — trước đây ước tính 50% Download folder → sửa: scan MediaStore thực sự, group by (name, size), tính tổng (count-1)*size
2. `getLargeFilesSizeEstimate()` — trước đây không exclude .Trash → sửa: thêm filter exclude `.Trash/`, giảm threshold từ 100MB xuống 25MB
3. `getTrashSizeEstimate()` — trước đây dùng Room `totalSize()` (sai cho folders vì File.length()=0 cho dir) → sửa: tính từ thư mục `.Trash/files` bằng recursive scan

Cũng fix `TrashManager.moveToTrash()`: size lưu vào Room dùng `calculateDirSize()` cho directories thay vì `trashFile.length()`.

**Files đã sửa:**
- `data/FileRepository.kt`: Rewrite `getDuplicateFilesSizeEstimate()`, `getLargeFilesSizeEstimate()`, `getTrashSizeEstimate()`, thêm `calculateDirSize()`, xóa `estimateDuplicatesInDir()`
- `data/TrashManager.kt`: Fix size calculation in `moveToTrash()` dùng `calculateDirSize()` cho folders, thêm helper `calculateDirSize()`

### Task #007 — Fix DuplicatesScreen build + selection + delete dialog
**Ngày:** 2026-04-03
**Mô tả:** Sửa 3 bug: (1) Build error do `Icons.AutoMirrored.Filled.SelectAll` không tồn tại → đổi thành `Icons.Default.SelectAll`, (2) SelectionCheckbox hiển thị empty circle dù đã chọn → thêm `triState` param, (3) Nút xóa không hiện dialog xác nhận → thêm `MoveToTrashDialog`.

**Files đã sửa:**
- `ui/duplicates/DuplicatesScreen.kt`: Fix import SelectAll, thêm TriState import + triState param cho SelectionCheckbox, thêm MoveToTrashDialog + showDeleteDialog state

### Task #008 — Tạo LargeFilesScreen (Samsung My Files style)
**Ngày:** 2026-04-03
**Mô tả:** Tạo màn hình File lớn hoàn chỉnh: danh sách file grouped by size (>1GB, >500MB, >100MB, >25MB), threshold settings (25MB/100MB/500MB/Custom), type filter (All/Image/Video/Other), selection mode + delete with MoveToTrashDialog.

**Files đã tạo:**
- `ui/largefiles/LargeFilesUiState.kt` — SizeThreshold enum, LargeFileTypeFilter enum, SizeGroup data class, LargeFilesUiState
- `ui/largefiles/LargeFilesViewModel.kt` — AndroidViewModel + SavedStateHandle + SelectionState, groupBySize(), applyTypeFilter()
- `ui/largefiles/LargeFilesScreen.kt` — Full UI: LargeFilesScreen (TopAppBar + TabRow + grouped LazyColumn + bottom delete bar), SizeThresholdScreen (radio buttons), CustomSizeDialog, LargeFileItem

**Files đã sửa:**
- `data/FileRepository.kt`: Thêm `findLargeFiles(minBytes)` — MediaStore query SIZE > minBytes, sorted DESC
- `MainViewModel.kt`: Thêm `Screen.LargeFiles`
- `MainActivity.kt`: Thêm import + `is Screen.LargeFiles` case, fix `onNavigateToLargeFiles` → `Screen.LargeFiles`
- `CLAUDE.md`: Thêm largefiles package, Screen.LargeFiles, findLargeFiles API

---

### Task #002 — Màn hình Quản lý lưu trữ (Storage Manager)
**Ngày:** 2026-04-02
**Mô tả:** Tạo màn hình Quản lý lưu trữ hoàn chỉnh giống Samsung My Files — hiển thị chi tiết bộ nhớ, storage bar phân đoạn theo loại file, carousel quick actions (File trùng lặp / File lớn), phần Đề xuất, và danh sách các category (Thùng rác, Ứng dụng không dùng).

**Files đã tạo:**
- `app/src/main/java/com/example/codevui/ui/storage/StorageManagerScreen.kt` — Full UI với InternalStorageCard, StorageBar segmented, QuickActionsCarousel, SuggestionsSection, StorageCategoriesSection
- `app/src/main/java/com/example/codevui/ui/storage/StorageManagerViewModel.kt` — ViewModel gọi getStorageAnalysis()
- `app/src/main/java/com/example/codevui/ui/storage/StorageManagerUiState.kt` — UiState data class

**Files đã sửa:**
- `app/src/main/java/com/example/codevui/data/FileRepository.kt` — Thêm `getStorageAnalysis(): suspend` (StorageAnalysis data class bên dưới) + `suspend fun totalSize(): Long?` trong TrashDao
- `app/src/main/java/com/example/codevui/data/db/TrashDao.kt` — Thêm `totalSize()` query
- `app/src/main/java/com/example/codevui/MainViewModel.kt` — Thêm `Screen.StorageManager`
- `app/src/main/java/com/example/codevui/MainActivity.kt` — Thêm import StorageManagerScreen + StorageManager case trong when
- `app/src/main/java/com/example/codevui/ui/common/DrawerPane.kt` — Quản lý lưu trữ + Thùng rác có onClick navigation
- `app/src/main/java/com/example/codevui/model/Model.kt` — Thêm `data class StorageAnalysis`

**Patterns đã triển khai:**
- Segmented storage bar (Samsung My Files style) — phân đoạn theo Video/Picture/Audio/Doc/Archive/APK
- Carousel quick actions — File trùng lặp + File lớn với dots indicator
- Suggestions expandable section
- Navigation callback pattern cho mỗi action
