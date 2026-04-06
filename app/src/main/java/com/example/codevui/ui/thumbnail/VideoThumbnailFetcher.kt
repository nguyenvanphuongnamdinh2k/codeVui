package com.example.codevui.ui.thumbnail

import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File

/**
 * ThumbnailFetcher cho video files.
 * Hỗ trợ cả content:// URIs (MediaStore) và file:// URIs.
 */
class VideoThumbnailFetcher(
    private val data: ThumbnailData.Video,
    options: Options
) : ThumbnailFetcher(options) {

    override suspend fun extractBitmap(): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && data.uri.scheme == "content") {
            try {
                options.context.contentResolver.loadThumbnail(
                    data.uri,
                    Size(256, 256),
                    null
                )
            } catch (e: Exception) {
                extractLegacy(data.path)
            }
        } else {
            extractLegacy(data.path)
        }
    }

    private fun extractLegacy(path: String): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.media.ThumbnailUtils.createVideoThumbnail(
                    File(path),
                    Size(256, 256),
                    null
                )
            } else {
                @Suppress("DEPRECATION")
                android.media.ThumbnailUtils.createVideoThumbnail(
                    path,
                    MediaStore.Images.Thumbnails.MINI_KIND
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    class Factory : Fetcher.Factory<ThumbnailData> {
        override fun create(
            data: ThumbnailData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (data !is ThumbnailData.Video) return null
            return VideoThumbnailFetcher(data, options)
        }
    }
}
