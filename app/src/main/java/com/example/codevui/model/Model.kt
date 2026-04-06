package com.example.codevui.model

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryItem(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val label: String,
    val fileType: FileType
)

data class StorageItem(
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val title: String,
    val subtitle: String? = null,
    val used: String? = null,
    val total: String? = null
)

data class RecentFile(
    val name: String,
    val date: String,
    val type: FileType,
    val uri: Uri = Uri.EMPTY,
    val path: String = "",
    val size: Long = 0L,
    val dateModified: Long = 0L,
    val ownerApp: String? = null  // app label that created this file
)

/**
 * FavoriteItem — 1 file/folder trong danh sách yêu thích.
 * Dùng trong FavoritesScreen và HomeScreen.
 */
data class FavoriteItem(
    val fileId: String,        // SHA-256 hash của path
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String?,
    val isDirectory: Boolean,
    val dateModified: Long,    // epoch seconds
    val addedAt: Long,        // epoch seconds
    val sortOrder: Int,
    val uri: Uri = Uri.EMPTY  // Content Uri cho thumbnail
)

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long
) {
    val freeBytes: Long get() = totalBytes - usedBytes
}

enum class FileType(val mimePrefix: String) {
    IMAGE("image/"),
    VIDEO("video/"),
    AUDIO("audio/"),
    DOC(""),
    APK("application/vnd.android.package-archive"),
    ARCHIVE(""),
    DOWNLOAD(""),
    OTHER("");

    companion object {
        private val ARCHIVE_MIMES = setOf(
            "application/zip", "application/x-zip-compressed",
            "application/x-rar-compressed", "application/vnd.rar",
            "application/x-7z-compressed", "application/x-tar",
            "application/gzip", "application/x-gzip"
        )

        private val ARCHIVE_EXTENSIONS = setOf(
            "zip", "rar", "7z", "tar", "gz", "tgz", "tar.gz", "bz2"
        )

        fun fromMimeType(mimeType: String?): FileType = when {
            mimeType == null -> OTHER
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("video/") -> VIDEO
            mimeType.startsWith("audio/") -> AUDIO
            mimeType == "application/vnd.android.package-archive" -> APK
            mimeType in ARCHIVE_MIMES -> ARCHIVE
            mimeType.startsWith("application/") || mimeType.startsWith("text/") -> DOC
            else -> OTHER
        }

        fun fromExtension(ext: String): Boolean =
            ext.lowercase() in ARCHIVE_EXTENSIONS
    }
}

data class FolderItem(
    val name: String,
    val path: String,
    val dateModified: Long = 0L,
    val itemCount: Int = 0,
    val isPinned: Boolean = false
)

/**
 * ArchiveEntry — 1 item bên trong file nén
 */
data class ArchiveEntry(
    val name: String,
    val path: String,        // full path bên trong archive (e.g. "folder/file.txt")
    val size: Long = 0L,
    val compressedSize: Long = 0L,
    val isDirectory: Boolean = false,
    val lastModified: Long = 0L
)

/**
 * DuplicateGroup — nhóm file trùng lặp (cùng name + size)
 */
data class DuplicateGroup(
    val hash: String,              // content hash để identify nhóm
    val size: Long,               // kích thước file (tất cả giống nhau)
    val items: List<DuplicateItem>  // danh sách các file trùng
) {
    val wastedBytes: Long get() = size * (items.size - 1)
}

/**
 * DuplicateItem — 1 file trong nhóm trùng lặp
 */
data class DuplicateItem(
    val file: RecentFile,
    val isOriginal: Boolean,       // file gốc (cũ nhất theo dateModified)
    val parentFolder: String       // thư mục chứa (VD: "/Download/")
)

/**
 * RecommendCard — thông tin tổng hợp cho 1 loại recommendation card (hiển thị trong StorageManager)
 */
data class RecommendCard(
    val type: RecommendType,
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val fileCount: Int = 0,
    val iconRes: Int? = null  // null = dùng icon mặc định theo type
)

/**
 * RecommendFile — 1 file trong danh sách recommendation của 1 card type
 */
data class RecommendFile(
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String?,
    val bucketId: String,
    val isDirectory: Boolean = false,
    val uri: Uri = Uri.EMPTY  // Content Uri cho thumbnail loading
)

/**
 * Các loại recommendation card (đồng nhất với MyFiles RecommendConstants)
 */
enum class RecommendType(val value: Int) {
    OLD_MEDIA_FILES(0),    // File ảnh/video/audio > 1 tháng, ngoài thư mục mặc định
    UNNECESSARY_FILES(1),  // APK + file nén
    SCREENSHOT_FILES(2),   // Ảnh chụp màn hình
    DOWNLOAD_FILES(3),     // File trong thư mục Download
    COMPRESSED_FILES(8);  // File đã giải nén hoàn toàn (từ operation history)

    companion object {
        fun fromValue(value: Int): RecommendType = entries.first { it.value == value }
    }
}
