package com.example.codevui.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * FileOperations — thực hiện copy/move/compress trên background thread (IO dispatcher).
 *
 * Tất cả các hàm trả về Flow<ProgressState> để caller có thể:
 *   - Hiển thị progress realtime
 *   - Cancel bằng cách cancel coroutine collect flow
 *
 * Cách dùng trong ViewModel:
 *   viewModelScope.launch {
 *       FileOperations.execute(paths, dest, COPY).collect { state ->
 *           _progressState.value = state
 *       }
 *   }
 */
object FileOperations {

    private const val TAG = "FileOperations"
    private const val BUFFER_SIZE = 65536 // 64KB buffer cho performance tốt hơn

    enum class OperationType { COPY, MOVE }

    // ─── Progress State ───────────────────────────────────────────────────────

    sealed class ProgressState {
        /** Đang đếm file trước khi bắt đầu */
        object Counting : ProgressState()

        /** Đang xử lý */
        data class Running(
            val currentFile: String,  // tên file đang xử lý
            val done: Int,            // số file đã xong
            val total: Int,           // tổng số file
            val percent: Int          // 0..100
        ) : ProgressState()

        /** Hoàn thành */
        data class Done(
            val success: Int,
            val failed: Int,
            val outputPath: String? = null
        ) : ProgressState()

        /** Lỗi nghiêm trọng (không thể tạo folder đích, v.v.) */
        data class Error(val message: String) : ProgressState()
    }

    // ─── Execute (Copy / Move) ────────────────────────────────────────────────

