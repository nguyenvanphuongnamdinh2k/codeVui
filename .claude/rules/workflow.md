# Rule — Workflow

> Quy tắc workflow cho Claude khi làm việc trên CodeVui. Đọc trước mỗi session.

## 1. Bắt đầu Session

1. **Đọc CLAUDE.md** (root) — project spec
2. **Đọc `.claude/TASKS.md`** — lịch sử + TODO
3. **Đọc `.claude/memory.md`** — learning + decision
4. **Đọc skill(s) liên quan** — tìm trong `.claude/skills/` theo task
5. **Xác nhận đã đọc đủ** trước khi sửa code

## 2. Trong Khi Làm

- **Luôn dùng `TodoWrite`** ngay từ đầu nếu task > 2 bước
- **Mỗi lần Edit/Write file** → update todo status
- **Không commit giữa chừng** — chỉ commit khi build success
- **Log mọi quyết định lớn** (> 30 phút suy nghĩ) vào `memory.md` sau khi xong
- **Tuân thủ MVVM convention** — xem `.claude/rules/mvvm.md`
- **Ưu tiên reference MyFiles** khi port feature mới — xem agent `myfiles-reference`

## 3. Kết Thúc Task

Checklist bắt buộc:
- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] Cập nhật `CLAUDE.md` nếu thay đổi cấu trúc (file mới/xóa/rename)
- [ ] Cập nhật `TASKS.md` với entry mới (task #N, files thay đổi)
- [ ] Cập nhật skill liên quan nếu thêm pattern mới
- [ ] Cập nhật `memory.md` nếu có learning
- [ ] Báo cáo cho user theo format ở CLAUDE.md section 17

## 4. Cấm

- ❌ Commit khi build fail
- ❌ Xóa file mà không thông báo trước
- ❌ Sửa `build.gradle.kts` mà không có lý do ghi rõ trong TASKS.md
- ❌ Thêm dependency mới mà không check kích thước APK
- ❌ Dùng `!!` trong code mới
- ❌ Block main thread (mọi I/O phải `withContext(Dispatchers.IO)`)
- ❌ Truy cập MediaStore trực tiếp — qua `FileRepository`
- ❌ Dùng system clipboard cho file ops — qua `ClipboardManager`

## 5. Khuyến khích

- ✅ Dùng skill đã có — không reinvent
- ✅ Viết log qua `Logger(class)` — không `Log.d` raw
- ✅ Scan MediaStore sau mọi file operation
- ✅ Test trên device thật nếu có `MANAGE_EXTERNAL_STORAGE`
- ✅ Copy pattern từ MyFiles khi port feature phức tạp
