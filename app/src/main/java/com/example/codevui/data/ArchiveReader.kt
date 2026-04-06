package com.example.codevui.data

import android.util.Log
import com.example.codevui.model.ArchiveEntry
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import java.io.File

/**
 * ArchiveReader — đọc danh sách entries trong file nén
 * Hiện hỗ trợ ZIP. Có thể mở rộng thêm RAR, 7z bằng thư viện bên thứ 3
 */
object ArchiveReader {

    private const val TAG = "ArchiveReader"

    /**
     * Exception ném ra khi file archive cần password
     */
    class PasswordRequiredException(message: String) : Exception(message)

    /**
     * Exception ném ra khi password sai
     */
    class InvalidPasswordException(message: String) : Exception(message)

    /**
     * Đọc tất cả entries trong file zip
     * @param password Mật khẩu (optional) - hiện tại chưa hỗ trợ password protected zip
     */
    fun readEntries(archivePath: String, password: String? = null): List<ArchiveEntry> {
        val file = File(archivePath)
        if (!file.exists()) return emptyList()

        return try {
            when (file.extension.lowercase()) {
                "zip" -> readZipEntries(file, password)
                else -> {
                    Log.w(TAG, "Unsupported archive format: ${file.extension}")
                    emptyList()
                }
            }
        } catch (e: PasswordRequiredException) {
            throw e
        } catch (e: InvalidPasswordException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read archive: $archivePath", e)
            emptyList()
        }
    }

    /**
     * Lấy entries ở 1 level cụ thể trong archive (giống browse folder)
     * @param currentPath path bên trong archive, "" = root
     * @return Pair(folders, files) ở level đó
     */
    fun listLevel(
        allEntries: List<ArchiveEntry>,
        currentPath: String = ""
    ): Pair<List<ArchiveEntry>, List<ArchiveEntry>> {
        val prefix = if (currentPath.isEmpty()) "" else
            if (currentPath.endsWith("/")) currentPath else "$currentPath/"

        val folders = mutableSetOf<String>() // track unique folder names
        val folderEntries = mutableListOf<ArchiveEntry>()
        val fileEntries = mutableListOf<ArchiveEntry>()

        for (entry in allEntries) {
            val entryPath = entry.path
            if (!entryPath.startsWith(prefix)) continue
            if (entryPath == prefix) continue // skip the current dir itself

            val relativePath = entryPath.removePrefix(prefix)

            // Check if this is a direct child
            val slashIndex = relativePath.indexOf('/')
            if (slashIndex == -1) {
                // Direct child file
                if (!entry.isDirectory) {
                    fileEntries.add(entry)
                }
            } else {
                // It's inside a subfolder — extract folder name
                val folderName = relativePath.substring(0, slashIndex)
                if (folderName !in folders) {
                    folders.add(folderName)

                    // Count items in this subfolder
                    val subPrefix = "$prefix$folderName/"
                    val itemCount = allEntries.count { e ->
                        e.path.startsWith(subPrefix) && e.path != subPrefix &&
                                !e.path.removePrefix(subPrefix).contains('/')
                    }

                    folderEntries.add(
                        ArchiveEntry(
                            name = folderName,
                            path = "$prefix$folderName/",
                            isDirectory = true,
                            size = 0,
                            lastModified = entry.lastModified
                        )
                    )
                }
            }
        }

        // Also check entries that ARE directories at this level
        for (entry in allEntries) {
            if (!entry.isDirectory) continue
            val entryPath = entry.path
            if (!entryPath.startsWith(prefix)) continue
            val relativePath = entryPath.removePrefix(prefix).trimEnd('/')
            if (relativePath.isEmpty() || relativePath.contains('/')) continue

            if (relativePath !in folders) {
                folders.add(relativePath)
                val subPrefix = "$prefix$relativePath/"
                val itemCount = allEntries.count { e ->
                    e.path.startsWith(subPrefix) && e.path != subPrefix &&
                            !e.path.removePrefix(subPrefix).contains('/')
                }
                folderEntries.add(
                    ArchiveEntry(
                        name = relativePath,
                        path = entryPath,
                        isDirectory = true,
                        size = 0,
                        lastModified = entry.lastModified
                    )
                )
            }
        }

        return Pair(
            folderEntries.sortedBy { it.name.lowercase() },
            fileEntries.sortedBy { it.name.lowercase() }
        )
    }

