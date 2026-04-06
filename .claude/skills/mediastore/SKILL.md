---
name: mediastore
description: Hướng dẫn tương tác với MediaStore và File I/O trong CodeVui. Dùng khi đọc file list, thao tác file, hoặc cần notify app khác. KHÔNG query MediaStore trực tiếp — luôn qua FileRepository.
---

# MediaStore & File I/O — CodeVui

## Quy Tắc Vàng

| Tình huống | Dùng gì | Tại sao |
|---|:---:|---|
| Đọc file list (gallery, download) | `FileRepository` | Query MediaStore ContentProvider |
| Copy/Move/Compress (long operation) | `FileOperationService` | ForegroundService + notification |
| Tạo folder/file nhanh (mkdir) | `java.io.File` + `MediaStoreScanner` | Không cần progress |
| Xóa (move to trash) | `TrashManager` | Copy → delete → Room metadata |
| Đọc file text (editor) | `FileInputStream` | Đọc trực tiếp |
| Ghi file text (editor) | `FileOutputStream` | Ghi trực tiếp |

## Đọc File List → FileRepository

```kotlin
private val repository = FileRepository(context)

// Recent files
val files = repository.getRecentFiles(limit = 20)

// Files by type
val images = repository.getFilesByType(FileType.IMAGE, SortBy.DATE, false)

// Directory listing (java.io.File based)
val (folders, files) = repository.listDirectory(
    "/storage/emulated/0/Download",
    SortBy.NAME,
    true
)

// Search
val results = repository.searchFiles(
    query = "photo",
    fileTypes = setOf(FileType.IMAGE, FileType.VIDEO),
    afterTimestamp = oneWeekAgo
)

// Cancellable folder search
val folders = repository.searchFoldersSuspend("DCIM")
```

## Thao Tác File → ForegroundService

```kotlin
// ViewModel
fun copyFiles(sourcePaths: List<String>, destDir: String) {
    copyFiles(sourcePaths, destDir, null)
}
```

**KHÔNG dùng `java.io.File.copyRecursively()` trong ViewModel.**
LUÔN qua `FileOperationService` để:
1. Chạy background (Android kill app nếu không foreground)
2. Hiện notification progress
3. Cancel được
4. Notify MediaStore

## Tạo File/Folder Nhanh → MediaStoreScanner

```kotlin
// ViewModel
fun createFolder(folderName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val newFolder = java.io.File(currentPath, folderName)
        val created = newFolder.mkdirs()

        if (created) {
            // ← LUÔN scan sau khi tạo
            MediaStoreScanner.scanNewFile(
                getApplication(),
                parentPath = currentPath,
                newFilePath = newFolder.absolutePath
            )
            reload()
        }
    }
}
```

**Tại sao scan?** `java.io.File` thao tác trực tiếp → MediaStore không biết →
app khác (gallery, download manager) không nhận ra file mới.

## MediaStoreScanner API

```kotlin
// Scan danh sách file/folder
MediaStoreScanner.scanPaths(context, listOf("/path/a", "/path/b"))

// Scan toàn bộ thư mục (đệ quy)
MediaStoreScanner.scanDirectory(context, "/storage/emulated/0/Download")

// Scan cả source + dest (sau MOVE)
MediaStoreScanner.scanSourceAndDest(context, sourcePath, destPath)

// Scan parent + new file (sau CREATE, COMPRESS)
MediaStoreScanner.scanNewFile(context, parentPath, newFilePath)
```

**Excluded:** `.Trash` directory — tự động bị loại trừ.

## TrashManager

```kotlin
private val trashManager = TrashManager(context)

viewModelScope.launch {
    // Move to trash
    val (success, failed) = trashManager.moveToTrash(listOf("/path/to/file"))

    // Restore
    val (success, failed, destDir) = trashManager.restore(listOf("trashId1"))

    // Permanently delete
    val (success, failed) = trashManager.permanentlyDelete(listOf("trashId1"))

    // Observe realtime
    trashManager.observeTrashItems().collect { items -> }
}
```

**Luồng tự động scan bên trong:**
```
moveToTrash: copy → delete → Room metadata → scan parent dirs
restore: copy → delete from .Trash → scan dest dirs
permanentlyDelete: delete from .Trash → scan .Trash
```

## MediaStoreObserver — Auto-Reload

```kotlin
// BaseMediaStoreViewModel tự động dùng
class MyViewModel(app: Application) : BaseMediaStoreViewModel(app) {
    override fun load() {
        // Được gọi:
        // 1. Khi ViewModel được tạo
        // 2. Khi operation của app hoàn thành
        // 3. Khi MediaStore thay đổi từ app bên ngoài
    }
}
```

## Conflict Resolution Pattern

```kotlin
private fun resolveConflict(file: File): File {
    if (!file.exists()) return file
    val parent = file.parentFile ?: return file
    val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
    var counter = 1
    var newFile: File
    do {
        newFile = File(parent, "${file.nameWithoutExtension} ($counter)$ext")
        counter++
    } while (newFile.exists())
    return newFile
}
```

## Checklist

- [ ] Đọc file list → dùng `FileRepository`
- [ ] Thao tác lâu (> 1s) → dùng `FileOperationService`
- [ ] Thao tác nhanh (mkdir, rename) → dùng `java.io.File`
- [ ] Sau thao tác → gọi `MediaStoreScanner.scan*()`
- [ ] KHÔNG dùng blocking I/O trên main thread
- [ ] KHÔNG query MediaStore trực tiếp — luôn qua `FileRepository`
