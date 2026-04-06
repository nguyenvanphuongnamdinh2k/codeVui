---
name: add-dialog
description: Thêm dialog mới vào CodeVui. Dùng khi cần tạo dialog như xác nhận xóa, đổi tên, tạo folder, nén file, v.v. LUÔN thông qua DialogManager — không viết dialog độc lập trong screen.
---

# Thêm Dialog Mới — CodeVui

## Pattern Overview

App dùng **DialogManager** làm central state machine cho tất cả dialogs.
KHÔNG viết dialog độc lập bên trong screen — luôn thông qua DialogManager.

```
Screen: dialogManager.showXxx(...) → UI
DialogHandler(dialogManager)       → Auto-render đúng dialog
```

## Step-by-Step

### Step 1 — Viết Composable Dialog

**Đặt file:** `app/src/main/java/com/example/codevui/ui/common/dialogs/TênDialog.kt`

```kotlin
package com.example.codevui.ui.common.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun TênDialog(
    // params: data cần hiển thị
    param1: String,
    param2: Int,

    onDismiss: () -> Unit,
    onConfirm: (result: String) -> Unit   // hoặc () -> Unit
) {
    var textValue by remember { mutableStateOf(param1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tiêu đề dialog") },
        text = {
            Column {
                Text("Mô tả ngắn")
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (textValue.isNotBlank()) onConfirm(textValue)
            }) { Text("Xác nhận") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
```

**Quy tắc:**
- Dùng `AlertDialog` (Material 3)
- Params luôn có `onDismiss` và `onConfirm`
- Auto-dismiss sau `onConfirm` — DialogManager.confirm() làm điều đó
- Confirm button chỉ active khi input hợp lệ

### Step 2 — Thêm DialogType

**File:** `ui/common/dialogs/DialogManager.kt`

```kotlin
enum class DialogType {
    RENAME,
    DETAILS,
    // ... existing ...
    YOUR_NEW_TYPE   // ← thêm vào đây
}
```

### Step 3 — Thêm Data Class (nếu cần nhiều params)

```kotlin
// Trong class DialogManager
data class YourDataClass(
    val param1: String,
    val param2: Int
)
```

### Step 4 — Thêm Method vào DialogManager

```kotlin
// Trong class DialogManager

fun showTênMới(
    param1: String,
    param2: Int,
    onConfirm: (result: String) -> Unit
) {
    dialogType = DialogType.YOUR_NEW_TYPE
    dialogData = YourDataClass(param1, param2)
    dialogCallback = { result -> onConfirm(result as String) }
}
```

### Step 5 — Thêm Case vào DialogHandler

```kotlin
DialogType.YOUR_NEW_TYPE -> {
    val data = manager.getData<DialogManager.YourDataClass>() ?: return
    TênDialog(
        param1 = data.param1,
        param2 = data.param2,
        onDismiss = { manager.dismiss() },
        onConfirm = { result -> manager.confirm(result) }
    )
}
```

### Step 6 — Gọi từ Screen/Composable

```kotlin
val dialogManager = rememberDialogManager()

// Ở đâu đó trong Composable (button click, etc.)
dialogManager.showTênMới(param1 = "hello", param2 = 42) { result ->
    handleResult(result)
}

// Ở cuối Composable
DialogHandler(dialogManager)
```

## Conventions Quan Trọng

| Convention | Lý do |
|---|---|
| Tất cả dialog qua DialogManager | UI screen gọn, dễ quản lý state |
| `onConfirm` không tự gọi `dismiss()` | DialogManager.confirm() làm điều đó |
| Đặt `DialogHandler` ở cuối Composable | Render sau cùng → dialog trên cùng |
| Dùng `rememberDialogManager()` | Composable-level state, tự cleanup |
| Confirm chỉ active khi input hợp lệ | Không submit empty/blank |

## Checklist

- [ ] Viết composable trong `ui/common/dialogs/`
- [ ] Thêm `DialogType` enum
- [ ] Thêm data class (nếu cần)
- [ ] Thêm `show*` method vào `DialogManager`
- [ ] Thêm `case` vào `DialogHandler`
- [ ] Test: mở → nhập → confirm → callback gọi đúng
- [ ] Test: back press / tap outside → dismiss
- [ ] Cập nhật `CLAUDE.md` §12 Dialog Reference
