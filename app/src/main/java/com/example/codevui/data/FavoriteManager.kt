package com.example.codevui.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import com.example.codevui.data.db.AppDatabase
import com.example.codevui.data.db.FavoriteDao
import com.example.codevui.data.db.FavoriteEntity
import com.example.codevui.model.FavoriteItem
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * FavoriteManager — singleton quản lý yêu thích (thêm, xóa, toggle, kiểm tra).
 *
 * Architecture:
 *   - Dùng Room DB để persist favorites
 *   - Flow observer → tự động cập nhật UI khi có thay đổi
 *   - Validation lazy: kiểm tra file tồn tại khi mở favorites
 *   - Reorder: cập nhật sortOrder sau khi kéo thả
 *
 * MyFiles behavior được replicate:
 *   - Thêm vào favorites: Broadcast → reload
 *   - Xóa khỏi favorites: Broadcast → reload
 *   - Reorder favorites: persist sortOrder
 *   - Validation: xóa favorites không tồn tại
 */
object FavoriteManager {

    private val log = Logger("FavoriteManager")

    private const val MAX_FAVORITES = 1000

    private var dao: FavoriteDao? = null

    private fun getDao(context: Context): FavoriteDao {
        if (dao == null) {
            dao = AppDatabase.getInstance(context).favoriteDao()
        }
        return dao!!
    }

    // ══════════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════════

    /**
     * Lấy Flow của danh sách yêu thích (tự động update khi DB thay đổi).
     */
    fun observeFavorites(context: Context): Flow<List<FavoriteItem>> {
        return getDao(context).getAllFavorites().map { entities ->
            entities.map { it.toFavoriteItem(context) }
        }
    }

    /**
     * Lấy danh sách yêu thích (non-flow).
     */
    suspend fun getFavorites(context: Context): List<FavoriteItem> = withContext(Dispatchers.IO) {
        getDao(context).getAllFavoritesList().map { it.toFavoriteItem(context) }
    }

    /**
     * Kiểm tra xem 1 path có trong favorites chưa.
     */
    suspend fun isFavorite(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        getDao(context).isFavorite(path)
    }

