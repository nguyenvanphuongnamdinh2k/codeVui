# Rule — File Operations

## Các loại thao tác

| Operation | Engine | Scan sau |
|---|---|---|
| COPY | `FileOperationService` + `FileOperations.execute(COPY)` | `scanSourceAndDest(src, dest)` |
| MOVE | `FileOperationService` + `FileOperations.execute(MOVE)` | `scanSourceAndDest(src, dest)` |
| COMPRESS | `FileOperationService` + `FileOperations.compressToZip()` | `scanNewFile(parent, zipPath)` |
| DELETE → trash | `TrashManager.moveToTrash()` | `scanPaths(parentDirs)` |
| RESTORE | `TrashManager.restore()` | `scanPaths(restoredDirs)` |
| PERMANENT DELETE | `TrashManager.permanentlyDelete()` | `scanPaths([trashDir])` |
| CREATE FOLDER | `java.io.File.mkdirs()` direct | `scanNewFile(parent, null)` |
| CREATE FILE | `java.io.File.createNewFile()` direct | `scanNewFile(parent, newFile)` |
| RENAME | `java.io.File.renameTo()` direct | `scanPaths([old, new])` |
| ARCHIVE EXTRACT | `ArchiveReader.extractEntries()` | `scanNewFile(parent, null)` |
| ARCHIVE REMOVE | `ArchiveReader.removeEntries()` | N/A (in-place) |

## Rule Tuyệt Đối

1. **Mọi file operation PHẢI scan MediaStore sau khi xong** — trừ `.Trash` internal ops
2. **Long operation (> 1s) PHẢI qua FileOperationService** — không block UI
3. **Short op (< 100ms) có thể chạy direct trong ViewModel** (create folder, rename)
4. **Không dùng `DELETE` trực tiếp** — luôn qua Trash
5. **Không copy vào chính mình** — `FileOperations` đã check, nhưng ViewModel cũng nên double-check

## Conflict Resolution

```
Paste (COPY/MOVE) → detect conflict
  → PasteConflictDialog (Thay thế / Đổi tên / Thoát)
  → resolveConflicts() → auto-rename hoặc overwrite
```

Auto-rename pattern: `file.txt` → `file (1).txt` → `file (2).txt`

## Clipboard

- **In-app ClipboardManager** — NOT system clipboard
- 2 mode: COPY / MOVE
- Survive rotation (SavedStateHandle)
- Clear sau paste thành công
- Check `dest == source` cho MOVE → error snackbar

## Progress

- `FileOperationService.operationState: StateFlow<ProgressState?>`
- UI show `OperationProgressDialog` (Samsung My Files style)
- 2 nút: "Thoát" (cancel) / "Ẩn cửa sổ pop-up" (minimize)
- Notification progress bar realtime

## Background Safety

- Service là ForegroundService (required Android 8+)
- Channel: `file_operation_channel`, IMPORTANCE_LOW
- Cancel intent: user tap nút Thoát
- Auto `stopSelf()` khi Done/Error
- Result notification tồn tại 5s rồi auto-dismiss
