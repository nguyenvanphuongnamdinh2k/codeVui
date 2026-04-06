---
name: auto-build
description: Tự động build project sau khi hoàn thành bất kỳ task nào. Chạy Gradle build, kiểm tra lỗi, và report kết quả.
---

# Auto-Build Skill — CodeVui

## Mục đích

**LUÔN chạy build** sau khi hoàn thành bất kỳ thay đổi code nào — bất kể lớn hay nhỏ.

```
Thay đổi code → Build → Kiểm tra lỗi → Sửa nếu cần → Done
```

## Khi Nào Chạy Build

| Tình huống | Chạy build? |
|---|---|
| Thêm screen mới | ✅ Luôn |
| Sửa lỗi | ✅ Luôn |
| Thêm dependency | ✅ Luôn |
| Đổi tên class/file | ✅ Luôn |
| Chỉ sửa comment/docs | ✅ Luôn |
| Đọc file mà không sửa | ❌ Không |

## Build Commands

### Assemble Debug (nhanh nhất)
```bash
cd C:\Users\phuong\Desktop\codeMyFiles\CodeVui\CodeVui
.\gradlew assembleDebug --no-daemon
```

### Nếu cần clean trước
```bash
.\gradlew clean assembleDebug --no-daemon
```

### Nếu cần full build với tất cả tasks
```bash
.\gradlew build --no-daemon
```

## Workflow Hoàn Thành Task

```
1. Code thay đổi (Write/Edit files)
   ↓
2. CHẠY BUILD NGAY
   .\gradlew assembleDebug --no-daemon
   ↓
3. Build THÀNH CÔNG
   → Cập nhật TASKS.md
   → Cập nhật CLAUDE.md (sections liên quan)
   → Cập nhật Skills liên quan
   → Commit
   ↓
4. Build THẤT BẠI (lỗi compile)
   → Đọc lỗi từ output
   → Sửa lỗi (import thiếu, typo, type mismatch)
   → Chạy build lại
   → Lặp đến khi thành công
   ↓
5. Done
```

## Đọc Lỗi Build

### Lỗi thường gặp

**1. Import thiếu**
```
Unresolved reference: Xxx
```
→ Thêm `import package.Xxx` vào đầu file.

**2. Type mismatch**
```
Type mismatch: inferred type is A but B was expected
```
→ Kiểm tra kiểu trả về, cast nếu cần.

**3. Deprecated API**
```
API deprecated in Java 11
```
→ Thay bằng API mới tương đương.

**4. Duplicate declaration**
```
Duplicate declaration, name already seen
```
→ Xóa duplicate hoặc rename.

**5. Unresolved reference: Logger**
```
Unresolved reference: Logger
```
→ Kiểm tra: `Logger` nằm trong package `com.example.codevui.util.Logger`.
Nếu class `Logger` trùng tên với java.util.logging.Logger:
```kotlin
private val log = com.example.codevui.util.Logger("ClassName")
```

**6. Parameter/argument mismatch**
```
No value passed for parameter 'xxx'
```
→ Kiểm tra signature của function được gọi.

### Sau khi sửa lỗi

Chạy build lại:
```bash
.\gradlew assembleDebug --no-daemon
```

## Auto-Build Checklist

- [ ] Chạy `.\gradlew assembleDebug --no-daemon`
- [ ] Đọc output — có `BUILD SUCCESSFUL`?
- [ ] Nếu có lỗi → đọc lỗi → sửa → build lại
- [ ] Build thành công → tiếp tục documentation workflow

## Lưu ý Quan Trọng

- **KHÔNG BAO GIỜ** kết thúc task mà không build
- **KHÔNG BAO GIỜ** commit khi build đang fail
- Nếu lỗi không rõ ràng, chạy `.\gradlew assembleDebug --stacktrace --no-daemon`
- Nếu build rất chậm, có thể dùng `--offline` nếu đã có cache
