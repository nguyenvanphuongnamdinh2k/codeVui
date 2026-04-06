# TASKS — CodeVui Session Memory

> **Tự động cập nhật bởi Claude.** Ghi lại task đã làm, đang làm, và TODO.
> Đọc file này khi bắt đầu session mới để hiểu context.

---

## ✅ Tasks Đã Hoàn Thành

### Task #001 — MediaStore Scanner Integration
**Ngày:** 2026-03-31
**Mô tả:** Tích hợp MediaStoreScanner để notify các app khác khi file được tạo/xóa/move.

**Files đã tạo:**
- `data/MediaStoreScanner.kt`

**Files đã sửa:**
- `service/FileOperationService.kt`
- `data/TrashManager.kt`
- `ui/browse/BrowseViewModel.kt`
- `ui/archive/ArchiveViewModel.kt`

**Patterns triển khai:**
- Copy/Move → `scanSourceAndDest()`
- Compress → `scanNewFile()`
- Create folder/file → `scanNewFile()`
- Move to Trash → `scanPaths()` (parent dirs)
- Restore → `scanPaths()` (dest dirs)
- Permanently Delete → `scanDirectory()` (.Trash)
- Extract/Move from Archive → `scanNewFile()`

---

### Task #002 — CLAUDE.md Documentation
**Ngày:** 2026-03-31
**Mô tả:** Tạo CLAUDE.md tổng quan toàn bộ codebase, 16 sections.

**Files đã tạo:**
- `CLAUDE.md`

---

### Task #003 — Skills Documentation
**Ngày:** 2026-03-31
**Mô tả:** Tạo skills cho Claude Code theo chuẩn format `.claude/skills/<name>/SKILL.md`.

**Files đã tạo:**
- `SKILLS.md` — Quick reference
- `.claude/skills/task-tracking/SKILL.md`
- `.claude/skills/add-dialog/SKILL.md`
- `.claude/skills/add-screen/SKILL.md`
- `.claude/skills/add-file-operation/SKILL.md`
- `.claude/skills/logging/SKILL.md`
- `.claude/skills/updating-claude/SKILL.md`
- `.claude/skills/mediastore/SKILL.md`

**Files đã xóa:**
- `SKILL_ADD_DIALOG.md` (standalone)
- `SKILL_ADD_SCREEN.md`
- `SKILL_ADD_FILE_OPERATION.md`
- `SKILL_LOGGING.md`
- `SKILL_UPDATING_CLAUDE.md`
- `SKILL_MEDIASTORE.md`

---

### Task #004 — Task Tracking System
**Ngày:** 2026-03-31
**Mô tả:** Tạo TASKS.md + task-tracking skill để quản lý task và session memory.

**Files đã tạo:**
- `TASKS.md` — Session memory file
- `.claude/skills/task-tracking/SKILL.md` — Skill hướng dẫn

---

## 📋 Tasks Đang Làm

### Task #005 — Google Drive Screen
**Mô tả:** Màn hình Google Drive với BrowseScreen UI + full features (copy/move/dialogs)
**Screen:** GoogleDrive (replica BrowseScreen)
**API:** Google Drive API v3
**Auth:** Google Sign-In OAuth 2.0
**Files cần tạo:**
- `ui/googledrive/GoogleDriveUiState.kt`
- `ui/googledrive/GoogleDriveViewModel.kt`
- `ui/googledrive/GoogleDriveScreen.kt`
- `data/GoogleDriveRepository.kt`
- `ui/googledrive/GoogleDriveAuthScreen.kt`
**Files cần sửa:**
- `MainViewModel.kt` (thêm Screen entry)
- `MainActivity.kt` (thêm rendering)
- `DrawerPane.kt` (thêm nav item)
- `app/build.gradle.kts` (thêm dependency)

---

## 📌 TODO — Tasks Sắp Làm

### Priority: Cao
- [ ] **Thêm clipboard sync** — Đồng bộ với system clipboard (copy path)
- [ ] **Kiểm tra permission handling** — Android 13+ READ_MEDIA_* permissions
- [ ] **Thêm favorites system** — Yêu thích file/folder

### Priority: Trung bình
- [ ] **Text Editor improvements** — Auto-save, font size persistence
- [ ] **Thêm shortcut/quick actions** — Home screen shortcuts
- [ ] **SelectionActionHandler audit** — Kiểm tra tất cả actions đã implement

### Priority: Thấp
- [ ] **Dark mode** — Thêm dark theme support
- [ ] **Storage analysis** — Phân tích dung lượng chi tiết
- [ ] **Batch rename** — Đổi tên nhiều file cùng lúc
- [ ] **File preview enhancements** — Preview cho nhiều loại file hơn

---

## 📝 Ghi Chú Quan Trọng

### Workflow Hoàn Thành Task
```
1. /task-tracking → đọc TASKS.md
2. Thêm task vào "Tasks Đang Làm"
3. Làm task
4. Build OK
5. /updating-claude → Cập nhật TASKS.md → CLAUDE.md → Skills
6. Commit
```

### Convention Quan Trọng
- Task files nằm trong `.claude/skills/`
- Invoke bằng `/task-name`
- TASKS.md luôn cập nhật sau mỗi task
- CLAUDE.md luôn cập nhật sau TASKS.md

---

## 🔄 Update Log

| Ngày | Ghi chú |
|---|---|
| 2026-03-31 | Tạo TASKS.md, ghi nhận Task #001-#004 |