    /**
     * Thêm 1 file/folder vào favorites.
     * @return true = thành công, false = thất bại (đã tồn tại hoặc quá limit)
     */
    suspend fun addFavorite(
        context: Context,
        path: String,
        name: String,
        size: Long,
        mimeType: String?,
        isDirectory: Boolean,
        dateModified: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentCount = getDao(context).getFavoritesCount()
            if (currentCount >= MAX_FAVORITES) {
                log.w("addFavorite: limit reached ($MAX_FAVORITES)")
                return@withContext false
            }

            val fileId = hashPath(path)
            val sortOrder = getDao(context).getMaxSortOrder() + 1
            val addedAt = System.currentTimeMillis() / 1000

            val entity = FavoriteEntity(
                fileId = fileId,
                name = name,
                path = path,
                size = size,
                mimeType = mimeType,
                isDirectory = isDirectory,
                dateModified = dateModified,
                addedAt = addedAt,
                sortOrder = sortOrder
            )

            val result = getDao(context).insert(entity)
            log.d("addFavorite: path=$path, result=$result")
            result > 0
        } catch (e: Exception) {
            log.e("addFavorite: failed for path=$path", e)
            false
        }
    }

    /**
     * Xóa 1 file/folder khỏi favorites.
     * @return true = thành công
     */
    suspend fun removeFavorite(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = getDao(context).deleteByPath(path)
            log.d("removeFavorite: path=$path, deleted=$deleted")
            deleted > 0
        } catch (e: Exception) {
            log.e("removeFavorite: failed for path=$path", e)
            false
        }
    }

    /**
     * Toggle yêu thích — thêm nếu chưa có, xóa nếu đã có.
     * @return true = thêm thành công, false = xóa thành công
     */
    suspend fun toggleFavorite(
        context: Context,
        path: String,
        name: String,
        size: Long,
        mimeType: String?,
        isDirectory: Boolean,
        dateModified: Long
    ): Boolean {
        val alreadyFavorite = isFavorite(context, path)
        return if (alreadyFavorite) {
            removeFavorite(context, path)
            false
        } else {
            addFavorite(context, path, name, size, mimeType, isDirectory, dateModified)
        }
    }

    /**
     * Xóa nhiều file khỏi favorites.
     */
    suspend fun removeFavorites(context: Context, paths: List<String>): Int = withContext(Dispatchers.IO) {
        try {
            val deleted = getDao(context).deleteByPaths(paths)
            log.d("removeFavorites: ${paths.size} paths, deleted=$deleted")
            deleted
        } catch (e: Exception) {
            log.e("removeFavorites: failed", e)
            0
        }
    }

    /**
     * Reorder favorites — cập nhật sortOrder sau khi kéo thả.
     */
    suspend fun reorderFavorites(context: Context, orderedPaths: List<String>) = withContext(Dispatchers.IO) {
        try {
            orderedPaths.forEachIndexed { index, path ->
                val entity = getDao(context).getFavoriteByPath(path)
                entity?.let {
                    getDao(context).updateSortOrder(it.fileId, index)
                }
            }
            log.d("reorderFavorites: ${orderedPaths.size} items reordered")
        } catch (e: Exception) {
            log.e("reorderFavorites: failed", e)
        }
    }

    /**
     * Xóa favorites mà file không còn tồn tại trên hệ thống.
     * Gọi khi mở FavoritesScreen — validation lazy.
     */
    suspend fun validateFavorites(context: Context): Int = withContext(Dispatchers.IO) {
        val favorites = getDao(context).getAllFavoritesList()
        var removed = 0

        for (entity in favorites) {
            val file = File(entity.path)
            if (!file.exists()) {
                val deleted = getDao(context).deleteById(entity.fileId)
                removed += deleted
                log.d("validateFavorites: removed invalid favorite — ${entity.path}")
            }
        }

        if (removed > 0) {
            log.d("validateFavorites: total removed=$removed")
        }
        removed
    }

    /**
     * Cập nhật thông tin file (khi file bị rename hoặc move).
     */
    suspend fun updateFileInfo(
        context: Context,
        oldPath: String,
        newName: String,
        newPath: String,
        newSize: Long,
        newDateModified: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val entity = getDao(context).getFavoriteByPath(oldPath)
            entity?.let {
                getDao(context).updateFileInfo(
                    fileId = it.fileId,
                    name = newName,
                    path = newPath,
                    size = newSize,
                    dateModified = newDateModified
                )
                log.d("updateFileInfo: oldPath=$oldPath → newPath=$newPath")
            }
        } catch (e: Exception) {
            log.e("updateFileInfo: failed", e)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════

    /** Hash path thành short ID (SHA-256, lấy đủ ký tự để làm primary key) */
    private fun hashPath(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(path.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    /** Convert Entity → Domain model, đồng thời resolve ContentUri cho thumbnail */
    private fun FavoriteEntity.toFavoriteItem(context: Context): FavoriteItem {
        // Resolve content URI và MIME type (nếu stored value là null)
        val (contentUri, resolvedMimeType) = if (!isDirectory) {
            val uri = resolveContentUri(context, path)
            val storedMime = mimeType
            val mime = storedMime ?: resolveMimeType(path)
            log.d("toFavoriteItem: path=$path, storedMime=$storedMime, resolvedMime=$mime, uri=$uri")
            uri to mime
        } else {
            Uri.EMPTY to null
        }
        return FavoriteItem(
            fileId = fileId,
            name = name,
            path = path,
            size = size,
            mimeType = resolvedMimeType,
            isDirectory = isDirectory,
            dateModified = dateModified,
            addedAt = addedAt,
            sortOrder = sortOrder,
            uri = contentUri
        )
    }

    /**
     * Resolve MediaStore content URI từ path.
     * Cần cho thumbnail loading.
     */
    private fun resolveContentUri(context: Context, path: String): Uri {
        return try {
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
            val uri = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                arrayOf(path),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    MediaStore.Files.getContentUri("external").buildUpon()
                        .appendPath(id.toString())
                        .build()
                } else {
                    log.w("resolveContentUri: no MediaStore entry for path=$path")
                    Uri.EMPTY
                }
            } ?: Uri.EMPTY
            log.d("resolveContentUri: path=$path → $uri")
            uri
        } catch (e: Exception) {
            log.e("resolveContentUri: failed for path=$path", e)
            Uri.EMPTY
        }
    }

    /**
     * Resolve MIME type từ file extension.
     * Dùng khi mimeType trong DB là null.
     */
    private fun resolveMimeType(path: String): String? {
        return try {
            val ext = path.substringAfterLast('.', "").lowercase()
            val mime = when (ext) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif" -> "image/jpeg"
                "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm" -> "video/mp4"
                "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> "audio/mpeg"
                "apk" -> "application/vnd.android.package-archive"
                "zip", "rar", "7z", "tar", "gz" -> "application/zip"
                "pdf" -> "application/pdf"
                "doc", "docx" -> "application/msword"
                "xls", "xlsx" -> "application/vnd.ms-excel"
                "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                "txt", "log", "md", "json", "xml", "csv" -> "text/plain"
                else -> null
            }
            log.d("resolveMimeType: path=$path, ext=$ext, mime=$mime")
            mime
        } catch (_: Exception) {
            log.e("resolveMimeType: failed for path=$path")
            null
        }
    }
}
