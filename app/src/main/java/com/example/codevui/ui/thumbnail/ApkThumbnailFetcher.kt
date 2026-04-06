package com.example.codevui.ui.thumbnail

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse

/**
 * ThumbnailFetcher cho APK files.
 * Extract icon từ APK package.
 */
class ApkThumbnailFetcher(
    private val data: ThumbnailData.Apk,
    options: Options
) : ThumbnailFetcher(options) {

    override suspend fun extractBitmap(): Bitmap? {
        return extractApkIcon(options.context)
    }

    private fun extractApkIcon(context: Context): Bitmap? {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(data.path, 0) ?: return null
            info.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = data.path
                appInfo.publicSourceDir = data.path
                val drawable = appInfo.loadIcon(pm)
                val size = options.size.width.pxOrElse { 128 }
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bitmap
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
            if (data !is ThumbnailData.Apk) return null
            return ApkThumbnailFetcher(data, options)
        }
    }
}