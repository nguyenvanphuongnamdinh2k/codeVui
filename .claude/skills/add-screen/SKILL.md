---
name: add-screen
description: Thêm screen/ViewModel mới vào CodeVui. Dùng khi cần tạo màn hình mới như màn hình cài đặt, màn hình yêu thích, màn hình lịch sử, v.v. Bao gồm sealed class Screen entry, ViewModel, và Screen Composable.
---

# Thêm Screen Mới — CodeVui

## Pattern Overview

App dùng **Single Activity** + **screenStack** (MutableStateList<Screen>).

```
MainActivity.kt
  └── MainViewModel.screenStack
        └── MyFilesApp composable
              └── when(currentScreen) → render Screen
```

---

## Step 1 — Thêm Screen Entry

**File:** `app/src/main/java/com/example/codevui/MainViewModel.kt`

```kotlin
sealed class Screen {
    // ... existing ...
    object MyNewScreen : Screen()
    // HOẶC với params:
    data class MyScreenWithParams(val id: String, val title: String) : Screen()
}
```

## Step 2 — Tạo UiState Data Class

**File:** `app/src/main/java/com/example/codevui/ui/myscreen/MyScreenUiState.kt`

```kotlin
data class MyScreenUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

## Step 3 — Tạo ViewModel

**File:** `app/src/main/java/com/example/codevui/ui/myscreen/MyScreenViewModel.kt`

**Template cơ bản (dùng MediaStore):**
```kotlin
class MyScreenViewModel(application: Application) : BaseMediaStoreViewModel(application) {

    private val repository = FileRepository(application)
    private val _uiState = MutableStateFlow(MyScreenUiState())
    val uiState: StateFlow<MyScreenUiState> = _uiState.asStateFlow()

    init { load() }

    override fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val items = repository.getSomething()
            _uiState.update { it.copy(items = items, isLoading = false, error = null) }
        }
    }
}
```

**Template cho Archive / KHÔNG dùng BaseMediaStoreViewModel:**
```kotlin
class MyArchiveViewModel(application: Application, savedStateHandle: SavedStateHandle)
    : AndroidViewModel(application) { /* archive không dùng MediaStore */ }
