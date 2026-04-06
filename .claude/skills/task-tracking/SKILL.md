---
name: task-tracking
description: Quản lý task và session memory. Dùng khi bắt đầu task mới, hoàn thành task, hoặc muốn xem lại những gì đã làm. LUÔN cập nhật TASKS.md sau mỗi thay đổi.
---

# Task Tracking — CodeVui

## Pattern Overview

Dùng `TASKS.md` để ghi lại memory giữa các sessions. Claude sessions không có persistent memory — TASKS.md là cách duy nhất để Claude sessions tương lai hiểu đã làm gì.

```
Session mới → Đọc TASKS.md → Hiểu context → Tiếp tục
Task hoàn thành → Cập nhật TASKS.md → Cập nhật CLAUDE.md → Commit
```

## Khi Bắt Đầu Session Mới

```
1. Đọc TASKS.md
2. Đọc CLAUDE.md
3. Xem có task đang làm dở không
4. Xem TODO list — ưu tiên task nào
5. Hỏi user muốn làm gì tiếp theo
```

## Khi Bắt Đầu Task Mới

### Step 1 — Thêm vào TODO (nếu chưa có)

```markdown
## 📌 TODO — Tasks Sắp Làm

### Priority: Cao
- [ ] **Task mô tả** — Chi tiết ngắn
```

Format:
```
- [ ] **<Mô tả ngắn>** — <Chi tiết>
```

### Step 2 — Chuyển sang "Đang Làm"

```markdown
## 📋 Tasks Đang Làm

### Task #00X — <Tên task>
**Mô tả:** <Chi tiết>
**Files liên quan:** <Danh sách files>
```

### Step 3 — Cập nhật TODO

```markdown
### Priority: Cao
- [ ] ~~**Task cũ đang làm**~~ → gạch ngang, thêm "→ Đang làm #00X"
```

## Khi Hoàn Thành Task

### Step 1 — Thêm vào "Tasks Đã Hoàn Thành"

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
- <Mô tả pattern>
```

### Step 2 — Cập nhật CLAUDE.md

Sau khi task hoàn thành, CẬP NHẬT CLAUDE.md theo `/updating-claude`.

### Step 3 — Cập nhật Skills liên quan

Nếu task thêm dialog mới:
- Cập nhật `/add-dialog` SKILL.md checklist

Nếu task thêm screen mới:
- Cập nhật `/add-screen` SKILL.md checklist

Nếu task thêm file operation:
- Cập nhật `/add-file-operation` SKILL.md checklist

### Step 4 — Xóa khỏi TODO

```markdown
- [ ] ~~**Task đã xong**~~ → xóa dòng này
```

### Step 5 — Thêm Update Log

```markdown
## 🔄 Update Log

| Ngày | Người cập nhật | Ghi chú |
|---|---|---|
| YYYY-MM-DD | Claude | Hoàn thành Task #00X: <mô tả> |
```

## Khi Task Thất Bại / Bị Hoãn

```markdown
### Task #00X — <Tên task>
**Ngày bắt đầu:** YYYY-MM-DD
**Trạng thái:** Tạm dừng
**Lý do:** <Tại sao>
**Đã làm:** <Những gì đã làm trước khi dừng>
**Cần làm tiếp:** <Những gì còn lại>
```

## Convention Quan Trọng

| Convention | Lý do |
|---|---|
| Task #001, #002, ... | Đánh số để dễ reference |
| Priority: Cao / Trung bình / Thấp | Ưu tiên làm trước |
| Mô tả ngắn trong TODO | Nhìn nhanh biết task gì |
| Chi tiết trong "Đang Làm" | Claude tiếp tục không phải hỏi lại |
| Files đã sửa | Biết đâu cần revert/review |
| Cập nhật CLAUDE.md sau mỗi task | Sessions tương lai hiểu codebase |
| Cập nhật Skills liên quan | Tránh thiếu thông tin |

## Checklist

- [ ] Đọc TASKS.md khi bắt đầu session mới
- [ ] Thêm task mới vào TODO trước khi làm
- [ ] Chuyển sang "Đang Làm" khi bắt đầu
- [ ] Hoàn thành → Thêm vào "Tasks Đã Hoàn Thành"
- [ ] Hoàn thành → Cập nhật CLAUDE.md
- [ ] Hoàn thành → Cập nhật Skills liên quan
- [ ] Hoàn thành → Cập nhật Update Log
- [ ] Hoàn thành → Xóa khỏi TODO
