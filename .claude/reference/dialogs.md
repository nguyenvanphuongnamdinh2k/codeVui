# Dialog Reference

> Danh sách tất cả dialog trong CodeVui. Dùng khi cần thêm/sửa dialog.

## Dialog Manager

Tất cả dialog được quản lý qua `DialogManager.kt` — central sealed class state machine.

```kotlin
sealed class DialogState {
    object None
    data class Rename(val path: String, val name: String)
    data class DeleteConfirm(val paths: List<String>)
    data class MoveToTrash(val path: String)
    data class Details(val path: String)
    data class OpenWith(val path: String, val mimeType: String)
    data class Password(val onOk: (String) -> Unit)
    data class Extract(val archivePath: String, val entries: List<ArchiveEntry>, val destPath: String)
    data class Compress(val paths: List<String>)
    data class CreateFolder(val currentPath: String)
    data class CreateFile(val currentPath: String)
    data class PasteConflict(val name: String, val onResolve: (ConflictResolution) -> Unit)
}
```

Trigger: **luôn qua `DialogManager`** — không viết dialog độc lập trong screen.

---

## Dialog Details

### RenameDialog
- **Trigger:** Selection → Đổi tên (1 file)
- **Key behavior:** Auto-append extension cho file không có extension

### BatchRenameDialog
- **Trigger:** Selection → Đổi tên (N files)
- **Key behavior:** Danh sách items với TextField per item

### DeleteConfirmDialog
- **Trigger:** Selection → Xóa
- **Key behavior:** Gọi `TrashViewModel.moveToTrash`

### MoveToTrashDialog
- **Trigger:** Long-press file
- **Key behavior:** Warning: "File sẽ bị xóa sau 30 ngày"

### DetailsDialog
- **Trigger:** Selection → Chi tiết
- **Key behavior:** Name, size, path, modified, MIME, owner app

### PasswordDialog
- **Trigger:** Archive encrypted
- **Key behavior:** Visibility toggle, ok/cancel

### ExtractDialog
- **Trigger:** Archive → Giải nén
- **Key behavior:** Auto-resolve name conflict (rename)

### OpenWithDialog
- **Trigger:** Click file (không có default app)
- **Key behavior:** List apps từ PackageManager, "Chỉ một lần" / "Luôn luôn"; lưu vào `DefaultAppManager`

### CompressDialog
- **Trigger:** Selection → Nén
- **Key behavior:** Auto-resolve conflict, custom ZIP name input

### CreateFolderDialog
- **Trigger:** FAB → Folder
- **Key behavior:** Auto-generate "Thư mục N", validate empty/duplicate

### CreateFileDialog
- **Trigger:** FAB → File
- **Key behavior:** Auto-add .txt, validate empty/duplicate

### PasteConflictDialog
- **Trigger:** Paste conflict
- **Key behavior:** Thay thế / Đổi tên / Thoát

---

## Progress Dialog

### OperationProgressDialog (`ui/progress/OperationProgressDialog.kt`)

Samsung My Files style — dùng chung cho tất cả operations (copy/move/compress/extract).

- **Title:** "Đang sao chép...", "Đang di chuyển...", "Đang nén mục..."
- **Linear progress bar**
- **Counter:** "1/8" trái, "4%" phải
- **2 buttons:** "Thoát" (cancel) | "Ẩn cửa sổ pop-up" (dismiss but continue)