    fun execute(
        sourcePaths: List<String>,
        destDir: String,
        operation: OperationType
    ): Flow<ProgressState> = channelFlow {
        // channelFlow: thread-safe, cho phép emit() từ nhiều coroutine song song

        send(ProgressState.Counting)

        val destFolder = File(destDir)
        if (!destFolder.exists() && !destFolder.mkdirs()) {
            send(ProgressState.Error("Không thể tạo thư mục đích"))
            return@channelFlow
        }

        val validSources = sourcePaths
            .map { File(it) }
            .filter { source ->
                when {
                    !source.exists() -> {
                        Log.e(TAG, "Source not found: ${source.path}")
                        false
                    }
                    destDir.startsWith(source.absolutePath) -> {
                        Log.e(TAG, "Cannot copy/move into itself: ${source.path}")
                        false
                    }
                    else -> true
                }
            }

        val invalidCount = sourcePaths.size - validSources.size

        val total = validSources.sumOf { countFiles(it) }
        if (total == 0) {
            send(ProgressState.Done(success = 0, failed = invalidCount))
            return@channelFlow
        }

        // Mutex chỉ bảo vệ counter — send() trong channelFlow đã thread-safe
        val mutex = Mutex()
        var done = 0
        var failed = invalidCount

        // Mỗi source chạy song song trên IO dispatcher
        validSources.map { source ->
            async(Dispatchers.IO) {
                val dest = resolveConflict(File(destFolder, source.name))
                try {
                    copyOrMoveRecursive(
                        source = source,
                        dest = dest,
                        operation = operation,
                        onFileProcessed = { fileName ->
                            mutex.withLock { done++ }
                            send(
                                ProgressState.Running(
                                    currentFile = fileName,
                                    done = done,
                                    total = total,
                                    percent = (done * 100 / total).coerceIn(0, 100)
                                )
                            )
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "$operation failed: ${source.path}", e)
                    mutex.withLock { failed++ }
                }
            }
        }.awaitAll()

        send(ProgressState.Done(success = done, failed = failed))
    }

    // ─── Compress to ZIP ──────────────────────────────────────────────────────

    fun compressToZip(
        sourcePaths: List<String>,
        customName: String? = null
    ): Flow<ProgressState> = flow {

        emit(ProgressState.Counting)

        if (sourcePaths.isEmpty()) {
            emit(ProgressState.Error("Không có file nào để nén"))
            return@flow
        }

        val firstFile = File(sourcePaths.first())
        val parentDir = firstFile.parentFile
            ?: run {
                emit(ProgressState.Error("Không tìm thấy thư mục cha"))
                return@flow
            }

        val baseName = customName
            ?: if (sourcePaths.size == 1) firstFile.nameWithoutExtension else "Archive"
        val zipFile = resolveConflict(File(parentDir, "$baseName.zip"))

        val sources = sourcePaths.map { File(it) }.filter { it.exists() }
        val total = sources.sumOf { countFiles(it) }
        if (total == 0) {
            emit(ProgressState.Error("Không tìm thấy file nào"))
            return@flow
        }

        var done = 0
        var failed = 0

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile), BUFFER_SIZE)).use { zos ->
                for (source in sources) {
                    addToZip(
                        zos = zos,
                        file = source,
                        entryName = source.name,
                        onFileProcessed = { fileName ->
                            done++
                            emit(
                                ProgressState.Running(
                                    currentFile = fileName,
                                    done = done,
                                    total = total,
                                    percent = (done * 100 / total).coerceIn(0, 100)
                                )
                            )
                        }
                    )
                }
            }
            emit(ProgressState.Done(success = done, failed = failed, outputPath = zipFile.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Compress failed", e)
            zipFile.delete()
            emit(ProgressState.Error(e.message ?: "Lỗi không xác định khi nén"))
        }

    }.flowOn(Dispatchers.IO)

    // ─── Internal helpers ────────────────────────────────────────────────────

    /** Đếm tổng số file (không tính folder) để tính % */
    private fun countFiles(file: File): Int {
        return if (file.isDirectory) {
            file.walkTopDown().count { it.isFile }
        } else 1
    }

    /**
     * Copy hoặc move đệ quy, emit progress mỗi khi xử lý xong 1 file.
     * Kiểm tra isActive để support cancel.
     */
    private suspend fun copyOrMoveRecursive(
        source: File,
        dest: File,
        operation: OperationType,
        onFileProcessed: suspend (fileName: String) -> Unit
    ) {
        val ctx = currentCoroutineContext()

        if (source.isDirectory) {
            dest.mkdirs()
            val children = source.listFiles() ?: return
            for (child in children) {
                if (!ctx.isActive) return // cancelled
                copyOrMoveRecursive(
                    source = child,
                    dest = File(dest, child.name),
                    operation = operation,
                    onFileProcessed = onFileProcessed
                )
            }
            // Sau khi copy xong folder, xóa source nếu MOVE
            if (operation == OperationType.MOVE && ctx.isActive) {
                source.delete()
            }
        } else {
            if (!ctx.isActive) return
            copyFileWithBuffer(source, dest)
            if (operation == OperationType.MOVE) source.delete()
            onFileProcessed(source.name)
        }
    }

    /** Copy file với buffer 64KB thay vì dùng copyTo() mặc định */
    private fun copyFileWithBuffer(source: File, dest: File) {
        dest.parentFile?.mkdirs()
        FileInputStream(source).use { fis ->
            FileOutputStream(dest).use { fos ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    /**
     * Thêm file/folder vào ZIP, emit progress mỗi file.
     * Kiểm tra isActive để support cancel.
     */
    private suspend fun addToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
        onFileProcessed: suspend (fileName: String) -> Unit
    ) {
        val ctx = currentCoroutineContext()
        if (!ctx.isActive) return

        if (file.isDirectory) {
            val children = file.listFiles()
            if (children.isNullOrEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            } else {
                for (child in children) {
                    if (!ctx.isActive) return
                    addToZip(zos, child, "$entryName/${child.name}", onFileProcessed)
                }
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    if (!ctx.isActive) {
                        zos.closeEntry()
                        return
                    }
                    zos.write(buffer, 0, bytesRead)
                }
            }
            zos.closeEntry()
            onFileProcessed(file.name)
        }
    }

    private fun resolveConflict(file: File): File {
        if (!file.exists()) return file
        val parent = file.parentFile ?: return file
        val nameWithoutExt = file.nameWithoutExtension
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        var counter = 1
        var newFile: File
        do {
            newFile = File(parent, "$nameWithoutExt ($counter)$ext")
            counter++
        } while (newFile.exists())
        return newFile
    }
}