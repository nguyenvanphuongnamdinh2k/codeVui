# Rule — Jetpack Compose Style

## Composable Structure

```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MyScreenContent(
        state = uiState,
        onAction = viewModel::handleAction,
        onBack = onBack,
    )
}

@Composable
private fun MyScreenContent(
    state: MyUiState,
    onAction: (MyAction) -> Unit,
    onBack: () -> Unit,
) { /* stateless */ }
```

**Rule:** tách stateful wrapper + stateless content để preview dễ.

## State Hoisting

- **KHÔNG** hold state trong composable leaf — hoist lên parent hoặc ViewModel
- `remember { mutableStateOf() }` chỉ cho ephemeral UI state (scroll, expand)
- ViewModel state luôn là StateFlow → `collectAsStateWithLifecycle()`

## Side Effects

| Effect | Khi dùng |
|---|---|
| `LaunchedEffect(key)` | Chạy coroutine khi key đổi |
| `LaunchedEffect(Unit)` | 1-shot on composition |
| `DisposableEffect(key)` | Cần cleanup khi leave composition |
| `SideEffect { }` | Publish state sang non-Compose API |
| `rememberUpdatedState` | Capture latest lambda trong LaunchedEffect lâu dài |

**Cấm:** không đặt side effect trong composable body trực tiếp.

## Modifier

- **Thứ tự:** size → padding → background → border → clickable → content
- Param `modifier: Modifier = Modifier` LUÔN là param đầu tiên sau state
- Không tạo `Modifier` bên trong composable — pass từ caller

## Preview

- Mọi composable stateless cần `@Preview`
- Dùng `@PreviewParameter` cho multiple variants
- Theme wrapper: `CodeVuiTheme { ... }`

## Recomposition

- Dùng `remember` cho derived state đắt
- `derivedStateOf` khi state phụ thuộc nhiều state khác
- `key = { it.id }` trong `LazyColumn.items()` — stable key mandatory
- Không tạo `remember { Something() }` mà không có key nếu cần reactive

## Material 3

- Import từ `androidx.compose.material3.*`
- KHÔNG trộn Material 2 (`androidx.compose.material.*`) vào Material 3
- Color: `MaterialTheme.colorScheme.*`
- Typography: `MaterialTheme.typography.*`
- Shape: `MaterialTheme.shapes.*`

## Performance

- `LazyColumn` / `LazyRow` cho list > 20 item
- `key` param mandatory
- `contentType` param cho list heterogeneous
- Image loading: Coil `AsyncImage` với `ImageLoader` từ `AppImageLoader`
- Tránh recompose toàn screen — dùng `Modifier.composed { }` cẩn thận
