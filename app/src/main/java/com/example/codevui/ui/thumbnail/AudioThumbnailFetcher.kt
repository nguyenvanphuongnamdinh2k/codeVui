package com.example.codevui.ui.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.fetch.Fetcher
import coil.request.Options

/**
 * ThumbnailFetcher cho audio files.
 * Extract embedded artwork từ audio file metadata.
 */
class AudioThumbnailFetcher(
    private val data: ThumbnailData.Audio,
    options: Options
) : ThumbnailFetcher(options) {

    override suspend fun extractBitmap(): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(options.context, data.uri)
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