    private fun readZipEntries(file: File, password: String? = null): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()

        try {
            val zipFile = ZipFile(file)

            // Check if encrypted
            if (zipFile.isEncrypted) {
                if (password == null) {
                    throw PasswordRequiredException("Archive file is password protected")
                }
                // Set password
                zipFile.setPassword(password.toCharArray())
            }

            // Read all file headers
            val fileHeaders = zipFile.fileHeaders
            for (header in fileHeaders) {
                entries.add(
                    ArchiveEntry(
                        name = header.fileName.trimEnd('/').substringAfterLast('/'),
                        path = header.fileName,
                        size = header.uncompressedSize.coerceAtLeast(0),
                        compressedSize = header.compressedSize.coerceAtLeast(0),
                        isDirectory = header.isDirectory,
                        lastModified = header.lastModifiedTimeEpoch / 1000
                    )
                )
            }

            // Verify password bằng cách thử đọc file đầu tiên
            if (zipFile.isEncrypted && password != null) {
                val firstFileHeader = fileHeaders.find { !it.isDirectory }
                if (firstFileHeader != null) {
                    try {
                        zipFile.getInputStream(firstFileHeader).use { it.read() }
                    } catch (e: ZipException) {
                        if (e.message?.contains("Wrong Password", ignoreCase = true) == true) {
                            throw InvalidPasswordException("Invalid password")
                        }
                        throw e
                    }
                }
            }

        } catch (e: PasswordRequiredException) {
            throw e
        } catch (e: InvalidPasswordException) {
            throw e
        } catch (e: ZipException) {
            Log.e(TAG, "ZipException: ${e.message}", e)
            if (e.message?.contains("Wrong Password", ignoreCase = true) == true) {
                throw InvalidPasswordException("Invalid password")
            }
            throw e
        }

