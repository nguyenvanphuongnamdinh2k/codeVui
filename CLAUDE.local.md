# CodeVui — CLAUDE.local.md

> Ghi chú dev local, **không commit** lên git. Đặt cạnh CLAUDE.md (root).
> CLAUDE.md = project-wide, đồng bộ với team. CLAUDE.local.md = cá nhân, máy mình.
> Last updated: 2026-04-08.

---

## 1. Môi trường Dev Local

| Thông tin | Giá trị |
|---|---|
| OS | Windows 11 |
| IDE | Android Studio Hedgehog (2023.1+) |
| JDK | 17 |
| Android SDK | Cài tại `C:\Users\phuong\AppData\Local\Android\Sdk` |
| Device test | Samsung Galaxy (Android 13+) — real device cho MANAGE_EXTERNAL_STORAGE |
| Emulator | Pixel 6 API 34 — test cho non-Samsung |
| Gradle wrapper | `./gradlew` (Linux/macOS) / `gradlew.bat` (Windows) |

## 2. Commands Hay Dùng

```bash
# Build debug
./gradlew assembleDebug

# Build + cài lên device
./gradlew installDebug

# Clean + rebuild
./gradlew clean assembleDebug

# Compile nhanh (chỉ check Kotlin)
./gradlew :app:compileDebugKotlin

# Verify KSP (cho Room)
./gradlew :app:kspDebugKotlin --stacktrace
```

**Build thường xuyên fail do:**
- Room schema mismatch → tăng version + viết Migration
- Coil API breaking change → check bump lại `Coil 2.6.0`
- zip4j exception mới → catch thêm `ZipException`

## 3. Sở thích Cá nhân

- Tiếng Việt trong UI + comment, tiếng Anh trong code/class name
- Style: 4 space indent, trailing comma cho list nhiều item
- Dialog naming: `*Dialog.kt` (không prefix)
- Screen naming: `*Screen.kt` + `*ViewModel.kt` + `*UiState.kt` (3 file nhóm)
- Không dùng `!!` — ưu tiên `?.let { }` hoặc `requireNotNull`
- Chỉ dùng `LaunchedEffect(Unit)` cho 1-shot side effect, còn lại dùng key

## 4. Ghi chú MyFiles Quick Reference

Hay đọc:
- `presenter/managers/EnvManager.kt` — để hiểu multi-volume
- `ui/dialog/DialogManager.kt` — để hiểu dialog flow
- `ui/menu/operator/*.kt` — để hiểu contextual menu visibility
- `ui/pages/managestorage/` — để hiểu Quản lý lưu trữ UI

## 5. TODO Local (chưa gán task chính thức)

- [ ] Thử port `DragManager` của MyFiles → Compose DnD cho CodeVui
- [ ] Nghiên cứu `AsyncLayoutInflateManager` — Compose có tương đương không?
- [ ] Viết unit test cho `FileOperations` (hiện chưa có test)
- [ ] Thêm dark mode theme tùy biến (hiện chỉ dùng Material 3 dynamic)

## 6. Debug Tips

- `Logger("TAG").d("msg")` → logcat filter `[Class.method:line]`
- Trash debug: xem `.Trash/files/` trên device
- FileOperationService không chạy → check notification permission + ForegroundServiceType
- MediaStoreObserver không emit → thường là do `.Trash` bị observed — check excluded paths
- Coil không load thumbnail → check register `ThumbnailManager.setup(context)` trong `AppImageLoader`

## 7. Ghi chú Cập nhật CLAUDE.md

Khi thay đổi package structure nhiều:
1. Cập nhật section 2 (Package Structure) của CLAUDE.md
2. Update bảng mapping ở section 18 nếu port thêm từ MyFiles
3. Thêm entry vào TASKS.md
4. Update memory.md nếu learning mới về architecture

---

**Quy tắc file này:**
- Không bao giờ commit — đã trong `.gitignore` (thêm nếu chưa)
- Ghi bất cứ thứ gì hữu ích cá nhân: env, shortcuts, learning, TODO nhỏ
- Không ghi secret (API key, token) — dùng `local.properties` cho secret
