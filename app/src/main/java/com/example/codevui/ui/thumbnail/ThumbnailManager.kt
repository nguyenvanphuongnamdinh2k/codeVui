package com.example.codevui.ui.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/**
 * Base ThumbnailFetcher abstract class - abstract class cha cho tất cả thumbnail fetchers.
 * Đảm bảo interface统一 và reusable logic (fetch() method).
 */
abstract class ThumbnailFetcher(
    protected val options: Options
) : Fetcher {

    /** Abstract method: thực hiện extract/generate bitmap cho từng loại file */
    protected abstract suspend fun extractBitmap(): Bitmap?

    override suspend fun fetch(): FetchResult? {
        val bitmap = extractBitmap() ?: return null
        return DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }
}

/**
 * Sealed class chứa tất cả loại thumbnail data.
 * Thêm loại thumbnail mới → thêm entry vào đây.
 */
sealed class ThumbnailData {
    abstract val uri: Uri
    abstract val path: String

    data class Video(
        override val uri: Uri,
        override val path: String
    ) : ThumbnailData()

    data class Audio(
        override val uri: Uri,
        override val path: String
    ) : ThumbnailData()

    data class Apk(
        override val uri: Uri,
        override val path: String
    ) : ThumbnailData()

    data class Archive(
        override val uri: Uri,
        override val path: String,
        val entryPath: String,
        val password: String? = null
    ) : ThumbnailData()
}

/**
 * ThumbnailManager - Singleton quản lý tất cả thumbnail factories.
 * Đăng ký factories vào ImageLoader.
 */
object ThumbnailManager {

    /**
     * Đăng ký tất cả thumbnail factories vào ImageLoader.Builder
     */
    fun register(builder: ImageLoader.Builder) {
        builder.components {
            add(VideoThumbnailFetcher.Factory())
            add(AudioThumbnailFetcher.Factory())
            add(ApkThumbnailFetcher.Factory())
            add(ArchiveThumbnailFetcher.Factory())
        }
    }

    /**
     * Helper: build ImageLoader hoàn chỉnh với thumbnail support.
     * Dùng trong AppImageLoader.
     */
    fun buildImageLoader(context: Context, crossfade: Boolean = true): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                coil.memory.MemoryCache.Builder(context)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("thumbnail_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .apply { register(this) }
            .crossfade(crossfade)
            .build()
    }
}
