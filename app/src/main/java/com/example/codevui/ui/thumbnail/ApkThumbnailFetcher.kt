package com.example.codevui.ui.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.provider.OpenableColumns
import coil.ImageLoader
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse

/**
 * ThumbnailFetcher cho APK files.
 * Extract icon từ APK package.
 * Hỗ trợ cả file:// Uri và content:// Uri.
 */
class ApkThumbnailFetcher(
    private val data: ThumbnailData.Apk,
    options: Options
) : ThumbnailFetcher(options) {

    override suspend fun extractBitmap(): Bitmap? {
        return extractApkIcon(options.context)
    }

    private fun extractApkIcon(context: Context): Bitmap? {
        val apkPath: String

        // Resolve APK path
        when {
            data.uri.scheme == "file" -> {
                apkPath = data.uri.path ?: return null
            }
            data.uri.scheme == "content" -> {
                // content:// Uri → lấy display name rồi resolve path
                val name = getFileName(context, data.uri) ?: return null
                // Thử tìm APK trong các thư mục đã biết
                apkPath = findApkPath(context, name) ?: return null
            }
            data.path.isNotEmpty() -> {
                apkPath = data.path
            }
            else -> {
                return null
            }
        }

        return try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkPath, 0) ?: return null
            info.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = apkPath
                appInfo.publicSourceDir = apkPath
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

    /**
     * Get file name từ content:// Uri.
     */
    private fun getFileName(context: Context, uri: android.net.Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else null
        }
    }

    /**
     * Tìm APK path từ file name.
     * Chỉ hoạt động với MANAGE_EXTERNAL_STORAGE.
     */
    private fun findApkPath(context: Context, fileName: String): String? {
        val projection = arrayOf(android.provider.MediaStore.Files.FileColumns.DATA)
        val selection = "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        context.contentResolver.query(
            android.provider.MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            arrayOf(fileName, "application/vnd.android.package-archive"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val colIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA)
                return cursor.getString(colIndex)
            }
        }
        return null
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