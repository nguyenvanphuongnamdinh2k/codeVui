# CodeVui — Memory

> Long-term context và learning giữa các Claude sessions.
> Khác TASKS.md (lịch sử task cụ thể) và CLAUDE.md (architecture reference).
> File này ghi **các learning, bẫy, convention mới khám phá, quyết định design** — những thứ không thuộc về spec cố định.
> Last updated: 2026-04-11.

---

## Key Learnings

### L001 — MediaStoreObserver debounce 500ms là đủ
- **Date:** 2026-03-15
- **Context:** Lúc đầu observer fire liên tục khi copy nhiều file → reload UI giật.
- **Solution:** `.debounce(500).conflate()` trong `MediaStoreObserver.observe()`. Sau 500ms im mới emit 1 lần.
- **Avoid:** Đừng giảm xuống < 300ms, không đủ thời gian để MediaStore flush batch.

### L002 — TrashManager luôn scan parent sau move
- **Date:** 2026-03-20
- **Context:** File di chuyển vào `.Trash` nhưng MediaStore vẫn thấy ở vị trí cũ.
- **Solution:** `MediaStoreScanner.scanPaths(parentDirs)` sau mỗi `moveToTrash` và `restore`.
- **Avoid:** KHÔNG scan `.Trash` trực tiếp — gây infinite loop với MediaStoreObserver.

