# UI Components Reference

> Chi tiết UI components trong CodeVui. Dùng khi cần thêm/sửa component.

---

## Selection Components

### SelectionTopBar
Thay TopAppBar khi selection mode.
- "Tất cả" checkbox | "Đã chọn N" | Thoát

### SelectionBottomBar
4 nút chính: **Di chuyển | Sao chép | Chia sẻ | Xóa**
+ "N.hơn" dropdown menu.

**Menu visibility (MyFiles pattern — menu chỉ hiện khi hành vi hợp lệ):**

| Menu | Điều kiện hiện |
|---|---|
| Nén | chỉ khi **không có** archive files (`zip/rar/7z/tar/gz`) |
| Giải nén | chỉ khi **có** archive files được chọn |
| Thêm vào yêu thích | chỉ khi **ít nhất 1 item chưa favorite** |
| Xóa khỏi yêu thích | chỉ khi **có item đã favorite** |

**`SelectionActions` data class:**
```kotlin
data class SelectionActions(
    val onMove: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onCopyToClipboard: () -> Unit = {},
    val onDetails: () -> Unit = {},
    val onRename: () -> Unit = {},
    val onCompress: (() -> Unit)? = null,              // null = ẩn
    val onExtract: (() -> Unit)? = null,               // null = ẩn
    val onAddToFavorites: (() -> Unit)? = null,       // null = ẩn
    val onRemoveFromFavorites: (() -> Unit)? = null,  // null = ẩn
    val onAddToHomeScreen: () -> Unit = {}
)
```

### SelectionCheckbox
Circular checkbox (Samsung style). 28dp, blue fill when selected.

### Selection Mode Pattern
**Chỉ thoát selection mode khi user nhấn nút Thoát.**

| Hàm | Hành vi |
|---|---|
| `toggle(id)` | Bỏ check → xóa khỏi selectedIds, **giữ mode** |
| `selectAll(ids)` | Bỏ check all → empty set, **giữ mode** |
| `exit()` | Thoát mode — CHỈ gọi khi nhấn nút Thoát |

**Bottom bar:** `visible = isSelectionMode && selectedCount > 0`
- Khi selectedCount=0 → bottom bar ẩn, nhưng mode vẫn bật
- Header vẫn hiện "Đã chọn 0" + nút Thoát

---

## Navigation Components

### SortBar
- **Left:** "Cần thiết / Tất cả" filter dropdown (chỉ hiện ở root)
- **Right:** Sort type (Ngày/Tên/Kích thước) + direction (↑/↓)

### Breadcrumb
- Single: 🏠 > Tên | Size
- Multi: 🏠 > Bộ nhớ trong > DCIM > Camera
- Last segment: màu xanh, không clickable

---

## List Components

### FileListItem
File row: thumbnail + name + size + date

### FolderListItem
Folder row: name + item count

### ArchiveEntryItem
Entry row inside ZIP

---

## Thumbnail System (`ui/thumbnail/`)

### Coil Custom Fetchers

| Fetcher | Method |
|---|---|
| `VideoThumbnailFetcher` | ContentResolver.loadThumbnail (Q+) / ThumbnailUtils (legacy) |
| `AudioThumbnailFetcher` | MediaMetadataRetriever.embeddedPicture |
| `ApkThumbnailFetcher` | PackageManager.getApplicationIcon() → Bitmap |
| `ArchiveThumbnailFetcher` | ArchiveReader.extractToTemp → BitmapFactory.decodeFile |

**Architecture:**
- `ThumbnailFetcher` (abstract base): chứa `fetch()` method + abstract `extractBitmap()`
- `ThumbnailData` (sealed class): Video/Audio/Apk/Archive data classes
- `ThumbnailManager`: singleton đăng ký factories vào ImageLoader

**Lưu ý quan trọng:**
- Luôn check `path.isNotEmpty()` trước khi tạo `ThumbnailData.Video/Audio/Apk`
- IMAGE dùng Uri trực tiếp, không cần custom fetcher
- `FavoriteThumbnail` track `imageLoaded` state — chỉ có background khi chưa load xong

---

## Common Components (`ui/components/`)

| Component | Mô tả |
|---|---|
| `IconBox` | Icon với background color |
| `ListRow` | Generic list row wrapper |
| `CategoryCard` | Category card cho HomeScreen grid |
| `StorageBadge` | Storage usage badge |
| `HighlightedText` | Text với search term highlighted |
