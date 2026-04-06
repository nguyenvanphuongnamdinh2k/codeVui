package com.example.codevui.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import com.example.codevui.model.RecommendCard
import com.example.codevui.model.RecommendFile
import com.example.codevui.model.RecommendType
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * RecommendRepository — cung cấp dữ liệu recommendation cards cho màn hình Quản lý lưu trữ.
 *
 * Bao gồm 4 strategy tương ứng MyFiles:
 *   OLD_MEDIA_FILES    — file ảnh/video/audio > 1 tháng, ngoài Download/Screenshot
 *   UNNECESSARY_FILES — APK files + file nén
 *   SCREENSHOT_FILES  — ảnh chụp màn hình
 *   DOWNLOAD_FILES    — tất cả file trong thư mục Download
 *
 * Architecture: Strategy pattern — mỗi RecommendType có logic query riêng.
 */
class RecommendRepository(private val context: Context) {

    private val log = Logger("RecommendRepository")

    // ══════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════

    /**
     * Lấy thông tin tổng hợp (title, size, count) cho TẤT CẢ card types.
     * Dùng cho StorageManagerScreen — hiển thị nhanh không cần load đầy đủ.
     */
    suspend fun getAllCards(): List<RecommendCard> = withContext(Dispatchers.IO) {
        log.d("getAllCards: start")
        listOf(
            getCardInfo(RecommendType.OLD_MEDIA_FILES),
            getCardInfo(RecommendType.UNNECESSARY_FILES),
            getCardInfo(RecommendType.SCREENSHOT_FILES),
            getCardInfo(RecommendType.DOWNLOAD_FILES)
        ).filter { it.sizeBytes > 0 }
    }

    /**
     * Lấy thông tin tổng hợp cho 1 card type cụ thể.
     */
    suspend fun getCardInfo(type: RecommendType): RecommendCard = withContext(Dispatchers.IO) {
        log.d("getCardInfo: type=$type")
        val (title, description, sizeBytes) = when (type) {
            RecommendType.OLD_MEDIA_FILES -> Triple(
                "File cũ",
                "File ảnh, video, âm thanh bạn đã không xem trong hơn 1 tháng",
                queryOldMediaFilesTotalSize()
            )
            RecommendType.UNNECESSARY_FILES -> Triple(
                "File cài đặt & nén",
                "APK và file nén chiếm dung lượng có thể giải phóng",
                queryUnnecessaryFilesTotalSize()
            )
            RecommendType.SCREENSHOT_FILES -> Triple(
                "Ảnh chụp màn hình",
                "Ảnh chụp màn hình chiếm không gian lưu trữ",
                queryScreenshotFilesTotalSize()
            )
            RecommendType.DOWNLOAD_FILES -> Triple(
                "File đã tải",
                "File đã tải về trong thư mục Tải về",
                queryDownloadFilesTotalSize()
            )
            RecommendType.COMPRESSED_FILES -> Triple(
                "File đã giải nén",
                "File đã được giải nén từ archive",
                0L
            )
        }
        RecommendCard(
            type = type,
            title = title,
            description = description,
            sizeBytes = sizeBytes
        )
    }