        return entries
    }

    /**
     * Giải nén các entries đã chọn từ archive
     * Giữ nguyên cấu trúc folder như trong archive
     *
     * @param archivePath đường dẫn tới file archive
     * @param entryPaths danh sách các entry paths cần giải nén (bao gồm cả folders)
     * @param destPath folder đích để giải nén
     * @param allEntries tất cả entries trong archive (để resolve folders)
     * @param password Mật khẩu (optional)
     * @return Pair(success count, failed count)
     */
    fun extractEntries(
        archivePath: String,
        entryPaths: List<String>,
        destPath: String,
        allEntries: List<ArchiveEntry>,
        password: String? = null
    ): Pair<Int, Int> {
        val archiveFile = File(archivePath)
        if (!archiveFile.exists()) return Pair(0, entryPaths.size)

        return try {
            when (archiveFile.extension.lowercase()) {
                "zip" -> extractZipEntries(archiveFile, entryPaths, destPath, allEntries, password)
                else -> {
                    Log.w(TAG, "Unsupported archive format for extraction: ${archiveFile.extension}")
                    Pair(0, entryPaths.size)
                }
            }
        } catch (e: PasswordRequiredException) {
            throw e
        } catch (e: InvalidPasswordException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract entries from: $archivePath", e)
            Pair(0, entryPaths.size)
        }
    }

    private fun extractZipEntries(
        archiveFile: File,
        entryPaths: List<String>,
        destPath: String,
        allEntries: List<ArchiveEntry>,
        password: String? = null
    ): Pair<Int, Int> {
        val destDir = File(destPath)
        if (!destDir.exists()) destDir.mkdirs()

        var success = 0
        var failed = 0

        // Separate folders and files from selection
        val selectedFolders = mutableSetOf<String>()
        val selectedFiles = mutableSetOf<String>()

        for (path in entryPaths) {
            if (path.endsWith("/")) {
                selectedFolders.add(path)
            } else {
                selectedFiles.add(path)
                // Check if this represents a folder (even without trailing slash)
                val asFolder = "$path/"
                val hasChildren = allEntries.any { it.path.startsWith(asFolder) }
                if (hasChildren) {
                    selectedFolders.add(asFolder)
                }
            }
        }

        // Remove folders from selection if ANY of their children are explicitly selected
        val foldersToRemove = mutableSetOf<String>()
        for (folder in selectedFolders) {
            val hasExplicitChildren = selectedFiles.any { it.startsWith(folder) }
            if (hasExplicitChildren) {
                foldersToRemove.add(folder)
            }
        }
        selectedFolders.removeAll(foldersToRemove)

        // Build final paths to extract
        val pathsToExtract = mutableSetOf<String>()

        // Add all selected files
        pathsToExtract.addAll(selectedFiles)

        // For remaining folders (those without explicit children), add all their children
        for (folder in selectedFolders) {
            pathsToExtract.add(folder)
            val children = allEntries.filter { it.path.startsWith(folder) && it.path != folder }
            pathsToExtract.addAll(children.map { it.path })
        }

        Log.d("van.phuong.ArchiveReader", "extractZipEntries: input=${entryPaths.size}, folders=${selectedFolders.size}, files=${selectedFiles.size}, final=${pathsToExtract.size}")

        try {
            val zipFile = ZipFile(archiveFile)

            // Check if encrypted and set password
            if (zipFile.isEncrypted) {
                if (password == null) {
                    throw PasswordRequiredException("Archive file is password protected")
                }
                zipFile.setPassword(password.toCharArray())
            }

            // Extract each file
            for (entryPath in pathsToExtract) {
                try {
                    val fileHeader = zipFile.getFileHeader(entryPath)
                    if (fileHeader == null) {
                        Log.w("van.phuong.ArchiveReader", "File header not found: $entryPath")
                        // Don't count as failed - might be a path issue
                        continue
                    }

                    // Create directory structure but don't count directories
                    if (fileHeader.isDirectory) {
                        val outputDir = File(destDir, entryPath)
                        outputDir.mkdirs()
                        Log.d("van.phuong.ArchiveReader", "Created directory: ${outputDir.name}")
                        continue  // Don't count directories in success/failed
                    }

                    // Extract file to destination
                    val outputFile = File(destDir, entryPath)
                    outputFile.parentFile?.mkdirs()

                    zipFile.extractFile(fileHeader, destDir.absolutePath)

                    success++  // Only count actual files
                    Log.d("van.phuong.ArchiveReader", "Extracted file: ${outputFile.name}")
                } catch (e: ZipException) {
                    if (e.message?.contains("Wrong Password", ignoreCase = true) == true) {
                        throw InvalidPasswordException("Invalid password")
                    }
                    Log.e("van.phuong.ArchiveReader", "ZipException extracting: $entryPath - ${e.message}", e)
                    failed++  // Only count file failures
                } catch (e: Exception) {
                    Log.e("van.phuong.ArchiveReader", "Exception extracting: $entryPath - ${e.message}", e)
                    failed++  // Only count file failures
                }
            }
        } catch (e: PasswordRequiredException) {
            throw e
        } catch (e: InvalidPasswordException) {
            throw e
        } catch (e: ZipException) {
            Log.e(TAG, "ZipException during extraction: ${e.message}", e)
            if (e.message?.contains("Wrong Password", ignoreCase = true) == true) {
                throw InvalidPasswordException("Invalid password")
            }
            throw e
        }

        return Pair(success, failed)
    }

    /**
     * Xóa các entries khỏi archive bằng cách tạo file zip mới
     * zip4j không hỗ trợ in-place deletion, nên phải tạo temp file rồi replace
     *
     * @param archivePath đường dẫn tới file archive
     * @param entryPaths danh sách các entry paths cần xóa
     * @param allEntries tất cả entries trong archive (để resolve folders)
     * @param password Mật khẩu (optional)
     * @return Pair(success count, failed count)
     */
    fun removeEntries(
        archivePath: String,
        entryPaths: List<String>,
        allEntries: List<ArchiveEntry>,
        password: String? = null
    ): Pair<Int, Int> {
        val archiveFile = File(archivePath)
        if (!archiveFile.exists()) return Pair(0, entryPaths.size)

        return try {
            when (archiveFile.extension.lowercase()) {
                "zip" -> removeZipEntries(archiveFile, entryPaths, allEntries, password)
                else -> {
                    Log.w(TAG, "Unsupported archive format for removal: ${archiveFile.extension}")
                    Pair(0, entryPaths.size)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove entries from: $archivePath", e)
            Pair(0, entryPaths.size)
        }
    }

    private fun removeZipEntries(
        archiveFile: File,
        entryPaths: List<String>,
        allEntries: List<ArchiveEntry>,
        password: String? = null
    ): Pair<Int, Int> {
        // Expand folders to include all their children
        val pathsToRemove = mutableSetOf<String>()
        for (path in entryPaths) {
            pathsToRemove.add(path)

            // If it's a folder, add all children
            if (path.endsWith("/")) {
                val children = allEntries.filter { it.path.startsWith(path) && it.path != path }
                pathsToRemove.addAll(children.map { it.path })
            } else {
                // Check if this path represents a folder (even without trailing slash)
                val asFolder = if (path.endsWith("/")) path else "$path/"
                val children = allEntries.filter { it.path.startsWith(asFolder) }
                if (children.isNotEmpty()) {
                    pathsToRemove.addAll(children.map { it.path })
                }
            }
        }

        var success = 0
        var failed = 0

        try {
            val zipFile = ZipFile(archiveFile)

            // Check if encrypted and set password
            if (zipFile.isEncrypted && password != null) {
                zipFile.setPassword(password.toCharArray())
            }

            // Remove each file using zip4j's removeFile method
            for (entryPath in pathsToRemove) {
                try {
                    zipFile.removeFile(entryPath)
                    success++
                    Log.d(TAG, "Removed: $entryPath")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove entry: $entryPath", e)
                    failed++
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove entries", e)
            return Pair(0, pathsToRemove.size)
        }

        return Pair(success, failed)
    }

    /**
     * Extract single file to temp location for thumbnail preview
     * @param archivePath đường dẫn tới file archive
     * @param entryPath đường dẫn entry trong archive
     * @param tempDir thư mục temp để lưu file
     * @param password Mật khẩu (optional)
     * @return File nếu extract thành công, null nếu thất bại
     */
    fun extractToTemp(
        archivePath: String,
        entryPath: String,
        tempDir: File,
        password: String? = null
    ): File? {
        val archiveFile = File(archivePath)
        if (!archiveFile.exists()) return null

        return try {
            when (archiveFile.extension.lowercase()) {
                "zip" -> extractZipToTemp(archiveFile, entryPath, tempDir, password)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract to temp: $entryPath", e)
            null
        }
    }

    private fun extractZipToTemp(
        archiveFile: File,
        entryPath: String,
        tempDir: File,
        password: String? = null
    ): File? {
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            val zipFile = ZipFile(archiveFile)

            // Check if encrypted and set password
            if (zipFile.isEncrypted) {
                if (password == null) return null
                zipFile.setPassword(password.toCharArray())
            }

            val fileHeader = zipFile.getFileHeader(entryPath)
            if (fileHeader == null || fileHeader.isDirectory) return null

            // Create unique temp file name (use hash of archive path + entry path)
            val hash = "${archiveFile.absolutePath}::$entryPath".hashCode()
            val ext = entryPath.substringAfterLast('.', "")
            val tempFile = File(tempDir, "thumb_$hash.$ext")

            // Skip if already extracted
            if (tempFile.exists()) {
                return tempFile
            }

            // Extract to temp location
            zipFile.getInputStream(fileHeader).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Extracted to temp: ${tempFile.absolutePath}")
            return tempFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract to temp: $entryPath", e)
            return null
        }
    }
}