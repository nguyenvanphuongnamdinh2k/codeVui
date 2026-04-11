# Rule — Kotlin Style

## Naming

- **Class:** `PascalCase` — `FileRepository`, `BrowseViewModel`
- **Function:** `camelCase` — `loadFiles()`, `moveToTrash()`
- **Constant:** `UPPER_SNAKE_CASE` — `MAX_TRASH_DAYS`, `NOTIFICATION_ID`
- **Composable:** `PascalCase` — `BrowseScreen`, `SelectionTopBar`
- **Private members:** no prefix — `private val fileRepo` (không dùng `_` hay `m`)
- **Backing field state:** `_state` + `val state: StateFlow<...>` — pattern StateFlow exposure

## Nullability

- **KHÔNG dùng `!!`** trong code mới. Thay bằng:
  - `?.let { ... }` cho side effect
  - `requireNotNull(x) { "msg" }` khi assert
  - `x ?: return` cho early return
  - `x ?: error("msg")` cho fatal
- **Function param nullable** chỉ khi thực sự cần — ưu tiên default value

## Coroutines

- **ViewModelScope** — `viewModelScope.launch { }` cho background work trong VM
- **Dispatcher rule:**
  - File I/O, MediaStore, DB → `Dispatchers.IO`
  - CPU heavy (hash, parse) → `Dispatchers.Default`
  - UI update → `Dispatchers.Main` (default cho viewModelScope)
- **Flow:** dùng `flowOn(Dispatchers.IO)` trong upstream chain, KHÔNG dùng `withContext` giữa chain
- **Cancel-safe:** check `isActive` trong loop dài

## Collections

- `buildList { }` cho build dần
- `listOf(...)` cho literal cố định
- `mutableListOf<T>()` chỉ khi cần mutate — ưu tiên `toMutableList()`
- Không dùng `!!` trên `List<T?>` — dùng `filterNotNull()`

## Data Class

- Tất cả model trong `model/Model.kt` là `data class`
- Nếu cần `copy()` với nhiều field optional → dùng default value
- Equals/hashCode tự sinh — không override trừ khi cần

## Scope Functions

- `let` — null check + transform
- `apply` — config builder (trả về this)
- `run` — compute value from receiver (trả về lambda result)
- `with` — nhóm gọi trên cùng receiver
- `also` — side effect (log, debug)

**Rule:** không lạm dụng. Code rõ ràng > cleverness.

## Imports

- KHÔNG dùng `import *` trừ package theme
- Sắp xếp theo Android Studio default
- Tách import theo group: kotlin / java / android / androidx / third-party / local

## Comments

- **Tiếng Việt** cho business logic comment
- **Tiếng Anh** cho TODO/FIXME/KDoc
- Không comment obvious code
- KDoc chỉ khi public API phức tạp

## Error Handling

- `try/catch` chỉ ở boundary (Repository, Service)
- Domain error → `sealed class Result`
- Không swallow exception — log qua `Logger`
- Không throw `Exception` generic — dùng custom hoặc đã có
