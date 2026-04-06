# SKILLS — CodeVui Quick Reference

> Skills nằm trong `.claude/skills/`. Invoke bằng `/skill-name`.
> Đọc skill file trước khi bắt đầu task.

## 🚀 Invoking Skills

```
/task-tracking        Quản lý task và session memory
/add-dialog          Thêm dialog mới
/add-screen          Thêm screen/ViewModel mới
/add-file-operation  Thêm thao tác file mới
/logging             Logging convention
/updating-claude     Cập nhật CLAUDE.md sau thay đổi
/mediastore          MediaStore & File I/O guide
```

## ⚡ Quick Conventions

| Convention | Rule |
|:---|:---|
| Dialog mới | LUÔN qua DialogManager |
| Screen mới | Extend BaseMediaStoreViewModel (TRỪ Archive) |
| File operation lâu | Dùng FileOperationService |
| File operation nhanh | java.io.File + MediaStoreScanner |
| Sau thao tác file | LUÔN gọi MediaStoreScanner.scan*() |
| Log | Dùng Logger utility, TRUYỀN exception khi error |
| Sau task xong | Cập nhật CLAUDE.md → Commit |
| KHÔNG query MediaStore trực tiếp | Luôn qua FileRepository |
| KHÔNG blocking I/O trên main thread | viewModelScope.launch(Dispatchers.IO) |

## 📁 File Structure

```
CodeVui/
├── CLAUDE.md                          # Tổng quan toàn bộ codebase
├── SKILLS.md                          # File này — quick reference
└── .claude/
    └── skills/
        ├── add-dialog/SKILL.md      # Thêm dialog
        ├── add-screen/SKILL.md        # Thêm screen
        ├── add-file-operation/SKILL.md # Thêm file operation
        ├── logging/SKILL.md           # Logging convention
        ├── updating-claude/SKILL.md   # Cập nhật CLAUDE.md
        └── mediastore/SKILL.md        # MediaStore guide
```

## 🔗 How to Use

### Bắt đầu task mới
```
1. Invoke skill: /add-dialog | /add-screen | /add-file-operation
2. Read SKILL.md để hiểu pattern
3. Read CLAUDE.md để hiểu codebase tổng quan
4. Code
5. Read /logging để thêm log
6. Read /updating-claude → cập nhật CLAUDE.md
7. Commit
```

### Khi cần tương tác file
```
1. Read /mediastore
2. Chọn đúng pattern: FileRepository | FileOperationService | MediaStoreScanner
3. LUÔN scan sau thao tác
```
