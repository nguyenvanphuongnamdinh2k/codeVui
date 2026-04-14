package com.example.codevui.data

import com.example.codevui.util.Logger
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
import com.example.codevui.model.ArchiveEntry
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
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

    private val log = Logger("FileOperations")
    private const val BUFFER_SIZE = 65536 // 64KB buffer cho performance tốt hơn

    enum class OperationType { COPY, MOVE }

    /**
     * Move files to trash (delete) — emit progress qua Flow<ProgressState>.
     * Chạy qua FileOperationService để có progress bar + notification.
     */
    fun trashFiles(context: android.content.Context, paths: List<String>): kotlinx.coroutines.flow.Flow<ProgressState> = flow {
        emit(ProgressState.Counting)

        if (paths.isEmpty()) {
            emit(ProgressState.Error("Không có file nào để xóa"))
            return@flow
        }

        val trashManager = TrashManager(context)
        val total = paths.size

        emit(ProgressState.Running(
            currentFile = "Đang xóa...",
            done = 0,
            total = total,
            percent = 0
        ))

        try {
            val (success, failed) = trashManager.moveToTrash(paths)
            val doneCount = success
            emit(ProgressState.Running(
                currentFile = "Hoàn tất",
                done = doneCount,
                total = total,
                percent = 100
            ))
            emit(ProgressState.Done(success = doneCount, failed = failed))
        } catch (e: Exception) {
            log.e("trashFiles failed", e)
            emit(ProgressState.Error(e.message ?: "Lỗi khi xóa file"))
        }
    }.flowOn(Dispatchers.IO)

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
                        log.e("Source not found: ${source.path}")
                        false
                    }
                    destDir.startsWith(source.absolutePath) -> {
                        log.e("Cannot copy/move into itself: ${source.path}")
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
                    log.e("$operation failed: ${source.path}", e)
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
            log.e("Compress failed", e)
            zipFile.delete()
            emit(ProgressState.Error(e.message ?: "Lỗi không xác định khi nén"))
        }

    }.flowOn(Dispatchers.IO)

    // ─── Extract Archive ─────────────────────────────────────────────────────

    /**
     * Giải nén các entries đã chọn từ archive, emit progress byte-level.
     * Chạy qua FileOperationService — giống copy/move/compress.
     *
     * @param archivePath đường dẫn file archive
     * @param entryPaths danh sách entry paths cần giải nén
     * @param destPath folder đích
     * @param allEntries tất cả entries trong archive (để resolve folders)
     * @param password mật khẩu (optional)
     */
    fun extractArchive(
        archivePath: String,
        entryPaths: List<String>,
        destPath: String,
        allEntries: List<ArchiveEntry>,
        password: String? = null
    ): Flow<ProgressState> = flow {

        emit(ProgressState.Counting)

        val archiveFile = File(archivePath)
        if (!archiveFile.exists()) {
            emit(ProgressState.Error("File nén không tồn tại"))
            return@flow
        }

        val destDir = File(destPath)
        if (!destDir.exists()) destDir.mkdirs()

        // ── Resolve selected folders → tất cả file entries cần extract ──
        val selectedFolders = mutableSetOf<String>()
        val selectedFiles = mutableSetOf<String>()

        for (path in entryPaths) {
            if (path.endsWith("/")) {
                selectedFolders.add(path)
            } else {
                selectedFiles.add(path)
                val asFolder = "$path/"
                val hasChildren = allEntries.any { it.path.startsWith(asFolder) }
                if (hasChildren) selectedFolders.add(asFolder)
            }
        }

        // Nếu folder có children explicit selected → không expand folder
        val foldersToRemove = mutableSetOf<String>()
        for (folder in selectedFolders) {
            if (selectedFiles.any { it.startsWith(folder) }) {
                foldersToRemove.add(folder)
            }
        }
        selectedFolders.removeAll(foldersToRemove)

        val pathsToExtract = mutableSetOf<String>()
        pathsToExtract.addAll(selectedFiles)
        for (folder in selectedFolders) {
            pathsToExtract.add(folder)
            allEntries.filter { it.path.startsWith(folder) && it.path != folder }
                .forEach { pathsToExtract.add(it.path) }
        }

        // Tính totalSize cho byte-level progress
        val filePathsToExtract = pathsToExtract.filter { !it.endsWith("/") }
        val totalSize = filePathsToExtract.sumOf { path ->
            allEntries.find { it.path == path }?.size ?: 0L
        }
        val total = filePathsToExtract.size
        if (total == 0) {
            emit(ProgressState.Done(success = 0, failed = 0))
            return@flow
        }

        var success = 0
        var failed = 0
        var handledBytes = 0L

        try {
            val zipFile = ZipFile(archiveFile)

            if (zipFile.isEncrypted) {
                if (password == null) {
                    emit(ProgressState.Error("File nén được bảo vệ bằng mật khẩu"))
                    return@flow
                }
                zipFile.setPassword(password.toCharArray())
            }

            for (entryPath in pathsToExtract) {
                val ctx = currentCoroutineContext()
                if (!ctx.isActive) break

                try {
                    val fileHeader = zipFile.getFileHeader(entryPath) ?: continue

                    if (fileHeader.isDirectory) {
                        File(destDir, entryPath).mkdirs()
                        continue
                    }

                    // Extract file với byte-level progress
                    val outputFile = File(destDir, entryPath)
                    outputFile.parentFile?.mkdirs()

                    val entrySize = fileHeader.uncompressedSize.coerceAtLeast(0)

                    zipFile.getInputStream(fileHeader).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!ctx.isActive) return@flow
                                output.write(buffer, 0, bytesRead)
                                handledBytes += bytesRead
                            }
                        }
                    }

                    success++
                    val percent = if (totalSize > 0) {
                        (handledBytes * 100 / totalSize).toInt().coerceIn(0, 100)
                    } else {
                        (success * 100 / total).coerceIn(0, 100)
                    }

                    emit(ProgressState.Running(
                        currentFile = outputFile.name,
                        done = success,
                        total = total,
                        percent = percent
                    ))

                } catch (e: ZipException) {
                    if (e.message?.contains("Wrong Password", ignoreCase = true) == true) {
                        emit(ProgressState.Error("Sai mật khẩu"))
                        return@flow
                    }
                    log.e("Extract failed: $entryPath", e)
                    failed++
                } catch (e: Exception) {
                    log.e("Extract failed: $entryPath", e)
                    failed++
                }
            }

            emit(ProgressState.Done(success = success, failed = failed))

        } catch (e: ZipException) {
            if (e.message?.contains("Wrong Password", ignoreCase = true) == true) {
                emit(ProgressState.Error("Sai mật khẩu"))
            } else {
                emit(ProgressState.Error(e.message ?: "Lỗi giải nén"))
            }
        } catch (e: Exception) {
            log.e("Extract archive failed", e)
            emit(ProgressState.Error(e.message ?: "Lỗi giải nén không xác định"))
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