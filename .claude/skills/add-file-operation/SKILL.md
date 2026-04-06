---
name: add-file-operation
description: Thêm thao tác file mới vào CodeVui (copy/move/compress/rename/share). Dùng khi cần thêm file operation mới. LUÔN dùng FileOperationService (ForegroundService) cho operations lâu và MediaStoreScanner sau mỗi operation.
---

# Thêm Thao Tác File Mới — CodeVui

## Pattern Overview

```
Screen → ViewModel → FileOperationService (ForegroundService)
                         ↓
                   FileOperations.kt (Flow engine)
                         ↓
                   ProgressState emit → ViewModel collect
                         ↓
                   MediaStoreScanner.scan*() → notify other apps
```

## Step-by-Step

### Step 1 — Thêm Operation Method vào FileOperations.kt

**File:** `app/src/main/java/com/example/codevui/data/FileOperations.kt`

```kotlin
// Trong object FileOperations

fun renameMultiple(
    renamePairs: List<Pair<String, String>>  // (oldPath, newName)
): Flow<ProgressState> = channelFlow {
    send(ProgressState.Counting)

    if (renamePairs.isEmpty()) {
        send(ProgressState.Done(success = 0, failed = 0))
        return@channelFlow
    }

    var done = 0
    var failed = 0
    val mutex = Mutex()

    renamePairs.mapIndexed { index, (oldPath, newName) ->
        async(Dispatchers.IO) {
            try {
                val oldFile = File(oldPath)
                val dest = File(oldFile.parentFile, newName)

                send(ProgressState.Running(
                    currentFile = oldFile.name,
                    done = index,
                    total = renamePairs.size,
                    percent = (index * 100 / renamePairs.size).coerceIn(0, 100)
                ))

                val success = oldFile.renameTo(dest)
                mutex.withLock { if (success) done++ else failed++ }
            } catch (e: Exception) {
                mutex.withLock { failed++ }
            }
        }
    }.awaitAll()

    send(ProgressState.Done(success = done, failed = failed))
}
```

### Step 2 — Thêm Launcher vào FileOperationService

**File:** `app/src/main/java/com/example/codevui/service/FileOperationService.kt`

```kotlin
companion object {
    // ... existing ...

    fun startRenameMultiple(context: Context, renamePairs: List<Pair<String, String>>) {
        val intent = Intent(context, FileOperationService::class.java).apply {
            action = "RENAME_MULTIPLE"
            putExtra("pairs", ArrayList().apply {
                renamePairs.forEach { add("${it.first}|${it.second}") }
            })
        }
        context.startForegroundService(intent)
    }
}
```

### Step 3 — Xử lý Intent trong onStartCommand

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        // ... existing ...
        "RENAME_MULTIPLE" -> {
            val pairsRaw = intent.getStringArrayListExtra("pairs")
            val pairs = pairsRaw?.mapNotNull { str ->
                str.split("|").takeIf { it.size == 2 }
                    ?.let { it[0] to it[1] }
            } ?: emptyList()

            destinationPath = pairs.firstOrNull()?.first?.let { File(it).parent }

            _operationTitle.value = "Đang đổi tên..."
            currentActionName = "Đổi tên"

            startForeground(NOTIFICATION_ID, buildNotification("Đang đổi tên...", 0, 0, 0))

            operationJob?.cancel()
            operationJob = serviceScope.launch {
                FileOperations.renameMultiple(pairs).collect { state ->
                    _operationState.value = state
                    when (state) {
                        is ProgressState.Done -> {
                            notificationManager.cancel(NOTIFICATION_ID)
                            // ← LUÔN scan sau operation
                            MediaStoreScanner.scanPaths(
                                applicationContext,
                                pairs.mapNotNull { it.first }.distinct()
                            )
                            showResultNotification(buildDoneMessage(currentActionName, state))
                            stopSelf()
                        }
                        is ProgressState.Error -> {
                            notificationManager.cancel(NOTIFICATION_ID)
                            pairs.firstOrNull()?.first?.let {
                                MediaStoreScanner.scanDirectory(applicationContext, File(it).parent ?: "")
                            }
                            stopSelf()
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    return START_NOT_STICKY
}
```

### Step 4 — Thêm Method vào BaseFileOperationViewModel

**File:** `app/src/main/java/com/example/codevui/ui/common/BaseFileOperationViewModel.kt`

```kotlin
fun renameMultipleFiles(renamePairs: List<Pair<String, String>>) {
    _isDialogHidden.value = false
    lastOperationDestPath = renamePairs.firstOrNull()?.first?.let { File(it).parent }
    FileOperationService.startRenameMultiple(getApplication(), renamePairs)
    bindToService()
}
```

### Step 5 — Thêm Action vào SelectionActionHandler

**File:** `app/src/main/java/com/example/codevui/ui/selection/SelectionActionHandler.kt`

Thêm vào `SelectionActions` data class:
```kotlin
data class SelectionActions(
    // ... existing ...
    val onRenameMultiple: (() -> Unit)? = null  // nullable
)
```

## Quick Operation (Đồng Bộ, < 100ms)

Nếu operation NHANH và không cần notification:

```kotlin
fun quickOperation(paths: List<String>) {
    viewModelScope.launch(Dispatchers.IO) {
        val results = paths.map { path ->
            try {
                File(path).doSomething()
                true
            } catch (e: Exception) {
                log.e("quickOperation failed: $path", e)
                false
            }
        }
        val success = results.count { it }
        val failed = results.size - success

        if (success > 0) {
            // ← LUÔN scan sau operation
            MediaStoreScanner.scanPaths(getApplication(), paths)
        }

        resultManager.setResult(
            destPath = paths.firstOrNull()?.let { File(it).parent ?: "" },
            success = success,
            failed = failed,
            actionName = "Thao tác"
        )
        reload()
    }
}
```

## Conventions Quan Trọng

| Convention | Lý do |
|---|---|
| Dùng `channelFlow` | Thread-safe, nhiều coroutines emit |
| Dùng `async/awaitAll` | Parallel execution |
| `isActive` check trong loop | Support cancellation |
| ForegroundService cho ops > 1s | Android kill background nếu không |
| LUÔN gọi MediaStoreScanner sau operation | App khác nhận biết thay đổi |
| Buffer 64KB cho file I/O | Performance tốt |

## Checklist

- [ ] Thêm method vào `FileOperations.kt` (Flow-based)
- [ ] Thêm `startXxx()` static vào `FileOperationService`
- [ ] Xử lý intent trong `onStartCommand()`
- [ ] Thêm method vào `BaseFileOperationViewModel`
- [ ] Thêm action vào `SelectionActions`
- [ ] Thêm MediaStoreScanner sau operation
- [ ] Cập nhật `CLAUDE.md` §2, §4, §9
