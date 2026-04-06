---
name: updating-claude
description: Cập nhật documentation sau khi hoàn thành task. Dùng SAU KHI build thành công. LUÔN cập nhật TASKS.md → CLAUDE.md → Skills liên quan → Commit.
---

# Cập Nhật Documentation — CodeVui

## Workflow Hoàn Thành Task

```
Task done → Build OK
    → Cập nhật TASKS.md (Task #00X)
        → Cập nhật CLAUDE.md (sections liên quan)
            → Cập nhật Skills liên quan
                → Commit
```

## Step 1 — Cập Nhật TASKS.md

**Đọc:** `TASKS.md` trước.

### 1a. Thêm vào "Tasks Đã Hoàn Thành"

```markdown
## ✅ Tasks Đã Hoàn Thành

### Task #00X — <Tên task>
**Ngày:** YYYY-MM-DD
**Mô tả:** <Chi tiết>

**Files đã tạo:**
- `path/to/file.kt`

**Files đã sửa:**
- `path/to/file.kt`

**Patterns đã triển khai:**
- <Mô tả>
```

### 1b. Xóa khỏi TODO

```markdown
- [ ] ~~**Task đã xong**~~ → xóa dòng này
```

### 1c. Thêm Update Log

```markdown
## 🔄 Update Log

| Ngày | Người cập nhật | Ghi chú |
|---|---|---|
| YYYY-MM-DD | Claude | Hoàn thành Task #00X: <mô tả> |
```

## Khi Nào Cần Cập Nhật CLAUDE.md

| Thay đổi | Cập nhật section nào |
|---|---|
| Thêm dialog mới | §12 Dialog Reference |
| Thêm screen mới | §2 Package Structure, §8 Navigation |
| Thêm file operation | §2, §4, §9 File Ops E2E |
| Thêm data class | §3 Data Classes |
| Thêm method vào Repository | §4 Data Layer |
| Thêm UI component | §13 UI Components |
| Thêm navigation entry | §8 Navigation Structure |
| Thay đổi architecture | §7 ViewModel Hierarchy |
| Thêm thư viện mới | §1 Project Overview (Dependencies) |

## Section Checklist

### §1 Project Overview
```
[ ] Thêm dependency mới (nếu có)
```

### §2 Package Structure
```
[ ] Thêm file vào tree
    Format: "  relative/path.kt   # one-line purpose"
[ ] Package mới → mô tả package đó
```

### §3 Data Classes
```
[ ] Thêm data class mới
[ ] Mô tả params
```

### §4 Data Layer
```
[ ] Class mới → thêm mục với bảng API
[ ] Method mới → thêm vào bảng API của class đó
```

### §5 Database (Room)
```
[ ] Schema thay đổi → cập nhật bảng columns
[ ] Migration thêm → ghi chú
```

### §6 Service Layer
```
[ ] Intent action mới → thêm vào deep-links table
[ ] Method mới → mô tả
```

### §7 ViewModel Hierarchy
```
[ ] ViewModel mới → thêm vào tree
[ ] Class mới → ghi extends gì
```

### §8 Navigation Structure
```
[ ] Screen mới → thêm vào sealed class list
[ ] Deep link mới → thêm vào deep links table
```

### §9 File Operations E2E
```
[ ] Operation mới → thêm flow step-by-step
```

### §10 Dialog Reference
```
[ ] Dialog mới → thêm row vào bảng
    Format: "| TênDialog | Trigger | Key Behavior |"
```

## Quick Update Templates

### Thêm dialog mới
```
# §12 Dialog Reference
| MyNewDialog | Selection → More | Mô tả ngắn |
```

### Thêm screen mới
```
# §2 Package Structure
mynew/
  MyNewScreen.kt      # My new screen description
  MyNewViewModel.kt  # My new ViewModel

# §8 Navigation
data class MyNewScreen : Screen()  // thêm vào sealed class list
```

### Thêm method vào data class
```
# §4 Data Layer
| myNewMethod(param) | Mô tả ngắn |
```

## Update Rule

> **QUAN TRỌNG:** Sau khi hoàn thành task và build thành công,
> LUÔN cập nhật CLAUDE.md trước khi commit.

```
Task done → Build OK → Update CLAUDE.md → Commit
```

**KHÔNG BAO GIỜ** commit code mà không cập nhật CLAUDE.md.

## Step 2 — Cập Nhật CLAUDE.md

Theo section checklist bên dưới.

## Step 3 — Cập Nhật Skills Liên Quan

| Task thuộc loại | Skill cần cập nhật |
|---|---|
| Thêm dialog | `/add-dialog` |
| Thêm screen | `/add-screen` |
| Thêm file operation | `/add-file-operation` |
| Thêm/đổi convention | `/logging` |
| Thêm pattern mới | `/mediastore` |
| Thêm skill mới | Cập nhật `SKILLS.md` |

**Luôn cập nhật TASKS.md sau mỗi task.**

## Checklist

- [ ] Build thành công (`./gradlew assembleDebug`)
- [ ] Thêm task vào `TASKS.md` → "Tasks Đã Hoàn Thành"
- [ ] Xóa task khỏi TODO trong `TASKS.md`
- [ ] Thêm Update Log vào `TASKS.md`
- [ ] Cập nhật `CLAUDE.md` (sections liên quan)
- [ ] Cập nhật Skills liên quan (xem bảng trên)
- [ ] Kiểm tra CLAUDE.md không có typo
- [ ] Commit message mô tả rõ ràng
