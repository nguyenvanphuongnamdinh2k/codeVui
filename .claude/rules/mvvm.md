# Rule — MVVM Convention

## Layer Separation

```
UI (Composable)
  ↓ observe StateFlow
ViewModel
  ↓ call suspend fun
Repository / Manager
  ↓ use
MediaStore / Room / java.io.File / zip4j
```

**Rule:** Composable KHÔNG gọi thẳng Repository. Luôn qua ViewModel.

## ViewModel Hierarchy

```
AndroidViewModel
└── BaseFileOperationViewModel        # bind FileOperationService
    ├── BaseMediaStoreViewModel        # + auto reload khi MediaStore đổi
    │   ├── BrowseViewModel
    │   ├── FileListViewModel
    │   ├── RecentFilesViewModel
    │   ├── SearchViewModel
    │   ├── DuplicatesViewModel
    │   ├── LargeFilesViewModel
    │   ├── UnusedAppsViewModel
    │   ├── RecommendViewModel
    │   ├── StorageManagerViewModel
    │   └── FavoritesViewModel
    └── ArchiveViewModel               # extend BaseFileOperationViewModel (extract/move qua service)
```

**Rule:** Nếu screen cần MediaStore live update → extend `BaseMediaStoreViewModel`. Nếu không → `BaseFileOperationViewModel`.

## UiState Pattern

```kotlin
data class MyUiState(
    val isLoading: Boolean = false,
    val items: List<FileItem> = emptyList(),
    val error: String? = null,
)

class MyViewModel(app: Application) : BaseMediaStoreViewModel(app) {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()

    override fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val items = repo.fetch()
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
```

## Rotation Safety

- Long-running state → `SavedStateHandle`
- `SelectionState`, `ClipboardManager`, `FileActionState`, `TextEditorViewModel` đều đã pattern này

## Repository Rules

- **Không singleton via `object`** nếu cần Context → dùng class + inject Context
- `FileRepository` instantiate mới, `FileOperations` singleton (không cần Context)
- Repository không biết về UI layer — không import Compose/ViewModel

## Manager Rules

- Singleton `object` OK cho manager KHÔNG cần Context
- Manager cần Context → `fun setup(context)` + internal state
- Các Manager trong app:
  - `StorageVolumeManager` — quản lý volumes
  - `StorageUsageManager` — query size
  - `ClipboardManager` — in-app clipboard
  - `MediaStoreScanner` — scan files
  - `MediaStoreObserver` — observe changes
  - `TrashManager` — thùng rác
  - `FavoriteManager` — yêu thích
  - `ArchiveReader` — zip4j wrapper

## Flow Convention

- Repository trả về `Flow<T>` cho stream, `suspend fun` cho one-shot
- ViewModel convert Flow → StateFlow qua `stateIn` hoặc `_uiState.update`
- UI collect qua `collectAsStateWithLifecycle()`

## Error Convention

- Repository throw Exception với message rõ ràng
- ViewModel catch trong try/catch → set error state
- UI hiển thị error qua Snackbar (OperationResultManager)
- Không bao giờ crash UI thread vì exception
