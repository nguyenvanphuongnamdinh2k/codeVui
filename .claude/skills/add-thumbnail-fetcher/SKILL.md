---
name: add-thumbnail-fetcher
description: Thêm Coil custom thumbnail fetcher mới cho CodeVui. Dùng khi cần load thumbnail cho file type chưa hỗ trợ (PDF, EPUB, SVG, DWG, …) hoặc cần extract preview từ format đặc biệt.
---

# Thêm Thumbnail Fetcher — CodeVui

## Pattern Overview

CodeVui dùng Coil 2.6.0 với custom fetcher architecture:

```
ImageLoader
  └── Components
        ├── VideoThumbnailFetcher   → ThumbnailData.Video
        ├── AudioThumbnailFetcher   → ThumbnailData.Audio
        ├── ApkThumbnailFetcher     → ThumbnailData.Apk
        ├── ArchiveThumbnailFetcher → ThumbnailData.Archive
        └── [YOUR_NEW_FETCHER]      → ThumbnailData.YourType
```

- **`ThumbnailFetcher` (abstract)** ở `ui/thumbnail/ThumbnailManager.kt` — base class, chứa `fetch()` + abstract `extractBitmap()`
- **`ThumbnailData` (sealed class)** — mỗi subtype ứng với 1 fetcher
- **`ThumbnailManager.setup(context, imageLoader)`** — register factories

## Step-by-Step

### Step 1 — Thêm sealed class entry

**File:** `app/src/main/java/com/example/codevui/ui/thumbnail/ThumbnailManager.kt`

```kotlin
sealed class ThumbnailData {
    // ... existing ...
    data class YourType(val path: String, val extra: String? = null) : ThumbnailData()
}
```

**Lưu ý:** luôn có `path: String` field đầu tiên (convention).

### Step 2 — Tạo Fetcher class

**File:** `app/src/main/java/com/example/codevui/ui/thumbnail/YourThumbnailFetcher.kt`

```kotlin
package com.example.codevui.ui.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.example.codevui.util.Logger
import okio.buffer
import okio.source
import java.io.File

class YourThumbnailFetcher(
    private val data: ThumbnailData.YourType,
    private val context: Context,
) : ThumbnailFetcher() {

    private val log = Logger("YourThumbnailFetcher")

    override suspend fun extractBitmap(): Bitmap? {
        return try {
            // Logic extract bitmap từ file
            val file = File(data.path)
            if (!file.exists()) return null
            // Example: decode first page / frame / embedded preview
            BitmapFactory.decodeFile(data.path)  // replace with real extraction
        } catch (e: Exception) {
            log.e("extractBitmap failed: ${e.message}", e)
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<ThumbnailData.YourType> {
        override fun create(
            data: ThumbnailData.YourType,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return YourThumbnailFetcher(data, context)
        }
    }
}
```

### Step 3 — Đăng ký Factory

**File:** `app/src/main/java/com/example/codevui/ui/thumbnail/ThumbnailManager.kt`

```kotlin
object ThumbnailManager {
    fun setup(context: Context, builder: ImageLoader.Builder): ImageLoader.Builder {
        return builder
            .components {
                // ... existing ...
                add(VideoThumbnailFetcher.Factory(context))
                add(AudioThumbnailFetcher.Factory(context))
                add(ApkThumbnailFetcher.Factory(context))
                add(ArchiveThumbnailFetcher.Factory(context))
                add(YourThumbnailFetcher.Factory(context))  // ← THÊM
            }
    }
}
```

### Step 4 — Dùng trong composable

**File:** `app/src/main/java/com/example/codevui/ui/components/FileListItem.kt` (hoặc nơi cần)

```kotlin
val thumbnailData = when {
    file.path.endsWith(".your_ext", ignoreCase = true) && file.path.isNotEmpty() ->
        ThumbnailData.YourType(file.path)
    // ... existing mappings ...
    else -> null
}

AsyncImage(
    model = thumbnailData ?: file.uri,  // fallback to URI
    contentDescription = file.name,
    imageLoader = AppImageLoader.get(context),
    // ...
)
```

### Step 5 — Extend FileType detection (nếu cần)

**File:** `app/src/main/java/com/example/codevui/model/Model.kt`

```kotlin
enum class FileType {
    IMAGE, VIDEO, AUDIO, DOC, APK, ARCHIVE, DOWNLOAD, YOUR_TYPE, OTHER
}
```

Và update:
- `FileRepository.getFileType(mimeType)` — map MIME → FileType
- `FormatUtils` / icon picker — icon cho FileType mới
- `FileListScreen` filter chip nếu cần

## Checklist

- [ ] Sealed class entry `ThumbnailData.YourType`
- [ ] `YourThumbnailFetcher.kt` extend `ThumbnailFetcher`
- [ ] `extractBitmap()` handle lỗi (trả null)
- [ ] `Factory` inner class
- [ ] Register trong `ThumbnailManager.setup()`
- [ ] Map file extension → `ThumbnailData.YourType` ở composable
- [ ] Check `path.isNotEmpty()` trước khi tạo `ThumbnailData`
- [ ] Logger để debug
- [ ] Handle cả `file://` và `content://` URI nếu cần (xem `AudioThumbnailFetcher` pattern)
- [ ] Test trên file thật — không crash khi file corrupted/missing

## Common Pitfalls

1. **Quên `path.isNotEmpty()` check** → crash khi model init
2. **Không register factory** → Coil fallback về URI → sai thumbnail
3. **Blocking main thread** trong `extractBitmap` → UI jank. Fetcher tự chạy trên IO thread nhưng bitmap decode heavy nên cần cẩn thận.
4. **Không cache** → decode lại mỗi scroll. Coil có memory cache sẵn, nhưng cần đảm bảo `equals/hashCode` của `ThumbnailData` đúng (data class tự sinh, OK).
5. **Content URI crash MediaMetadataRetriever** → dùng `ContentResolver.openFileDescriptor` + `setDataSource(FileDescriptor)`
6. **OOM với file to** → resize với `BitmapFactory.Options.inSampleSize`

## Examples to copy from

- `VideoThumbnailFetcher.kt` — dùng ContentResolver.loadThumbnail (Q+) / ThumbnailUtils
- `AudioThumbnailFetcher.kt` — MediaMetadataRetriever với embedded picture
- `ApkThumbnailFetcher.kt` — PackageManager.getApplicationIcon
- `ArchiveThumbnailFetcher.kt` — extract-to-temp + decodeFile

## Sau khi thêm

1. `./gradlew assembleDebug` — verify build
2. Test trên device với file thật
3. Update `CLAUDE.md` section 2 (Package Structure)
4. Update `CLAUDE.md` section 13 "Coil Custom Fetchers" table
5. Update `TASKS.md` task entry
