package com.example.codevui

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.codevui.ui.thumbnail.ThumbnailManager

/**
 * Singleton ImageLoader với:
 *   - Custom fetchers: video, audio, APK, archive thumbnails (qua ThumbnailManager)
 *   - Memory cache: 15% RAM
 *   - Disk cache: 100MB
 *
 * Dùng trong Application.onCreate():
 *   Coil.setImageLoader(AppImageLoader.get(this))
 */
object AppImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
    }

    private fun build(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15) // 15% RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("thumbnail_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB
                    .build()
            }
            // Đăng ký tất cả thumbnail factories qua ThumbnailManager
            .apply { ThumbnailManager.register(this) }
            .crossfade(true)
            .build()
    }
}