    /**
     * Lấy danh sách file cho 1 card type cụ thể.
     * Trả về danh sách file với thông tin đầy đủ.
     */
    suspend fun getCardFiles(type: RecommendType): List<RecommendFile> = withContext(Dispatchers.IO) {
        log.d("getCardFiles: type=$type")
        when (type) {
            RecommendType.OLD_MEDIA_FILES -> queryOldMediaFiles()
            RecommendType.UNNECESSARY_FILES -> queryUnnecessaryFiles()
            RecommendType.SCREENSHOT_FILES -> queryScreenshotFiles()
            RecommendType.DOWNLOAD_FILES -> queryDownloadFiles()
            RecommendType.COMPRESSED_FILES -> emptyList() // Chưa hỗ trợ (cần OperationHistory table)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // OLD_MEDIA_FILES strategy
    // ══════════════════════════════════════════════════════════════

    /**
     * Query file ảnh/video/audio > 1 tháng, ngoài Download/Screenshot.
     * Group theo BUCKET_ID để hiển thị theo thư mục.
     */
    private fun queryOldMediaFilesTotalSize(): Long {
        val selection = buildOldMediaSelection()
        val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
        var total = 0L
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            while (cursor.moveToNext()) {
                total += cursor.getLong(sizeCol)
            }
        }
        return total
    }

    private fun queryOldMediaFiles(): List<RecommendFile> {
        val selection = buildOldMediaSelection()
        return queryMediaFiles(selection, extraSelection = null)
    }

    private fun buildOldMediaSelection(): String {
        val oneMonthAgo = getOneMonthAgoSeconds()
        val screenshotPath = getScreenshotAbsolutePath()
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

        // MIME: image/* OR video/* OR audio/*
        val mediaTypeFilter = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} " +
                "OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO} " +
                "OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO})"

        // Exclude: Download folder, Screenshot folder, Over_the_Horizon.m4a
        val exceptFilter = "(${MediaStore.Files.FileColumns.DATA} NOT LIKE '$downloadDir%') " +
                "AND (${MediaStore.Files.FileColumns.DATA} NOT LIKE '$screenshotPath%') " +
                "AND (${MediaStore.Files.FileColumns.DISPLAY_NAME} != 'Over_the_Horizon.m4a')"

        // IS_TRASHED = 0, VOLUME_NAME = external_primary
        val baseFilter = "${MediaStore.MediaColumns.IS_TRASHED} = 0 AND " +
                "${MediaStore.Files.FileColumns.VOLUME_NAME} = 'external_primary'"

        // DATE_MODIFIED < 1 tháng
        val dateFilter = "${MediaStore.MediaColumns.DATE_MODIFIED} < $oneMonthAgo"

        return "$mediaTypeFilter AND $exceptFilter AND $baseFilter AND $dateFilter"
    }

    // ══════════════════════════════════════════════════════════════
    // UNNECESSARY_FILES strategy (APK + compressed)
    // ══════════════════════════════════════════════════════════════

