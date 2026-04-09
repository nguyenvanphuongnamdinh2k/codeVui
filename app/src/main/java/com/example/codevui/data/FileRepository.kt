package com.example.codevui.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.LruCache
import android.webkit.MimeTypeMap
import com.example.codevui.model.FileType
import com.example.codevui.model.DuplicateGroup
import com.example.codevui.model.DuplicateItem
import com.example.codevui.model.FolderItem
import com.example.codevui.model.RecentFile
import com.example.codevui.model.StorageInfo
import com.example.codevui.util.Logger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.suspendCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Dữ liệu chi tiết cho màn hình Quản lý lưu trữ
 */
data class StorageAnalysis(
    val videoBytes: Long = 0L,
    val imageBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val archiveBytes: Long = 0L,
    val apkBytes: Long = 0L,
    val docBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val trashBytes: Long = 0L,
    val unusedAppsBytes: Long = 0L,
    val duplicateBytes: Long = 0L,
    val largeFilesBytes: Long = 0L,
    val oldScreenshotsBytes: Long = 0L,
    val appsBytes: Long = 0L   // Ứng dụng (APK + data + cache) — từ PackageManager
)

class FileRepository(private val context: Context) {

    private val log = Logger("FileRepository")

    /**
     * Public wrapper cho correctionStorageSize — dùng cho StorageVolumeManager.
     */
    companion object {
        fun correctStorageSize(rawTotalBytes: Long): Long {
            val DISPLAY_UNIT_GB = 1_000_000_000L
            val rootDirPath = Environment.getRootDirectory().path
            val rootStat = StatFs(rootDirPath)
            val realTotalSize = rawTotalBytes + rootStat.totalBytes

            var power = 2
            var tempTotalSize: Long
            do {
                val powOfTwo = 1L shl power
                tempTotalSize = DISPLAY_UNIT_GB * powOfTwo
                power++
            } while (realTotalSize > tempTotalSize && power < 63)
            return tempTotalSize
        }
    }

    /**
     * Essential folders displayed in "Cần thiết" filter mode
     */
    private val essentialFolderNames = setOf(
        "DCIM", "Pictures", "Download", "Documents",
        "Android", "Alarms", "Music", "Movies", "Podcasts",
        "Ringtones", "Notifications", "Audiobooks"
    )

    /**
     * Check if a folder name is considered "essential" (Cần thiết)
     */
    fun isEssentialFolder(folderName: String): Boolean =
        folderName in essentialFolderNames

    /**
     * Filter folders to only essential ones
     */
    fun filterEssentialFolders(folders: List<FolderItem>): List<FolderItem> =
        folders.filter { it.name in essentialFolderNames }

    /**
     * Query recent files từ MediaStore, sắp xếp theo date_modified DESC
     */
    fun getRecentFiles(limit: Int = 20): List<RecentFile> {
        val files = mutableListOf<RecentFile>()

        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )

        // OWNER_PACKAGE_NAME available on Android 10+ (API 29)
        val hasOwner = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        if (hasOwner) {
            projection.add(MediaStore.Files.FileColumns.OWNER_PACKAGE_NAME)
        }

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val uri = MediaStore.Files.getContentUri("external")

        // Chỉ lấy file, bỏ folder (folder có mime_type = null)
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL"

