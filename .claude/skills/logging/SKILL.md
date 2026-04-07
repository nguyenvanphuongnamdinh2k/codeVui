---
name: logging
description: Logging convention cho CodeVui. Dùng khi thêm code mới. LUÔN dùng Logger utility từ util/Logger.kt — KHÔNG dùng android.util.Log trực tiếp. TRUYỀN exception khi log error.
---

# Logging Convention — CodeVui

## Logger Utility

App dùng `util/Logger.kt` — wrapper cho Android `Log` với caller info tự động.

```kotlin
private val log = Logger("YourTag")  // khai báo ở top-level file

log.d("message")   // Log.d("YourTag", "[Class.method:line] message")
log.e("message")   // Log.e(...) — in red
log.w("message")   // Log.w(...)
log.i("message")   // Log.i(...)
```

## Khi Nào Log

### ViewModel — Log Entry Points

```kotlin
class MyViewModel(app: Application) : BaseMediaStoreViewModel(app) {
    private val log = Logger("MyVM")

    fun myMethod(param: String) {
        log.d("myMethod: param='$param'")
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.action(param)
            log.d("myMethod: done, result=$result")
            _uiState.update { it.copy(data = result) }
        }
    }

    override fun load() {
        log.d("load: starting")
        // ...
        log.d("load: loaded ${items.size} items")
    }

    override fun onOperationDone(success: Int, failed: Int, actionName: String) {
        log.d("onOperationDone: action=$actionName, success=$success, failed=$failed")
    }
}
```

### Repository / Data Layer

```kotlin
class MyRepository(private val context: Context) {
    private val log = Logger("MyRepo")

    fun getData(): List<Item> {
        log.d("getData: querying MediaStore")
        val items = queryMediaStore()
        log.d("getData: got ${items.size} items")
        return items
    }
}
```

### Error Logging — LUÔN có exception

```kotlin
try {
    riskyOperation()
} catch (e: Exception) {
    log.e("riskyOperation failed: $path", e)  // ← TRUYỀN exception!
}
// hoặc
} catch (e: Exception) {
    log.e("riskyOperation failed: path=$path, error=${e.message}")
}
```

## Log Levels

| Level | Khi nào | Applies to |
|:---:|---|---|
| `d` DEBUG | Entry/exit methods, state changes, operation flow | Development builds |
| `i` INFO | Operation started, completed | Development builds |
| `w` WARN | Unexpected state (file not found, permission denied) | All builds |
| `e` ERROR | Exception, operation failed | All builds |

## FavoriteManager Logging

```kotlin
// toFavoriteItem — debug mimeType và Uri resolution
log.d("toFavoriteItem: path=$path, storedMime=$storedMime, resolvedMime=$mime, uri=$uri")

// resolveContentUri — debug MediaStore lookup
log.d("resolveContentUri: path=$path → $uri")
log.w("resolveContentUri: no MediaStore entry for path=$path")

// resolveMimeType — debug extension → MIME
log.d("resolveMimeType: path=$path, ext=$ext, mime=$mime")
```

## FavoriteThumbnail Logging

```kotlin
// Composable top-level — debug props
log.d("FavoriteThumbnail: name=$name, mimeType=$mimeType, uri=$uri, isDir=$isDir, fileType=$fileType")
log.d("FavoriteThumbnail: thumbnailData=$thumbnailData")
log.d("FavoriteThumbnail SUCCESS: name=$name")
log.w("FavoriteThumbnail ERROR: name=$name, fileType=$fileType, thumbnailData=$thumbnailData")
log.w("FavoriteThumbnail: no thumbnail branch — fileType=$fileType, uri=$uri")
```

## What NOT to Log

```
❌ KHÔNG log: passwords, tokens, file content, sensitive paths
❌ KHÔNG log: user input lớn (long strings)
❌ KHÔNG log: mỗi iteration trong vòng lặp lớn (spam)
❌ KHÔNG log: dòng thường (nhiều lần mỗi second)
```

## Quick Template

```kotlin
package com.example.codevui.ui.xxx

import com.example.codevui.util.Logger

private val log = Logger("XxxVM")  // ← thêm dòng này

class XxxViewModel(...) : BaseMediaStoreViewModel(...) {

    fun doSomething(param: String) {
        log.d("doSomething: param='$param'")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.action(param)
                log.d("doSomething: success, result=$result")
                _uiState.update { it.copy(data = result) }
            } catch (e: Exception) {
                log.e("doSomething failed: param=$param", e)
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    override fun onOperationDone(success: Int, failed: Int, actionName: String) {
        log.d("onOperationDone: action=$actionName, success=$success, failed=$failed")
    }
}
```

## Checklist

- [ ] Thêm `private val log = Logger("Tag")` ở top-level file
- [ ] Log entry point: `log.d("methodName: params")`
- [ ] Log exit / return: `log.d("methodName: done, result=...")`
- [ ] Log errors: `log.e("methodName failed: ...", e)` ← TRUYỀN exception
- [ ] Log state changes quan trọng (selection mode, navigation, operation)
- [ ] KHÔNG log sensitive data
