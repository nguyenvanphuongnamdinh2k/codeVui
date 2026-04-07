package com.example.codevui.ui.thumbnail

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import coil.ImageLoader
import coil.fetch.Fetcher
import coil.request.Options
import java.io.FileDescriptor

/**
 * ThumbnailFetcher cho audio files.
 * Extract embedded artwork từ audio file metadata.
 * Hỗ trợ cả content:// Uri và file:// Uri.
 */
class AudioThumbnailFetcher(
    private val data: ThumbnailData.Audio,
    options: Options
) : ThumbnailFetcher(options) {

    override suspend fun extractBitmap(): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                when {
                    data.uri.scheme == "file" -> {
                        // file:// Uri → dùng path
                        val path = data.uri.path ?: return null
                        retriever.setDataSource(path)
                    }
                    data.uri.scheme == "content" -> {
                        // content:// Uri → mở ParcelFileDescriptor
                        val pfd = options.context.contentResolver.openFileDescriptor(data.uri, "r")
                        if (pfd != null) {
                            retriever.setDataSource(pfd.fileDescriptor)
                            pfd.close()
                        } else {
                            // Fallback: thử resolve path từ Uri
                            resolvePath(options.context, data.uri)?.let { retriever.setDataSource(it) }
                                ?: return null
                        }
                    }
                    else -> {
                        // Fallback cuối: dùng path trực tiếp
                        if (data.path.isNotEmpty()) {
                            retriever.setDataSource(data.path)
                        } else return null
                    }
                }
            } catch (e: Exception) {
                // Fallback: thử dùng path trực tiếp
                if (data.path.isNotEmpty()) {
                    retriever.setDataSource(data.path)
                } else return null
            }

            val artBytes = retriever.embeddedPicture
            retriever.release()
            if (artBytes != null) {
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve file path từ content:// Uri.
     * Cần quyền truy cập file.
     */
    private fun resolvePath(context: android.content.Context, uri: android.net.Uri): String? {
        // Thử lấy path từ cursor
        val projection = arrayOf(android.provider.MediaStore.Files.FileColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
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
            if (data !is ThumbnailData.Audio) return null
            return AudioThumbnailFetcher(data, options)
        }
    }
}
