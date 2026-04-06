package com.example.codevui.data

import android.content.Context
import android.os.Environment
import com.example.codevui.data.db.AppDatabase
import com.example.codevui.data.db.TrashEntity
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private val log = Logger("TrashManager")

/**
 * TrashManager — quản lý thùng rác bằng Room + filesystem.
 *
 * Files thật được lưu ở: /storage/emulated/0/.Trash/files/
 * Metadata được lưu trong Room table: trash_items
 */
class TrashManager(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).trashDao()

    companion object {
        private const val TRASH_DIR = ".Trash"
        private const val FILES_DIR = "files"
        private const val MAX_TRASH_DAYS = 30L

        fun expirationTime(deleteTimeEpochSeconds: Long): Long =
            deleteTimeEpochSeconds + TimeUnit.DAYS.toSeconds(MAX_TRASH_DAYS)

        fun isExpired(deleteTimeEpochSeconds: Long): Boolean =
            System.currentTimeMillis() / 1000 > expirationTime(deleteTimeEpochSeconds)

        fun daysUntilExpiry(deleteTimeEpochSeconds: Long): Int {
            val remain = expirationTime(deleteTimeEpochSeconds) - (System.currentTimeMillis() / 1000)
            return (remain / TimeUnit.DAYS.toSeconds(1)).toInt().coerceAtLeast(0)
        }
    }

    private val trashRoot: File
        get() = File(Environment.getExternalStorageDirectory(), TRASH_DIR)

    private val filesDir: File
        get() = File(trashRoot, FILES_DIR)

    init {
        if (!filesDir.exists()) filesDir.mkdirs()
    }

    suspend fun moveToTrash(filePaths: List<String>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        log.d("moveToTrash: ${filePaths.size} items")

        // Track parent directories để scan sau khi xóa
        val parentDirs = mutableSetOf<String>()

        for (path in filePaths) {
            val source = File(path)
            if (!source.exists()) {
                failed++
                continue
            }

            // Ghi nhận thư mục cha trước khi xóa
            source.parentFile?.let { parentDirs.add(it.absolutePath) }

            val trashName = generateTrashName(source.name)
            val trashFile = File(filesDir, trashName)

            try {
                if (source.isDirectory) {
                    copyDirectoryRecursively(source, trashFile)
                    source.deleteRecursively()
                } else {
                    FileInputStream(source).use { fis ->
                        FileOutputStream(trashFile).use { fos ->
                            fis.copyTo(fos)
                        }
                    }
                    source.delete()
                }

                // Tính size chính xác: dùng recursive size cho directories
                val fileSize = if (trashFile.exists()) {
                    if (trashFile.isDirectory) calculateDirSize(trashFile) else trashFile.length()
                } else {
                    source.length()
                }

                val entity = TrashEntity(
                    id = trashName,
                    originalName = source.name,
                    trashName = trashName,
                    originalPath = path,
                    size = fileSize,
                    deleteTimeEpoch = System.currentTimeMillis() / 1000,
                    isDirectory = source.isDirectory,
                    mimeType = getMimeType(path)
                )
                dao.insert(entity)
                success++
                log.d("Moved to trash: $path -> $trashName")
            } catch (e: Exception) {
                log.e("Move to trash failed: $path", e)
                trashFile.deleteRecursively()
                failed++
            }
        }

        cleanExpired()

        // Scan thư mục cha sau khi file đã bị xóa khỏi vị trí gốc
        // → MediaStore nhận biết file không còn ở đó nữa (đối với các app khác)
        if (parentDirs.isNotEmpty()) {
            MediaStoreScanner.scanPaths(context, parentDirs.toList())
        }

        Pair(success, failed)
    }

    /**
     * Restore các item từ trash về vị trí gốc.
     * Trả về Triple(success, failed, destDir) — destDir là thư mục của item đầu tiên restore thành công.
     */
    suspend fun restore(trashIds: List<String>): Triple<Int, Int, String> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        var firstDestDir = ""
        val all = dao.getAll().associateBy { it.id }
        val restoredDirs = mutableSetOf<String>()

        for (id in trashIds) {
            val item = all[id] ?: run {
                failed++
                continue
            }
            val trashFile = File(filesDir, item.trashName)
            val originalFile = File(item.originalPath)
            val restoreDir = originalFile.parentFile

            if (restoreDir == null) {
                failed++
                continue
            }
            if (!restoreDir.exists()) restoreDir.mkdirs()

            var dest = File(restoreDir, originalFile.name)
            if (dest.exists()) dest = resolveConflict(dest)

            try {
                if (trashFile.isDirectory) {
                    copyDirectoryRecursively(trashFile, dest)
                    trashFile.deleteRecursively()
                } else {
                    FileInputStream(trashFile).use { fis ->
                        FileOutputStream(dest).use { fos ->
                            fis.copyTo(fos)
                        }
                    }
                    trashFile.delete()
                }
                dao.deleteById(id)
                if (firstDestDir.isEmpty()) {
                    firstDestDir = if (trashFile.isDirectory) dest.absolutePath else restoreDir.absolutePath
                }
                restoredDirs.add(restoreDir.absolutePath)
                success++
                log.d("Restored: ${item.trashName} -> ${dest.absolutePath}")
            } catch (e: Exception) {
                log.e("Restore failed: ${item.trashName}", e)
                failed++
            }
        }

        // Scan thư mục đích sau khi restore
        // → MediaStore nhận biết file mới xuất hiện (các app khác thấy được)
        if (restoredDirs.isNotEmpty()) {
            MediaStoreScanner.scanPaths(context, restoredDirs.toList())
        }

        Triple(success, failed, firstDestDir)
    }

    suspend fun permanentlyDelete(trashIds: List<String>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        val all = dao.getAll().associateBy { it.id }

        for (id in trashIds) {
            val item = all[id] ?: run {
                failed++
                continue
            }
            val trashFile = File(filesDir, item.trashName)
            try {
                if (trashFile.isDirectory) trashFile.deleteRecursively() else trashFile.delete()
                dao.deleteById(id)
                success++
            } catch (e: Exception) {
                log.e("Permanent delete failed: ${item.trashName}", e)
                failed++
            }
        }

        // Scan thùng rác sau khi xóa vĩnh viễn
        // → MediaStore cập nhật (files đã thật sự biến mất khỏi .Trash)
        if (filesDir.exists()) {
            MediaStoreScanner.scanDirectory(context, filesDir.absolutePath)
        }

        Pair(success, failed)
    }

    suspend fun emptyTrash(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        val ids = all.map { it.id }
        permanentlyDelete(ids)
    }

    suspend fun getTrashItems(): List<TrashItem> = withContext(Dispatchers.IO) {
        cleanExpired()
        dao.getAll().map { it.toTrashItem() }
    }

    fun observeTrashItems(): Flow<List<TrashItem>> {
        return dao.observeAll().map { list ->
            list.filterNot { isExpired(it.deleteTimeEpoch) }
                .map { it.toTrashItem() }
        }
    }

    fun observeTrashCount(): Flow<Int> {
        return dao.observeCount()
    }

    suspend fun getTrashCount(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }

    private suspend fun cleanExpired() = withContext(Dispatchers.IO) {
        val cutoff = (System.currentTimeMillis() / 1000) - TimeUnit.DAYS.toSeconds(MAX_TRASH_DAYS)
        val expired = dao.findExpired(cutoff)
        if (expired.isEmpty()) return@withContext

        expired.forEach { item ->
            val f = File(filesDir, item.trashName)
            try {
                if (item.isDirectory) f.deleteRecursively() else f.delete()
            } catch (_: Exception) {}
        }
        dao.deleteByIds(expired.map { it.id })
    }

    private fun generateTrashName(originalName: String): String {
        val ts = System.currentTimeMillis()
        val safe = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "${ts}_$safe"
    }

    private fun resolveConflict(file: File): File {
        if (!file.exists()) return file
        val parent = file.parentFile ?: return file
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var n = 1
        var candidate: File
        do {
            candidate = File(parent, "${file.nameWithoutExtension} ($n)$ext")
            n++
        } while (candidate.exists())
        return candidate
    }

    private fun copyDirectoryRecursively(src: File, dest: File) {
        dest.mkdirs()
        for (child in src.listFiles() ?: return) {
            val childDest = File(dest, child.name)
            if (child.isDirectory) {
                copyDirectoryRecursively(child, childDest)
            } else {
                FileInputStream(child).use { fis ->
                    FileOutputStream(childDest).use { fos ->
                        fis.copyTo(fos)
                    }
                }
            }
        }
    }

    /** Tính tổng size đệ quy của 1 thư mục */
    private fun calculateDirSize(dir: File): Long {
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return total
    }

    private fun getMimeType(path: String): String {
        val ext = File(path).extension.lowercase()
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun TrashEntity.toTrashItem(): TrashItem = TrashItem(
        id = id,
        originalName = originalName,
        trashName = trashName,
        originalPath = originalPath,
        size = size,
        deleteTimeEpoch = deleteTimeEpoch,
        isDirectory = isDirectory,
        mimeType = mimeType,
        daysUntilExpiry = daysUntilExpiry(deleteTimeEpoch)
    )
}

data class TrashItem(
    val id: String,
    val originalName: String,
    val trashName: String,
    val originalPath: String,
    val size: Long = 0L,
    val deleteTimeEpoch: Long = 0L,
    val isDirectory: Boolean = false,
    val mimeType: String = "",
    val daysUntilExpiry: Int = 30
)