```

## Step 4 — Tạo Screen Composable

**File:** `app/src/main/java/com/example/codevui/ui/myscreen/MyScreen.kt`

```kotlin
@Composable
fun MyScreen(viewModel: MyScreenViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Tiêu đề") }) }) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> Text(uiState.error!!)
                uiState.items.isEmpty() -> Text("Không có dữ liệu")
                else -> LazyColumn {
                    items(uiState.items) { item -> ItemRow(item) }
                }
            }
        }
    }
}
```

## Step 5 — Thêm Rendering Trong MainActivity

**File:** `app/src/main/java/com/example/codevui/MainActivity.kt`

```kotlin
// Trong MyFilesApp composable, thêm case vào when
when (val screen = mainViewModel.currentScreen) {
    is Screen.MyNewScreen -> MyScreen()
    is Screen.MyScreenWithParams -> MyScreen(id = screen.id, title = screen.title)
}
```

---

## UI Components — Selection, Navigation, List

### Selection Components

**SelectionTopBar** — Thay TopAppBar khi selection mode.
- "Tất cả" checkbox | "Đã chọn N" | Thoát

**SelectionBottomBar** — 4 nút: **Di chuyển | Sao chép | Chia sẻ | Xóa** + "N.hơn" dropdown.
- `visible = isSelectionMode && selectedCount > 0`
- Khi selectedCount=0 → bottom bar ẩn, mode vẫn bật

**Menu visibility (MyFiles pattern):**

| Menu | Điều kiện hiện |
|---|---|
| Nén | chỉ khi **không có** archive files |
| Giải nén | chỉ khi **có** archive files được chọn |
| Thêm vào yêu thích | chỉ khi **ít nhất 1 item chưa favorite** |
| Xóa khỏi yêu thích | chỉ khi **có item đã favorite** |

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

**SelectionCheckbox** — Circular checkbox (Samsung style). 28dp, blue fill when selected.

**Selection Mode Pattern:**
- `toggle(id)` → bỏ check, **giữ mode**
- `selectAll(ids)` → bỏ check all, **giữ mode**
- `exit()` → **CHỈ** gọi khi nhấn nút Thoát

### Navigation Components

**SortBar:**
- Left: "Cần thiết / Tất cả" filter dropdown (chỉ hiện ở root)
- Right: Sort type (Ngày/Tên/Kích thước) + direction (↑/↓)

**Breadcrumb:**
- Single: 🏠 > Tên | Size
- Multi: 🏠 > Bộ nhớ trong > DCIM > Camera
- Last segment: màu xanh, không clickable

### List Components

| Component | Mô tả |
|---|---|
| `FileListItem` | File row: thumbnail + name + size + date |
| `FolderListItem` | Folder row: name + item count |
| `ArchiveEntryItem` | Entry row inside ZIP |
| `HighlightedText` | Text với search term highlighted |

### Thumbnail System

Xem skill **`add-thumbnail-fetcher`** để thêm fetcher mới.

Architecture hiện tại:
- `ThumbnailFetcher` (abstract base) — chứa `fetch()` + abstract `extractBitmap()`
- `ThumbnailData` (sealed class) — Video/Audio/Apk/Archive data classes
- `ThumbnailManager` — singleton register factories

---

## Database — Room Schema (nếu screen cần local DB)

App hiện có 2 bảng: `trash_items` (v3) và `favorites` (v3).

### Schema: `trash_items`

| Column | Type | Notes |
|---|:---:|---|
| id | TEXT PK | = trashName |
| originalName | TEXT | |
| trashName | TEXT | `<timestamp>_<safeName>` |
| originalPath | TEXT | INDEX v2 |
| size | INTEGER | bytes |
| deleteTimeEpoch | INTEGER | seconds, INDEX v2 |
| isDirectory | INTEGER | 0/1 |
| mimeType | TEXT | |
| extension_ | TEXT? | nullable |

**Migration v1→v2**: Thêm indexes trên `deleteTimeEpoch` và `originalPath`.

### Schema: `favorites`

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

**Migration v2→v3**: Thêm bảng `favorites`.

### AppDatabase + Migrations

```kotlin
@Database(
    entities = [TrashEntity::class, FavoriteEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trashDao(): TrashDao
    abstract fun favoriteDao(): FavoriteDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trash_deleteTime ON trash_items(deleteTimeEpoch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_trash_originalPath ON trash_items(originalPath)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE favorites (
                fileId TEXT PRIMARY KEY, name TEXT NOT NULL, path TEXT NOT NULL UNIQUE,
                size INTEGER NOT NULL DEFAULT 0, mimeType TEXT,
                isDirectory INTEGER NOT NULL DEFAULT 0, dateModified INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL DEFAULT 0, sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_favorites_sortOrder ON favorites(sortOrder)")
    }
}
```

### DAO Quick Reference

```kotlin
// TrashDao
fun observeAll(): Flow<List<TrashEntity>>
suspend fun getByIds(ids: List<String>): List<TrashEntity>
suspend fun insert(item: TrashEntity)
suspend fun deleteByIds(ids: List<String>)
suspend fun deleteExpired(epoch: Long)

// FavoriteDao
fun observeAll(): Flow<List<FavoriteEntity>>
suspend fun getByPath(path: String): FavoriteEntity?
suspend fun getAllPaths(): List<String>
suspend fun insert(item: FavoriteEntity)
suspend fun deleteByPath(path: String)
suspend fun deleteByPaths(paths: List<String>)
suspend fun updateSortOrder(path: String, sortOrder: Int)
```

---

## Conventions Quan Trọng

| Convention | Lý do |
|---|---|
| ViewModel extend BaseMediaStoreViewModel | Auto-reload khi MediaStore thay đổi |
| ArchiveViewModel KHÔNG extend BaseMediaStoreViewModel | Vì archive không dùng MediaStore |
| Screen Composable dùng `viewModel()` default param | Unit testable, có thể inject mock |
| UiState là immutable data class | Predictable, dễ test |
| Dùng `collectAsStateWithLifecycle()` | Respects Compose lifecycle |
| `load()` được gọi trong `init {}` | ViewModel được tạo → load ngay |
| Dialog: LUÔN qua DialogManager, không viết độc lập | Xem skill `add-dialog` |

## Checklist

- [ ] Thêm `Screen` entry vào `MainViewModel.kt`
- [ ] Tạo `*UiState.kt` data class
- [ ] Tạo `*ViewModel.kt` (extend BaseMediaStoreViewModel TRỪ archive)
- [ ] Tạo `*Screen.kt` Composable
- [ ] Thêm `when` case trong `MainActivity.kt` → `MyFilesApp`
- [ ] Test navigation: Home → New Screen → Back → đúng state
- [ ] Cập nhật `CLAUDE.md` section Navigation
- [ ] **Build sau khi hoàn thành** (`./gradlew assembleDebug --no-daemon`)