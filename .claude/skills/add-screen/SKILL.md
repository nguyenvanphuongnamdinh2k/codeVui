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

## Step-by-Step

### Step 1 — Thêm Screen Entry

**File:** `app/src/main/java/com/example/codevui/MainViewModel.kt`

```kotlin
sealed class Screen {
    // ... existing ...
    object MyNewScreen : Screen()
    // HOẶC với params:
    data class MyScreenWithParams(val id: String, val title: String) : Screen()
}
```

### Step 2 — Thêm Navigation Methods (nếu cần)

```kotlin
// Trong MainViewModel class
fun navigateToMyScreen() {
    screenStack.add(Screen.MyNewScreen())
}
```

### Step 3 — Tạo UiState Data Class

**File:** `app/src/main/java/com/example/codevui/ui/myscreen/MyScreenUiState.kt`

```kotlin
data class MyScreenUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### Step 4 — Tạo ViewModel

**File:** `app/src/main/java/com/example/codevui/ui/myscreen/MyScreenViewModel.kt`

**Template cơ bản (dùng MediaStore):**
```kotlin
class MyScreenViewModel(
    application: Application
) : BaseMediaStoreViewModel(application) {

    private val repository = FileRepository(application)
    private val _uiState = MutableStateFlow(MyScreenUiState())
    val uiState: StateFlow<MyScreenUiState> = _uiState.asStateFlow()

    init { load() }

    override fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val items = repository.getSomething()
            _uiState.update {
                it.copy(items = items, isLoading = false, error = null)
            }
        }
    }
}
```

**Template cho Archive (KHÔNG dùng BaseMediaStoreViewModel):**
```kotlin
class MyArchiveViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    // archive không dùng MediaStore
}
```

### Step 5 — Tạo Screen Composable

**File:** `app/src/main/java/com/example/codevui/ui/myscreen/MyScreen.kt`

```kotlin
@Composable
fun MyScreen(
    viewModel: MyScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tiêu đề") }) }
    ) { padding ->
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

### Step 6 — Thêm Rendering Trong MainActivity

**File:** `app/src/main/java/com/example/codevui/MainActivity.kt`

```kotlin
// Trong MyFilesApp composable, thêm case vào when
when (val screen = mainViewModel.currentScreen) {
    // ... existing ...
    is Screen.MyNewScreen -> MyScreen()
    is Screen.MyScreenWithParams -> MyScreen(id = screen.id, title = screen.title)
}
```

## Conventions Quan Trọng

| Convention | Lý do |
|---|---|
| ViewModel extend BaseMediaStoreViewModel | Auto-reload khi MediaStore thay đổi |
| ArchiveViewModel KHÔNG extend BaseMediaStoreViewModel | Vì archive không dùng MediaStore |
| Screen Composable dùng `viewModel()` default param | Unit testable, có thể inject mock |
| UiState là immutable data class | Predictable, dễ test |
| Dùng `collectAsStateWithLifecycle()` | Respects Compose lifecycle |
| `load()` được gọi trong `init {}` | ViewModel được tạo → load ngay |

## Checklist

- [ ] Thêm `Screen` entry vào `MainViewModel.kt`
- [ ] Thêm navigation helper method (nếu cần)
- [ ] Tạo `*UiState.kt` data class
- [ ] Tạo `*ViewModel.kt` (extend BaseMediaStoreViewModel TRỪ archive)
- [ ] Tạo `*Screen.kt` Composable
- [ ] Thêm `when` case trong `MainActivity.kt` → `MyFilesApp`
- [ ] Test navigation: Home → New Screen → Back → đúng state
- [ ] Cập nhật `CLAUDE.md` §2 Package Structure + §8 Navigation
- [ ] **Build sau khi hoàn thành** (`./gradlew assembleDebug --no-daemon`)

## Google Drive Screen (Special Case)

GoogleDriveScreen KHÔNG extend BaseMediaStoreViewModel vì không dùng MediaStore.

Thay vào đó dùng `AndroidViewModel` + `SelectionState(savedStateHandle)`:
```kotlin
class GoogleDriveViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    val selection = SelectionState(savedStateHandle)
}
```

Data class cho Drive items nên đặt trong `data/` package (KHÔNG trong `ui/googledrive/`) để tránh circular dependency với Repository.