### L003 — Selection mode không thoát khi uncheck hết
- **Date:** 2026-04-06 (Task #015)
- **Context:** Samsung My Files giữ selection mode khi user bỏ check hết item, CodeVui phải làm giống.
- **Solution:** `SelectionState.toggle(id)` chỉ sửa `selectedIds`, không gọi `exit()`. Chỉ nút "Thoát" mới gọi `exit()`.
- **Learning:** Bottom bar có thể ẩn (visible = `isSelectionMode && selectedCount > 0`), nhưng top bar (header "Đã chọn 0") vẫn ở lại.

### L004 — Menu visibility theo ContextualMenuUpdateOperator của MyFiles
- **Date:** 2026-04-07 (Task #017)
- **Context:** Menu "Nén/Giải nén/Yêu thích" hiện bừa → trông buggy.
- **Solution:** `SelectionActions` dùng nullable callback (`onCompress: (() -> Unit)? = null`). Null → ẩn menu item.
- **Rules:**
  - `Nén` chỉ hiện khi KHÔNG có archive trong selection
  - `Giải nén` chỉ hiện khi CÓ archive
  - `Thêm vào yêu thích` chỉ hiện khi `favoriteCount < selectedCount`
  - `Xóa khỏi yêu thích` chỉ hiện khi `favoriteCount > 0`

### L005 — `getDataDirectory()` ≠ `getExternalStorageDirectory()`
- **Date:** 2026-04-07 (Task #017)
- **Context:** Internal storage hiện 223GB thay vì 256GB.
- **Solution:** `getDataDirectory()` trả về `/data` (user partition, ~223GB), còn `getExternalStorageDirectory()` trả về `/storage/emulated/0` (full capacity ~256GB).
- **Rule:** Dùng `getExternalStorageDirectory()` cho volume hiển thị user-facing.

### L006 — Favorite mimeType null phá thumbnail
- **Date:** 2026-04-06 (Task #014)
- **Context:** Sau khi lưu favorite, mimeType null → FileType.OTHER → không load thumbnail.
- **Solution:** `FavoriteManager.toFavoriteItem()` resolve mimeType từ extension nếu null. `resolveContentUri()` query MediaStore cho content:// URI.
- **Pattern:** Fallback luôn trên mọi field nullable khi load từ DB.

### L007 — AudioThumbnailFetcher cần handle cả file:// và content:// URI
- **Date:** 2026-04-06 (Task #014)
- **Context:** MediaMetadataRetriever.setDataSource(String) crash với content:// URI.
- **Solution:** Check URI scheme → dùng `setDataSource(FileDescriptor)` cho content://.
- **Tương tự cho ApkThumbnailFetcher.**

### L008 — Room v1→v2→v3 migration
- **Date:** 2026-04-04 (Task #013)
- **Context:** Thêm bảng `favorites` vào Room DB.
- **Solution:** `MIGRATION_2_3` thêm `CREATE TABLE favorites(...)`. Không quên index `path` UNIQUE + `sortOrder`.
- **Avoid:** Đừng tăng version mà không viết migration — user bị wipe data.

### L009 — FileOperationService MUST scan sau khi Done
- **Date:** 2026-03-25
- **Context:** Copy xong, nhìn ở app khác chưa thấy file.
- **Solution:** Trong `FileOperationService.onStateUpdate(Done)`, gọi `MediaStoreScanner.scanSourceAndDest(src, dest)` TRƯỚC khi `stopSelf()`.
- **Rule:** Mọi flow file operation đều kết thúc với scan.

### L010 — zip4j `removeFile()` in-place rất nhanh
- **Date:** 2026-03-28
- **Context:** Xóa file trong ZIP lớn bị lag — nghĩ phải extract + re-archive.
- **Solution:** `ZipFile.removeFile(fileHeader)` tự rewrite in-place, nhanh gấp 10x.
- **Limit:** Cần password nếu ZIP encrypted.

### L011 — Extract progress cần byte-level (không phải file-level)
- **Date:** 2026-04-11 (Task #022)
- **Context:** zip4j `extractFile()` không cho biết tiến trình bên trong file lớn (1 file 500MB → hiện 0% rồi nhảy 100%).
- **Solution:** Dùng `zipFile.getInputStream(fileHeader)` + manual buffer read (64KB) + đếm bytesRead. Tính percent = handledBytes/totalSize.
- **Avoid:** Không dùng `extractFile()` cho progress tracking. Chỉ dùng cho quick extract (không cần progress).

### L012 — Intent không chứa được object lớn cho ForegroundService
- **Date:** 2026-04-11 (Task #022)
- **Context:** `startExtract` cần truyền `List<ArchiveEntry>` (có thể hàng ngàn entry) → Intent quá lớn → TransactionTooLargeException.
- **Solution:** Static `ConcurrentHashMap<Int, ExtractData>` cache. Service đọc xong thì remove ngay.
- **Rule:** Luôn cleanup static cache sau khi đọc để tránh memory leak.

---

## Architectural Decisions

### D001 — Single Activity + screenStack (không dùng Jetpack Navigation)
- **Why:** Navigation-compose quá nặng cho nhu cầu đơn giản. `MutableStateList<Screen>` survive recomposition + dễ deep-link.
- **Trade-off:** Không có back stack save state tự động — phải tự xử lý rotation trong ViewModel (SavedStateHandle).

### D002 — ForegroundService thay vì WorkManager cho file operation
- **Why:** User cần thấy progress bar realtime + nút Cancel. WorkManager không hỗ trợ tốt 2-way communication.
- **Trade-off:** Phải tự handle notification channel + permission POST_NOTIFICATIONS.

### D003 — In-app ClipboardManager tách với system clipboard
- **Why:** System clipboard không support MOVE semantics (chỉ COPY). Plus rotation-safe qua SavedStateHandle.

### D004 — BaseMediaStoreViewModel ≠ ArchiveViewModel
- **Why:** Archive không đọc MediaStore — chỉ đọc zip entries. Nên không extend BaseMediaStoreViewModel (sẽ reload vô ích).

### D005 — Dialog state machine (sealed class) thay vì nhiều MutableState<Boolean>
- **Why:** 10+ dialog trong `DialogManager` — quản lý qua `sealed class DialogState` dễ hơn nhiều.

### D006 — Port MyFiles multi-volume (Task #016)
- **Why:** Muốn support SD card và USB OTG cho Samsung/các máy có slot.
- **Implementation:** `StorageVolumeManager` + `DomainType` + `StorageUsageManager` + `StorageTypeForTrash` — mirror 1:1 từ MyFiles.
- **Skip:** `KnoxManager`, `AfwManager` (Samsung-only).

### D007 — Multi-operation + Extract progress + WakeLock (Task #022)
- **Why:** MyFiles hỗ trợ chạy nhiều operation cùng lúc (copy + move + extract). CodeVui chỉ chạy 1 → cần nâng cấp.
- **Implementation:** ConcurrentHashMap<Int, Job> cho multi-op, AtomicInteger cho unique ID, WakeLock cho CPU keep-alive. Extract dùng zip4j getInputStream() + manual buffer read thay vì extractFile() để có byte-level progress.
- **Key Decision:** Giữ Kotlin Flow architecture (không dùng Java callback như MyFiles). ArchiveViewModel extends BaseFileOperationViewModel thay vì AndroidViewModel.
- **Gotcha:** Intent không chứa được List<ArchiveEntry> lớn → dùng static ConcurrentHashMap cache (pendingExtractData), cleanup sau khi đọc.
- **Gotcha:** Move trong archive = extract (service, có progress) + remove entries (in-place, nhanh) → split 2 phase.

---

## Gotchas / Common Bugs

- **Không gọi `FileRepository` từ Main thread** — MediaStore query chậm, luôn `withContext(Dispatchers.IO)`.
- **Không dùng `java.io.File.length()` trong loop** — thay bằng MediaStore query batch.
- **SelectionCheckbox cần `imeNestedScroll = false`** — nếu không, scroll bị chặn.
- **ArchiveScreen previewMode=true phải clear clipboard khi thoát** — không thì paste nhầm vào vị trí ngoài ZIP.
- **DuplicatesScreen phải cache hash** — nếu không, scan lại folder 10k file mất 30s.
- **LargeFilesScreen cần exclude `.Trash`** — không thì file trong trash cũng tính là large.

---

## Context về User

- **User:** nguyen (giacatphuong2k@gmail.com)
- **Ngôn ngữ ưu tiên:** Tiếng Việt (UI + comment + chat reply)
- **Device chính:** Samsung Android 13+
- **Sở thích:** theo sát Samsung My Files style càng giống càng tốt
- **Workflow:** Làm task nhỏ, commit từng feature, cập nhật TASKS.md sau mỗi lần

---

## Template khi thêm Learning mới

```
### L### — [Tóm tắt ngắn]
- **Date:** YYYY-MM-DD (Task #xxx nếu có)
- **Context:** vấn đề gặp phải
- **Solution:** cách giải quyết
- **Avoid/Rule:** tránh gì / quy tắc rút ra
```