    private fun queryUnnecessaryFilesTotalSize(): Long {
        val selection = buildUnnecessarySelection()
        val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
        var total = 0L
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            while (cursor.moveToNext()) {
                total += cursor.getLong(sizeCol)
            }
        }
        return total
    }

    private fun queryUnnecessaryFiles(): List<RecommendFile> {
        val selection = buildUnnecessarySelection()
        return queryMediaFiles(selection, extraSelection = null)
    }

    private fun buildUnnecessarySelection(): String {
        val baseFilter = "${MediaStore.MediaColumns.IS_TRASHED} = 0 AND " +
                "${MediaStore.Files.FileColumns.VOLUME_NAME} = 'external_primary'"

        // MIME type: APK (application/vnd.android.package-archive)
        // hoặc compressed (zip/rar/7z/tar/gz)
        val apkMime = "application/vnd.android.package-archive"
        val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "tar.gz", "bz2")

        val mimeFilter = "(${MediaStore.Files.FileColumns.MIME_TYPE} = '$apkMime')"

        // Hoặc lọc theo extension cho các file nén
        val extensionConditions = archiveExtensions.joinToString(" OR ") { ext ->
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.$ext'"
        }

        return "$baseFilter AND ($mimeFilter OR ($extensionConditions))"
    }

    // ══════════════════════════════════════════════════════════════
    // SCREENSHOT_FILES strategy
    // ══════════════════════════════════════════════════════════════

    private fun queryScreenshotFilesTotalSize(): Long {
        val selection = buildScreenshotSelection()
        val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
        var total = 0L
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            while (cursor.moveToNext()) {
                total += cursor.getLong(sizeCol)
            }
        }
        return total
    }

    private fun queryScreenshotFiles(): List<RecommendFile> {
        val selection = buildScreenshotSelection()
        return queryMediaFiles(selection, extraSelection = null)
    }

    private fun buildScreenshotSelection(): String {
        val screenshotPath = getScreenshotAbsolutePath()
        return "${MediaStore.MediaColumns.IS_TRASHED} = 0 AND " +
                "${MediaStore.Files.FileColumns.VOLUME_NAME} = 'external_primary' AND " +
                "${MediaStore.Files.FileColumns.DATA} LIKE '$screenshotPath%'"
    }

    // ══════════════════════════════════════════════════════════════
    // DOWNLOAD_FILES strategy
    // ══════════════════════════════════════════════════════════════

    private fun queryDownloadFilesTotalSize(): Long {
        val selection = buildDownloadSelection()
        val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
        var total = 0L
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            while (cursor.moveToNext()) {
                total += cursor.getLong(sizeCol)
            }
        }
        return total
    }

    private fun queryDownloadFiles(): List<RecommendFile> {
        val selection = buildDownloadSelection()
        return queryMediaFiles(selection, extraSelection = null)
    }

    private fun buildDownloadSelection(): String {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        return "${MediaStore.MediaColumns.IS_TRASHED} = 0 AND " +
                "${MediaStore.Files.FileColumns.VOLUME_NAME} = 'external_primary' AND " +
                "${MediaStore.Files.FileColumns.DATA} LIKE '$downloadDir%'"
    }

    // ══════════════════════════════════════════════════════════════
    // Shared query helpers
    // ══════════════════════════════════════════════════════════════

    private fun queryMediaFiles(
        selection: String,
        extraSelection: String?
    ): List<RecommendFile> {
        val result = mutableListOf<RecommendFile>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.BUCKET_ID
        )

        val finalSelection = if (extraSelection != null) "$selection AND $extraSelection" else selection
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        context.contentResolver.query(uri, projection, finalSelection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val path = cursor.getString(dataCol) ?: continue
                val mime = cursor.getString(mimeCol)
                val dateMod = cursor.getLong(dateCol)
                val bucketId = cursor.getString(bucketCol) ?: ""

                if (size <= 0) continue

                result.add(
                    RecommendFile(
                        name = name,
                        path = path,
                        size = size,
                        dateModified = dateMod,
                        mimeType = mime,
                        bucketId = bucketId,
                        isDirectory = false,
                        uri = ContentUris.withAppendedId(uri, id)
                    )
                )
            }
        }
        return result
    }

    // ══════════════════════════════════════════════════════════════
    // Utility
    // ══════════════════════════════════════════════════════════════

    /** Trả về timestamp (seconds) cách đây 1 tháng, 00:00:00 */
    private fun getOneMonthAgoSeconds(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        calendar[Calendar.HOUR_OF_DAY] = 0
        calendar[Calendar.MINUTE] = 0
        calendar[Calendar.SECOND] = 0
        return calendar.timeInMillis / 1000
    }

    /**
     * Lấy đường dẫn tuyệt đối của thư mục Screenshots.
     * Ưu tiên từ Settings (cho phép user tùy chỉnh), fallback về mặc định.
     */
    private fun getScreenshotAbsolutePath(): String {
        val defaultPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + "/Screenshots"

        val savedPath = try {
            Settings.System.getString(context.contentResolver, "screenshot_current_save_dir")
        } catch (_: Exception) {
            null
        }

        val resolved = savedPath ?: "external_primary:DCIM/Screenshots"
        val parts = resolved.split(":", limit = 2)

        return if (parts.size == 2) {
            val volumeName = parts[0]
            val relativePath = parts[1]
            if (volumeName == "external_primary") {
                Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
            } else {
                "/storage/$volumeName/$relativePath"
            }
        } else {
            defaultPath
        }
    }
}