        context.contentResolver.query(
            uri, projection.toTypedArray(), selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val ownerCol = if (hasOwner) cursor.getColumnIndex(MediaStore.Files.FileColumns.OWNER_PACKAGE_NAME) else -1

            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val mimeType = cursor.getString(mimeCol)
                val size = cursor.getLong(sizeCol)
                val dateModified = cursor.getLong(dateCol)
                val path = cursor.getString(dataCol) ?: ""
                val ownerPkg = if (ownerCol >= 0) cursor.getString(ownerCol) else null

                // Skip empty/hidden files
                if (size <= 0 || name.startsWith(".")) continue

                val fileType = FileType.fromMimeType(mimeType)
                val contentUri = ContentUris.withAppendedId(uri, id)

                // Resolve package name → app label
                val appLabel = ownerPkg?.let { resolveAppLabel(it) }
                    ?: resolveAppFromPath(path)

                files.add(
                    RecentFile(
                        name = name,
                        date = formatRelativeDate(dateModified),
                        type = fileType,
                        uri = contentUri,
                        path = path,
                        size = size,
                        dateModified = dateModified,
                        ownerApp = appLabel
                    )
                )
                count++
            }
        }
        return files
    }

    /**
     * Resolve package name → app label via PackageManager
     * LruCache với maxSize = 100 entries để tránh memory leak
     */
    private val appLabelCache = LruCache<String, String?>(100)

    private fun resolveAppLabel(packageName: String): String? {
        // Check cache first
        appLabelCache.get(packageName)?.let { return it }

        // Query PackageManager nếu chưa có trong cache
        val label = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }

        // Store in cache
        appLabelCache.put(packageName, label)
        return label
    }

    /**
     * Fallback: resolve app name from file path folder names
     */
    private fun resolveAppFromPath(path: String): String? {
        val parts = path.split("/")
        val knownFolders = mapOf(
            "Claude" to "Claude",
            "Telegram" to "Telegram",
            "Messenger" to "Messenger",
            "Zalo" to "Zalo",
            "WhatsApp" to "WhatsApp",
            "Facebook" to "Facebook",
            "Instagram" to "Instagram",
            "TikTok" to "TikTok",
            "Samsung Notes" to "Samsung Notes",
            "SamsungNotes" to "Samsung Notes",
            "Screenshots" to "Screenshots",
            "Camera" to "Máy ảnh"
        )
        for (part in parts) {
            knownFolders[part]?.let { return it }
        }
        return null
    }

    /**
     * Đếm số file theo từng FileType
     */
    fun getFileCount(fileType: FileType): Int {
        val uri = MediaStore.Files.getContentUri("external")
        val selection = buildSelection(fileType) ?: return 0

        context.contentResolver.query(
            uri,
            arrayOf("COUNT(*) AS count"),
            selection,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return 0
    }

    /**
     * Lấy thông tin dung lượng bộ nhớ trong.
     * Dùng getExternalStorageDirectory() — đồng nhất với MyFiles.
     * KHÔNG dùng getDataDirectory() vì nó chỉ trả về partition /data (~223 GB),
     * trong khi MyFiles dùng /storage/emulated/0 (~256 GB).
     */
    /**
     * Lấy raw partition total bytes (không qua correction).
     * Dùng cho systemBytes calculation để tránh âm.
     */
    fun getRawInternalStorageTotalBytes(): Long {
        val path = Environment.getExternalStorageDirectory().absolutePath
        return try {
            StatFs(path).totalBytes
        } catch (e: Exception) {
            0L
        }
    }

    fun getInternalStorageInfo(): StorageInfo {
        val path = Environment.getExternalStorageDirectory().absolutePath
        log.d("getInternalStorageInfo: path=$path")

        // MyFiles dùng correctionStorageSize() để round partition size
        // lên advertised capacity (VD: partition 223 GB → hiển thị 256 GB)
        val stat = StatFs(path)
        val rawTotal = stat.totalBytes
        val freeBytes = stat.availableBytes
        val correctedTotal = correctionStorageSize(rawTotal)

        log.d("getInternalStorageInfo: rawTotal=$rawTotal (${rawTotal / 1_000_000_000.0} GB / ${rawTotal / 1073741824.0} GiB), correctedTotal=$correctedTotal (${correctedTotal / 1_000_000_000.0} GB), freeBytes=$freeBytes (${freeBytes / 1_000_000_000.0} GB)")
        return StorageInfo(
            totalBytes = correctedTotal,
            usedBytes = correctedTotal - freeBytes
        )
    }

    /**
     * MyFiles correctionStorageSize():
     * Raw partition size (VD: 239 GB = 223 GiB) → advertised capacity (256 GB).
     * Round up partition size lên nearest advertised tier (8/16/32/64/128/256/512 GB...).
     */
    private fun correctionStorageSize(totalSize: Long): Long {
        val DISPLAY_UNIT_GB = 1_000_000_000L // 1 GB = 1000³ bytes (manufacturer advertised)
        val rootDirPath = Environment.getRootDirectory().path
        val rootStat = StatFs(rootDirPath)
        val realTotalSize = totalSize + rootStat.totalBytes
        log.d("correctionStorageSize: totalSize=$totalSize, rootDirBytes=${rootStat.totalBytes}, realTotalSize=$realTotalSize")

        var power = 2
        var tempTotalSize: Long
        do {
            val powOfTwo = 1L shl power
            tempTotalSize = DISPLAY_UNIT_GB * powOfTwo
            power++
        } while (realTotalSize > tempTotalSize && power < 63)

        log.d("correctionStorageSize: result=$tempTotalSize (${tempTotalSize / 1000000000.0} GB advertised)")
        return tempTotalSize
    }

    /**
     * Query tất cả file theo FileType, sort theo date_modified DESC
     */
    fun getFilesByType(
        fileType: FileType,
        sortBy: SortBy = SortBy.DATE,
        ascending: Boolean = false
    ): List<RecentFile> {
        val files = mutableListOf<RecentFile>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )

        val selection = buildSelection(fileType)
        if (selection == null) return files

        val sortColumn = when (sortBy) {
            SortBy.DATE -> MediaStore.Files.FileColumns.DATE_MODIFIED
            SortBy.NAME -> MediaStore.Files.FileColumns.DISPLAY_NAME
            SortBy.SIZE -> MediaStore.Files.FileColumns.SIZE
        }
        val sortDirection = if (ascending) "ASC" else "DESC"
        val sortOrder = "$sortColumn $sortDirection"

        val uri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(
            uri, projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val mimeType = cursor.getString(mimeCol)
                val size = cursor.getLong(sizeCol)
                val dateModified = cursor.getLong(dateCol)
                val path = cursor.getString(dataCol) ?: ""

                if (size <= 0 || name.startsWith(".")) continue

                val actualType = FileType.fromMimeType(mimeType)
                val contentUri = ContentUris.withAppendedId(uri, id)

                files.add(
                    RecentFile(
                        name = name,
                        date = formatRelativeDate(dateModified),
                        type = actualType,
                        uri = contentUri,
                        path = path,
                        size = size,
                        dateModified = dateModified
                    )
                )
            }
        }
        return files
    }

    /**
     * Tính tổng size của tất cả file theo type
     */
    fun getTotalSizeByType(fileType: FileType): Long {
        val uri = MediaStore.Files.getContentUri("external")
        val selection = buildSelection(fileType) ?: return 0L

        var total = 0L
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Files.FileColumns.SIZE),
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

    /**
     * Liệt kê folders + files trong 1 directory path
     * Trả về Pair(folders, files)
     */
    fun listDirectory(
        dirPath: String,
        sortBy: SortBy = SortBy.NAME,
        ascending: Boolean = true
    ): Pair<List<FolderItem>, List<RecentFile>> {
        val dir = java.io.File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return Pair(emptyList(), emptyList())

        val children = dir.listFiles() ?: return Pair(emptyList(), emptyList())

        val folders = mutableListOf<FolderItem>()
        val files = mutableListOf<RecentFile>()

        // Pinned folders (DCIM, Download) hiển thị trên đầu
        val pinnedNames = setOf("DCIM", "Download")

        for (child in children) {
            if (child.name.startsWith(".")) continue // skip hidden

            if (child.isDirectory) {
                val itemCount = child.listFiles()?.size ?: 0
                folders.add(
                    FolderItem(
                        name = child.name,
                        path = child.absolutePath,
                        dateModified = child.lastModified() / 1000,
                        itemCount = itemCount,
                        isPinned = child.name in pinnedNames
                    )
                )
            } else {
                if (child.length() < 0) continue
                val mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(child.extension.lowercase())
                val fileType = FileType.fromMimeType(mimeType)
                val contentUri = getContentUri(child.absolutePath)

                files.add(
                    RecentFile(
                        name = child.name,
                        date = formatRelativeDate(child.lastModified() / 1000),
                        type = fileType,
                        uri = contentUri,
                        path = child.absolutePath,
                        size = child.length(),
                        dateModified = child.lastModified() / 1000
                    )
                )
            }
        }

        // Sort folders: pinned first, then by sortBy
        val sortedFolders = folders.sortedWith(
            compareByDescending<FolderItem> { it.isPinned }
                .then(
                    when (sortBy) {
                        SortBy.NAME -> if (ascending) compareBy { it.name.lowercase() }
                        else compareByDescending { it.name.lowercase() }
                        SortBy.DATE -> if (ascending) compareBy { it.dateModified }
                        else compareByDescending { it.dateModified }
                        SortBy.SIZE -> if (ascending) compareBy { it.itemCount }
                        else compareByDescending { it.itemCount }
                    }
                )
        )

        // Sort files
        val sortedFiles = when (sortBy) {
            SortBy.NAME -> if (ascending) files.sortedBy { it.name.lowercase() }
            else files.sortedByDescending { it.name.lowercase() }
            SortBy.DATE -> if (ascending) files.sortedBy { it.dateModified }
            else files.sortedByDescending { it.dateModified }
            SortBy.SIZE -> if (ascending) files.sortedBy { it.size }
            else files.sortedByDescending { it.size }
        }

        return Pair(sortedFolders, sortedFiles)
    }

    /**
     * Lấy content URI từ file path qua MediaStore
     */
    private fun getContentUri(filePath: String): Uri {
        val uri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?",
            arrayOf(filePath),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(uri, id)
            }
        }
        return Uri.EMPTY
    }

    private fun buildSelection(fileType: FileType): String? = when (fileType) {
        FileType.IMAGE -> "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%'"
        FileType.VIDEO -> "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%'"
        FileType.AUDIO -> "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'audio/%'"
        FileType.APK -> "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.android.package-archive'"
        FileType.DOC -> buildString {
            append("(")
            append("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/pdf'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/msword'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/vnd.openxmlformats%'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'text/%'")
            append(")")
        }
        FileType.DOWNLOAD -> {
            val downloadPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ).absolutePath
            "${MediaStore.Files.FileColumns.DATA} LIKE '$downloadPath%'"
        }
        FileType.OTHER -> null
        FileType.ARCHIVE -> buildString {
            append("(")
            append("${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/zip'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/x-zip-compressed'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/x-rar-compressed'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.rar'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/x-7z-compressed'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/x-tar'")
            append(" OR ${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/gzip'")
            append(")")
        }
    }

    enum class SortBy { DATE, NAME, SIZE }

    /**
     * Search files by name, with optional fileType and time range filters
     * Includes both files and folders when no type filter applied
     */
    fun searchFiles(
        query: String,
        fileTypes: Set<FileType>? = null,
        afterTimestamp: Long? = null // epoch seconds
    ): List<RecentFile> {
        val files = mutableListOf<RecentFile>()
        if (query.isBlank() && fileTypes == null && afterTimestamp == null) return files

        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA
        )

        val conditions = mutableListOf<String>()

        // Only files (has mime_type)
        conditions.add("${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL")

        // Name search
        if (query.isNotBlank()) {
            conditions.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%${query.replace("'", "''")}%'")
        }

        // File type filter
        if (fileTypes != null && fileTypes.isNotEmpty()) {
            val typeConditions = fileTypes.mapNotNull { buildSelection(it) }
            if (typeConditions.isNotEmpty()) {
                conditions.add("(${typeConditions.joinToString(" OR ")})")
            }
        }

        // Time filter
        if (afterTimestamp != null) {
            conditions.add("${MediaStore.Files.FileColumns.DATE_MODIFIED} >= $afterTimestamp")
        }

        val selection = conditions.joinToString(" AND ")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val uri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(
            uri, projection.toTypedArray(), selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val mimeType = cursor.getString(mimeCol)
                val size = cursor.getLong(sizeCol)
                val dateModified = cursor.getLong(dateCol)
                val path = cursor.getString(dataCol) ?: ""

                if (size <= 0 || name.startsWith(".")) continue

                val fileType = FileType.fromMimeType(mimeType)
                val contentUri = ContentUris.withAppendedId(uri, id)

                files.add(
                    RecentFile(
                        name = name,
                        date = formatRelativeDate(dateModified),
                        type = fileType,
                        uri = contentUri,
                        path = path,
                        size = size,
                        dateModified = dateModified
                    )
                )
            }
        }
        return files
    }

    /**
     * Search folders by name in external storage
     * DEPRECATED: Method này không nên dùng trực tiếp vì chạy blocking I/O
     * Sử dụng searchFoldersSuspend() thay thế để có cancellation support
     */
    @Deprecated(
        "Use searchFoldersSuspend() instead for better performance and cancellation support",
        ReplaceWith("searchFoldersSuspend(query)")
    )
    fun searchFolders(query: String): List<FolderItem> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<FolderItem>()
        val root = Environment.getExternalStorageDirectory()

        fun searchRecursive(dir: java.io.File, depth: Int) {
            if (depth > 5) return // limit depth
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (!child.isDirectory) continue
                if (child.name.startsWith(".")) continue

                if (child.name.contains(query, ignoreCase = true)) {
                    results.add(
                        FolderItem(
                            name = child.name,
                            path = child.absolutePath,
                            dateModified = child.lastModified() / 1000,
                            itemCount = child.listFiles()?.size ?: 0
                        )
                    )
                }
                searchRecursive(child, depth + 1)
            }
        }

        searchRecursive(root, 0)
        return results.sortedByDescending { it.dateModified }
    }

    /**
     * Search folders by name in external storage - Suspending version
     * Hỗ trợ cancellation và được tối ưu cho IO operations
     */
    suspend fun searchFoldersSuspend(query: String): List<FolderItem> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<FolderItem>()
        val root = Environment.getExternalStorageDirectory()

        suspend fun searchRecursive(dir: java.io.File, depth: Int) {
            // Check cancellation
            if (!currentCoroutineContext().isActive) return

            if (depth > 5) return // limit depth

            val children = try {
                dir.listFiles()
            } catch (e: SecurityException) {
                null // Skip folders không có quyền truy cập
            } ?: return

            for (child in children) {
                // Check cancellation mỗi iteration
                if (!currentCoroutineContext().isActive) return

                if (!child.isDirectory) continue
                if (child.name.startsWith(".")) continue

                if (child.name.contains(query, ignoreCase = true)) {
                    results.add(
                        FolderItem(
                            name = child.name,
                            path = child.absolutePath,
                            dateModified = child.lastModified() / 1000,
                            itemCount = try {
                                child.listFiles()?.size ?: 0
                            } catch (e: SecurityException) {
                                0
                            }
                        )
                    )
                }

                // Yield để tránh block thread quá lâu
                yield()
                searchRecursive(child, depth + 1)
            }
        }

        searchRecursive(root, 0)
        return results.sortedByDescending { it.dateModified }
    }

    /**
     * Format epoch seconds → relative date string
     */
    private fun formatRelativeDate(epochSeconds: Long): String {
        val now = System.currentTimeMillis()
        val dateMillis = epochSeconds * 1000
        val diffMillis = now - dateMillis

        val diffMinutes = diffMillis / (1000 * 60)
        val diffHours = diffMillis / (1000 * 60 * 60)
        val diffDays = diffMillis / (1000 * 60 * 60 * 24)

        return when {
            diffMinutes < 1 -> "Vừa xong"
            diffMinutes < 60 -> "$diffMinutes phút trước"
            diffHours < 24 -> "$diffHours giờ trước"
            diffDays < 2 -> "Hôm qua"
            diffDays < 7 -> "$diffDays ngày trước"
            isThisMonth(dateMillis) -> "Tháng này"
            isThisYear(dateMillis) -> {
                SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(dateMillis))
            }
            else -> {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(dateMillis))
            }
        }
    }

    private fun isThisMonth(millis: Long): Boolean {
        val cal = Calendar.getInstance()
        val nowMonth = cal.get(Calendar.MONTH)
        val nowYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = millis
        return cal.get(Calendar.MONTH) == nowMonth && cal.get(Calendar.YEAR) == nowYear
    }

    private fun isThisYear(millis: Long): Boolean {
        val cal = Calendar.getInstance()
        val nowYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = millis
        return cal.get(Calendar.YEAR) == nowYear
    }

    /**
     * Phân tích chi tiết dung lượng bộ nhớ — trả về thông tin chi tiết
     * cho màn hình Quản lý lưu trữ
     */
    suspend fun getStorageAnalysis(): StorageAnalysis {
        log.d("getStorageAnalysis: start")
        val videoBytes = getTotalSizeByType(FileType.VIDEO)
        log.d("getStorageAnalysis: videoBytes=$videoBytes")
        val imageBytes = getTotalSizeByType(FileType.IMAGE)
        log.d("getStorageAnalysis: imageBytes=$imageBytes")
        val audioBytes = getTotalSizeByType(FileType.AUDIO)
        log.d("getStorageAnalysis: audioBytes=$audioBytes")
        val archiveBytes = getTotalSizeByType(FileType.ARCHIVE)
        log.d("getStorageAnalysis: archiveBytes=$archiveBytes")
        val apkBytes = getTotalSizeByType(FileType.APK)
        log.d("getStorageAnalysis: apkBytes=$apkBytes")
        val docBytes = getTotalSizeByType(FileType.DOC)
        log.d("getStorageAnalysis: docBytes=$docBytes")
        val downloadBytes = getTotalSizeByType(FileType.DOWNLOAD)
        log.d("getStorageAnalysis: downloadBytes=$downloadBytes")

        // Approximate trong RAM để ước tính (thay vì scan toàn bộ)
        val trashBytes = getTrashSizeEstimate()
        log.d("getStorageAnalysis: trashBytes=$trashBytes")
        val unusedAppsBytes = getUnusedAppsSizeEstimate()
        log.d("getStorageAnalysis: unusedAppsBytes=$unusedAppsBytes")
        val duplicateBytes = getDuplicateFilesSizeEstimate()
        log.d("getStorageAnalysis: duplicateBytes=$duplicateBytes")
        val largeFilesBytes = getLargeFilesSizeEstimate()
        log.d("getStorageAnalysis: largeFilesBytes=$largeFilesBytes")
        val oldScreenshotsBytes = getOldScreenshotsSizeEstimate()
        log.d("getStorageAnalysis: oldScreenshotsBytes=$oldScreenshotsBytes")
        val appsBytes = getInstalledAppsSize()
        log.d("getStorageAnalysis: appsBytes=$appsBytes")

        return StorageAnalysis(
            videoBytes = videoBytes,
            imageBytes = imageBytes,
            audioBytes = audioBytes,
            archiveBytes = archiveBytes,
            apkBytes = apkBytes,
            docBytes = docBytes,
            downloadBytes = downloadBytes,
            trashBytes = trashBytes,
            unusedAppsBytes = unusedAppsBytes,
            duplicateBytes = duplicateBytes,
            largeFilesBytes = largeFilesBytes,
            oldScreenshotsBytes = oldScreenshotsBytes,
            appsBytes = appsBytes
        )
    }

    private fun getTrashSizeEstimate(): Long {
        // Tính size thực tế từ thư mục .Trash/files (Room có thể lưu sai size cho folders)
        val trashDir = java.io.File(
            Environment.getExternalStorageDirectory(), ".Trash/files"
        )
        if (!trashDir.exists()) return 0L
        return calculateDirSize(trashDir)
    }

    /** Tính tổng size đệ quy của 1 thư mục */
    private fun calculateDirSize(dir: java.io.File): Long {
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return total
    }

    /**
     * Lấy tổng dung lượng ứng dụng (APK + data + cache) qua PackageManager.
     * Dùng suspendCoroutine để bridge async IPackageDataObserver callback.
     * Phân biệt:
     *   - User apps (non-system): tính đầy đủ
     *   - System apps: bỏ qua hoặc tính một phần
     */
    private suspend fun getInstalledAppsSize(): Long {
        log.d("getInstalledAppsSize: start — querying PackageManager")
        return suspendCoroutine { cont ->
            try {
                val pm = context.packageManager
                val pkgSizes = mutableMapOf<String, Long>()

                // Lấy interface mô tả IPackageDataObserver
                val observerClass = Class.forName("android.content.pm.IPackageDataObserver")
                val statsClass = Class.forName("android.content.pm.PackageStats")

                // Tạo anonymous observer implementation
                val observerProxy = java.lang.reflect.Proxy.newProxyInstance(
                    observerClass.classLoader,
                    arrayOf(observerClass)
                ) { _, method, args ->
                    if (method.name == "onGetStatsCompleted" && args != null && args.isNotEmpty()) {
                        val stats = args[0]
                        if (statsClass.isInstance(stats)) {
                            val codeSize = statsClass.getMethod("getCodeSize").invoke(stats) as? Long ?: 0L
                            val dataSize = statsClass.getMethod("getDataSize").invoke(stats) as? Long ?: 0L
                            val cacheSize = statsClass.getMethod("getCacheSize").invoke(stats) as? Long ?: 0L
                            val pkgName = statsClass.getMethod("getPackageName").invoke(stats) as? String
                            if (pkgName != null) {
                                pkgSizes[pkgName] = codeSize + dataSize
                            }
                        }
                    }
                    null
                }

                // Lấy method getPackageSizeInfo(packageName, observer)
                val getSizeMethod = pm.javaClass.getMethod(
                    "getPackageSizeInfo",
                    String::class.java,
                    observerClass
                )

                // Gọi getPackageSizeInfo cho tất cả packages
                val packages = pm.getInstalledPackages(0)
                for (packageInfo in packages) {
                    try {
                        getSizeMethod.invoke(pm, packageInfo.packageName, observerProxy)
                    } catch (_: Exception) { }
                }

                // Sau 3 giây, tính tổng (tránh block vĩnh viễn)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // Filter: chỉ tính user apps (không phải system app)
                    // FLAG_SYSTEM: bit 0 của applicationInfo.flags
                    var total = 0L
                    var userAppCount = 0
                    for (pi in packages) {
                        val isSystem = (pi.applicationInfo?.flags ?: 0) and 1 != 0
                        if (!isSystem) {
                            total += pkgSizes[pi.packageName] ?: 0L
                            userAppCount++
                        }
                    }
                    log.d("getInstalledAppsSize: completed — userAppCount=$userAppCount, totalBytes=$total")
                    cont.resumeWith(Result.success(total))
                }, 3000)

            } catch (e: Exception) {
                log.e("getInstalledAppsSize: error using reflection, falling back to 0", e)
                cont.resumeWith(Result.success(0L))
            }
        }
    }

    private fun getUnusedAppsSizeEstimate(): Long {
        // Approximate: ước tính dựa trên APK files + cache thư mục
        var total = 0L
        val pkgManager = context.packageManager
        val installedApps = pkgManager.getInstalledApplications(0)

        // Duyệt qua các ứng dụng và ước tính size
        for (appInfo in installedApps) {
            try {
                val appDir = java.io.File(appInfo.sourceDir)
                if (appDir.exists()) {
                    total += appDir.length()
                }
            } catch (_: Exception) { }
        }
        return total / 10 // Approximate — chỉ ước tính phần "có thể giải phóng"
    }

    /**
     * Ước tính dung lượng file trùng lặp — PHẢI khớp với kết quả DuplicatesScreen.
     *
     * Trước đây hàm này group theo SIZE only → 2 file khác nội dung nhưng trùng size bị
     * coi là duplicate → số hiển thị ở StorageManager (vd: 300.5MB) không khớp với
     * DuplicatesScreen (dùng content hash MD5). Sửa: gọi trực tiếp findDuplicateFiles()
     * để 2 nơi luôn cùng logic và cùng kết quả.
     */
    private suspend fun getDuplicateFilesSizeEstimate(): Long {
        return try {
            findDuplicateFiles().sumOf { it.wastedBytes }
        } catch (e: Exception) {
            log.e("getDuplicateFilesSizeEstimate failed", e)
            0L
        }
    }

    private fun getLargeFilesSizeEstimate(): Long {
        // Files > 25MB, exclude .Trash
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > ?"
        val args = arrayOf((25L * 1024 * 1024).toString())

        var total = 0L
        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: ""
                if (path.contains("/.Trash/")) continue
                total += cursor.getLong(sizeCol)
            }
        }
        return total
    }

    private fun getOldScreenshotsSizeEstimate(): Long {
        // Ảnh chụp màn hình cũ hơn 30 ngày
        val screenshotsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Screenshots")
        if (!screenshotsDir.exists()) return 0L

        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        var total = 0L

        screenshotsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < thirtyDaysAgo) {
                total += file.length()
            }
        }
        return total
    }

    /**
     * Tìm các file trùng lặp dựa trên name + size + content hash.
     * Chỉ scan trong essential folders + Download để tránh scan toàn bộ bộ nhớ.
     * Kết quả: nhóm các file có cùng hash → gần như chắc chắn là trùng nhau.
     */
    suspend fun findDuplicateFiles(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        log.d("findDuplicateFiles: start")
        val allFiles = mutableMapOf<String, MutableList<RecentFile>>() // hash → files

        // Scan các thư mục chính (essential folders + Download)
        val scanDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_ALARMS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS),
        )

        for (dir in scanDirs) {
            if (!dir.exists() || !dir.canRead()) continue
            scanForDuplicates(dir, allFiles, maxDepth = 5)
            if (!currentCoroutineContext().isActive) return@withContext emptyList()
        }

        // Filter: chỉ giữ nhóm có >= 2 file cùng hash
        val groups = allFiles
            .filter { (_, files) -> files.size >= 2 }
            .map { (hash, files) ->
                // Sort theo dateModified, file cũ nhất = Original
                val sorted = files.sortedBy { it.dateModified }
                val items = sorted.mapIndexed { index, file ->
                    DuplicateItem(
                        file = file,
                        isOriginal = index == 0,
                        parentFolder = extractParentFolder(file.path)
                    )
                }
                val size = files.first().size
                DuplicateGroup(
                    hash = hash,
                    size = size,
                    items = items
                )
            }
            .sortedByDescending { it.wastedBytes }

        log.d("findDuplicateFiles: done — ${groups.size} groups found, wasted=${groups.sumOf { it.wastedBytes }} bytes")
        groups
    }

    private suspend fun scanForDuplicates(
        dir: java.io.File,
        acc: MutableMap<String, MutableList<RecentFile>>,
        maxDepth: Int,
        currentDepth: Int = 0
    ) {
        if (!currentCoroutineContext().isActive || currentDepth > maxDepth) return
        if (!dir.canRead() || dir.name.startsWith(".")) return

        // Skip Trash directory
        if (dir.absolutePath.contains(".Trash")) return

        dir.listFiles()?.forEach { file ->
            if (!currentCoroutineContext().isActive) return@forEach

            if (file.isFile && file.length() > 0) {
                // Chỉ scan image/video/audio (những loại hay bị trùng)
                val mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                if (mime?.startsWith("image/") == true ||
                    mime?.startsWith("video/") == true ||
                    mime?.startsWith("audio/") == true
                ) {
                    // Tính hash dựa trên name + size + first few bytes
                    val hash = computeQuickHash(file)
                    if (hash != null) {
                        val recentFile = RecentFile(
                            name = file.name,
                            date = formatRelativeDate(file.lastModified() / 1000),
                            type = FileType.fromMimeType(mime),
                            uri = Uri.fromFile(file),  // Coil dùng Uri này để load thumbnail
                            path = file.absolutePath,
                            size = file.length(),
                            dateModified = file.lastModified() / 1000
                        )
                        acc.getOrPut(hash) { mutableListOf() }.add(recentFile)
                    }
                }
            } else if (file.isDirectory) {
                yield() // avoid blocking
                scanForDuplicates(file, acc, maxDepth, currentDepth + 1)
            }
        }
    }

    /**
     * Tính hash nhanh dựa trên NỘI DUNG file: size + first 4KB + last 4KB.
     * KHÔNG dùng tên file — vì file trùng lặp có thể bị đổi tên.
     */
    private fun computeQuickHash(file: java.io.File): String? {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val fileSize = file.length()
            md.update(fileSize.toString().toByteArray())

            java.io.RandomAccessFile(file, "r").use { raf ->
                val buf = ByteArray(4096)

                // First 4KB
                val readFirst = raf.read(buf)
                if (readFirst > 0) md.update(buf, 0, readFirst)

                // Last 4KB (nếu file đủ lớn và khác vùng first)
                if (fileSize > 8192) {
                    raf.seek(fileSize - 4096)
                    val readLast = raf.read(buf)
                    if (readLast > 0) md.update(buf, 0, readLast)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractParentFolder(path: String): String {
        val file = java.io.File(path)
        val parent = file.parentFile?.name ?: "/"
        return if (parent == "/") "/" else "/$parent/"
    }

    // ══════════════════════════════════════════════════════
    // findLargeFiles — Large Files screen
    // ══════════════════════════════════════════════════════

    /**
     * Tìm các file lớn hơn [minBytes], sắp xếp theo size giảm dần.
     * Query MediaStore để lấy đầy đủ thông tin (name, size, path, uri, type, dateModified).
     */
    suspend fun findLargeFiles(minBytes: Long): List<RecentFile> = withContext(Dispatchers.IO) {
        log.d("findLargeFiles: minBytes=$minBytes")
        val result = mutableListOf<RecentFile>()

        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.SIZE} > ? AND ${MediaStore.Files.FileColumns.DATA} NOT LIKE ?"
        val args = arrayOf(minBytes.toString(), "%/.Trash/%")
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        context.contentResolver.query(uri, projection, selection, args, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                if (!currentCoroutineContext().isActive) break

                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                val path = cursor.getString(dataCol) ?: continue
                val mime = cursor.getString(mimeCol)
                val dateMod = cursor.getLong(dateCol)
                val type = FileType.fromMimeType(mime)

                val contentUri = ContentUris.withAppendedId(uri, id)

                result.add(
                    RecentFile(
                        name = name,
                        date = formatRelativeDate(dateMod),
                        type = type,
                        uri = contentUri,
                        path = path,
                        size = size,
                        dateModified = dateMod
                    )
                )
            }
        }

        log.d("findLargeFiles: found ${result.size} files")
        result
    }

    // ══════════════════════════════════════════════════════
    // getUnusedApps — Unused Apps screen
    // ══════════════════════════════════════════════════════

    /**
     * Lấy danh sách ứng dụng không dùng trong 30 ngày qua.
     * Dùng UsageStatsManager để check last used time.
     * Chỉ trả về user apps (non-system).
     */
    suspend fun getUnusedApps(): List<com.example.codevui.ui.unusedapps.UnusedAppInfo> = withContext(Dispatchers.IO) {
        log.d("getUnusedApps: start")
        val pm = context.packageManager
        val usageStatsManager = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE)
                as? android.app.usage.UsageStatsManager

        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)

        // Dùng queryAndAggregateUsageStats — gộp tất cả stats theo package, chính xác nhất
        val aggregatedStats = usageStatsManager?.queryAndAggregateUsageStats(
            thirtyDaysAgo,
            System.currentTimeMillis()
        ) ?: emptyMap()

        // Set packages đã dùng trong 30 ngày (có foreground time > 0 HOẶC lastTimeUsed gần đây)
        val recentlyUsedPackages = mutableSetOf<String>()
        for ((pkgName, stat) in aggregatedStats) {
            val wasUsedRecently = stat.lastTimeUsed >= thirtyDaysAgo
            val hadForegroundTime = stat.totalTimeInForeground > 0
            if (wasUsedRecently && hadForegroundTime) {
                recentlyUsedPackages.add(pkgName)
            }
        }
        log.d("getUnusedApps: ${recentlyUsedPackages.size} packages used in last 30 days")

        // Lấy tất cả apps có launcher intent (apps hiển thị trên home screen)
        val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        launcherIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val launcherApps = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
        log.d("getUnusedApps: ${launcherApps.size} launcher apps found")

        val packages = pm.getInstalledPackages(0)
        val seenPackages = mutableSetOf<String>()  // Deduplicate
        val result = mutableListOf<com.example.codevui.ui.unusedapps.UnusedAppInfo>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val pkgName = pkg.packageName

            // Deduplicate
            if (pkgName in seenPackages) continue
            seenPackages.add(pkgName)

            // Bỏ qua chính app này
            if (pkgName == context.packageName) continue

            // Chỉ lấy apps có launcher icon
            if (pkgName !in launcherApps) continue

            // App KHÔNG nằm trong danh sách "recently used" → unused
            if (pkgName in recentlyUsedPackages) continue

            val appName = appInfo.loadLabel(pm).toString()
            val icon = try { appInfo.loadIcon(pm) } catch (_: Exception) { null }
            val sizeBytes = getAppSizeBytes(pkgName, appInfo)

            // Lấy lastTimeUsed từ aggregated stats (nếu có)
            val lastUsed = aggregatedStats[pkgName]?.lastTimeUsed ?: 0L

            result.add(
                com.example.codevui.ui.unusedapps.UnusedAppInfo(
                    packageName = pkgName,
                    appName = appName,
                    icon = icon,
                    sizeBytes = sizeBytes,
                    lastUsedTimestamp = lastUsed
                )
            )
        }

        // Sort by size desc
        val sorted = result.sortedByDescending { it.sizeBytes }
        log.d("getUnusedApps: found ${sorted.size} unused apps, total=${sorted.sumOf { it.sizeBytes }}")
        sorted
    }

    /** Lấy size chính xác của app dùng StorageStatsManager.queryStatsForPackage (API 26+) */
    private fun getAppSizeBytes(packageName: String, appInfo: android.content.pm.ApplicationInfo): Long {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val storageStatsManager = context.getSystemService(android.content.Context.STORAGE_STATS_SERVICE)
                    as android.app.usage.StorageStatsManager
                val storageUuid = appInfo.storageUuid
                // queryStatsForPackage thay vì queryStatsForUid — tránh duplicate khi shared UID
                val stats = storageStatsManager.queryStatsForPackage(
                    storageUuid,
                    packageName,
                    android.os.Process.myUserHandle()
                )
                return stats.appBytes + stats.dataBytes + stats.cacheBytes
            } catch (e: Exception) {
                log.d("getAppSizeBytes: StorageStatsManager failed for $packageName: ${e.message}")
            }
        }

        // Fallback: tính từ APK file
        return try {
            val apkFile = java.io.File(appInfo.sourceDir)
            apkFile.length()
        } catch (_: Exception) { 0L }
    }

    // ══════════════════════════════════════════════════════
    // getVolumeFileBreakdown — per-volume file analysis
    // Mirror từ MyFiles: phân tích file theo volume path
    // ══════════════════════════════════════════════════════

    /**
     * Lấy file breakdown cho 1 volume cụ thể (SD card, USB).
     * Query MediaStore với filter theo volume path.
     *
     * @param volumeRootPath Root path của volume (VD: /storage/XXXX-XXXX)
     */
    fun getVolumeFileBreakdown(volumeRootPath: String): VolumeFileBreakdown {
        log.d("getVolumeFileBreakdown: path=$volumeRootPath")

        val videoBytes = getTotalSizeByTypeAndPath(FileType.VIDEO, volumeRootPath)
        val imageBytes = getTotalSizeByTypeAndPath(FileType.IMAGE, volumeRootPath)
        val audioBytes = getTotalSizeByTypeAndPath(FileType.AUDIO, volumeRootPath)
        val archiveBytes = getTotalSizeByTypeAndPath(FileType.ARCHIVE, volumeRootPath)
        val apkBytes = getTotalSizeByTypeAndPath(FileType.APK, volumeRootPath)
        val docBytes = getTotalSizeByTypeAndPath(FileType.DOC, volumeRootPath)
        val downloadBytes = getTotalSizeByTypeAndPath(FileType.DOWNLOAD, volumeRootPath)

        // Trash trên external storage
        val trashDir = java.io.File(volumeRootPath, ".Trash/files")
        val trashBytes = if (trashDir.exists()) calculateDirSize(trashDir) else 0L

        log.d("getVolumeFileBreakdown: video=$videoBytes, image=$imageBytes, audio=$audioBytes, " +
              "archive=$archiveBytes, apk=$apkBytes, doc=$docBytes, download=$downloadBytes, trash=$trashBytes")

        return VolumeFileBreakdown(
            domainType = 0,  // Caller sẽ set đúng
            videoBytes = videoBytes,
            imageBytes = imageBytes,
            audioBytes = audioBytes,
            archiveBytes = archiveBytes,
            apkBytes = apkBytes,
            docBytes = docBytes,
            downloadBytes = downloadBytes,
            trashBytes = trashBytes
        )
    }

    /**
     * Tính tổng size theo type + path filter
     */
    private fun getTotalSizeByTypeAndPath(fileType: FileType, volumeRootPath: String): Long {
        val uri = MediaStore.Files.getContentUri("external")

        val typeSelection = buildSelection(fileType) ?: return 0L
        val pathSelection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
        val selection = "$typeSelection AND $pathSelection"
        val selectionArgs = arrayOf("$volumeRootPath%")

        var total = 0L
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Files.FileColumns.SIZE),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            while (cursor.moveToNext()) {
                total += cursor.getLong(sizeCol)
            }
        }
        return total
    }
}

/**
 * Per-volume file category breakdown
 * Dùng cho SD/USB storage analysis
 */
data class VolumeFileBreakdown(
    val domainType: Int = 0,
    val videoBytes: Long = 0L,
    val imageBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val archiveBytes: Long = 0L,
    val apkBytes: Long = 0L,
    val docBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val trashBytes: Long = 0L,
    val otherBytes: Long = 0L,
    val systemBytes: Long = 0L
) {
    val totalCategorized: Long
        get() = videoBytes + imageBytes + audioBytes + archiveBytes + apkBytes + docBytes
}