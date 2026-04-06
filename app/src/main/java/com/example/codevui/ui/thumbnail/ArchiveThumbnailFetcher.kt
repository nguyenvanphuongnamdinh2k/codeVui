package com.example.codevui.ui.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import coil.ImageLoader
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File

/**
 * ThumbnailFetcher cho archive files (ZIP, RAR, 7z, etc.)
 * Extract image thumbnail từ archive entry đầu tiên.
 */
class ArchiveThumbnailFetcher(
    private val data: ThumbnailData.Archive,
    options: Options
) : ThumbnailFetcher(options) {

    override suspend fun extractBitmap(): Bitmap? {
        val tempDir = File(options.context.cacheDir, "archive_thumbs")
        val tempFile = extractToTemp(
            archivePath = data.path,
            entryPath = data.entryPath,
            tempDir = tempDir,
            password = data.password
        ) ?: return null

        return try {
            BitmapFactory.decodeFile(tempFile.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractToTemp(
        archivePath: String,
        entryPath: String,
        tempDir: File,
        password: String?
    ): File? {
        return try {
            com.example.codevui.data.ArchiveReader.extractToTemp(
                archivePath = archivePath,
                entryPath = entryPath,
                tempDir = tempDir,
                password = password
            )
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
            if (data !is ThumbnailData.Archive) return null
            return ArchiveThumbnailFetcher(data, options)
        }
    }
}
