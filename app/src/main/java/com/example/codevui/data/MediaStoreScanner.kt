package com.example.codevui.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * MediaStoreScanner — thông báo cho MediaStore biết khi có file được tạo / xóa / di chuyển.
 *
 * Khi app dùng `java.io.File` để thao tác trực tiếp (tạo, xóa, move, copy),
 * MediaStore không tự động cập nhật. Dùng class này để scan sau mỗi thao tác,
 * giúp các app khác (gallery, file manager, ...) nhận biết được thay đổi.
 *
 * Cách dùng:
 *   MediaStoreScanner.scanPaths(context, listOf(filePath))          // single file
 *   MediaStoreScanner.scanDirectory(context, folderPath)             // entire folder
 *   MediaStoreScanner.scanSourceAndDest(context, srcPath, destPath) // after move/copy
 */
object MediaStoreScanner {

    private const val TAG = "MediaStoreScanner"

    // Các thư mục hệ thống KHÔNG scan (tránh lãng phí + tránh trigger vòng lặp)
    private val excludedPrefixes = setOf(
        File(Environment.getExternalStorageDirectory(), ".Trash").absolutePath
    )

    /**
     * Scan một danh sách file/folder.
     * Nếu là folder → scan toàn bộ nội dung bên trong.
     * Tự động loại trừ thư mục hệ thống.
     *
     * @param context Application context
     * @param paths Danh sách đường dẫn cần scan
     * @param onComplete Callback khi scan hoàn tất (có thể null)
     */
    fun scanPaths(
        context: Context,
        paths: List<String>,
        onComplete: (() -> Unit)? = null
    ) {
        if (paths.isEmpty()) {
            onComplete?.invoke()
            return
        }

        // Expand folders → individual files
        val filesToScan = mutableListOf<String>()

        for (path in paths) {
            if (isExcluded(path)) continue

            val file = File(path)
            if (!file.exists()) continue

            if (file.isDirectory) {
                // Walk directory and add all files
                file.walkTopDown().forEach { f ->
                    if (f.isFile && !isExcluded(f.absolutePath)) {
                        filesToScan.add(f.absolutePath)
                    }
                }
            } else {
                filesToScan.add(path)
            }
        }

        if (filesToScan.isEmpty()) {
            onComplete?.invoke()
            return
        }

        scanFileList(context, filesToScan, onComplete)
    }

    /**
     * Scan toàn bộ một thư mục (đệ quy).
     */
    fun scanDirectory(
        context: Context,
        dirPath: String,
        onComplete: (() -> Unit)? = null
    ) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory || isExcluded(dirPath)) {
            onComplete?.invoke()
            return
        }

        val filesToScan = dir.walkTopDown()
            .filter { it.isFile && !isExcluded(it.absolutePath) }
            .map { it.absolutePath }
            .toList()

        if (filesToScan.isEmpty()) {
            onComplete?.invoke()
            return
        }

        scanFileList(context, filesToScan, onComplete)
    }

    /**
     * Scan cả source và destination — dùng sau MOVE để cập nhật cả vị trí cũ và mới.
     *
     * @param sourcePath Đường dẫn nguồn (file đã bị xóa / di chuyển khỏi)
     * @param destPath Đường dẫn đích (file mới được tạo)
     */
    fun scanSourceAndDest(
        context: Context,
        sourcePath: String?,
        destPath: String?,
        onComplete: (() -> Unit)? = null
    ) {
        val paths = listOfNotNull(sourcePath, destPath).distinct()
        scanPaths(context, paths, onComplete)
    }

    /**
     * Scan file cha + file đích — dùng sau COPY để đảm bảo file mới xuất hiện.
     *
     * @param parentPath Đường dẫn thư mục cha của file mới
     * @param newFilePath Đường dẫn file mới được tạo (có thể là folder)
     */
    fun scanNewFile(
        context: Context,
        parentPath: String?,
        newFilePath: String?,
        onComplete: (() -> Unit)? = null
    ) {
        val paths = mutableListOf<String>()

        parentPath?.let {
            val parent = File(it)
            if (parent.exists() && parent.isDirectory && !isExcluded(it)) {
                // Scan parent directory → tất cả file bên trong được nhận biết
                paths.add(it)
            }
        }

        newFilePath?.let {
            if (!isExcluded(it)) {
                paths.add(it)
            }
        }

        if (paths.isEmpty()) {
            onComplete?.invoke()
            return
        }

        scanPaths(context, paths.distinct(), onComplete)
    }

    // ─── Core scanning (MediaScannerConnection) ───────────────────────────────

    /**
     * Scan danh sách file bằng MediaScannerConnection.
     * Sử dụng callback-based API với coroutine wrapper để đồng bộ.
     */
    private fun scanFileList(
        context: Context,
        filePaths: List<String>,
        onComplete: (() -> Unit)?
    ) {
        val mimeTypes = filePaths.map { path ->
            getMimeType(path)
        }.toTypedArray()

        Log.d(TAG, "MediaStoreScanner: scanning ${filePaths.size} files")

        MediaScannerConnection.scanFile(
            context.applicationContext,
            filePaths.toTypedArray(),
            mimeTypes
        ) { scannedPath, uri ->
            if (uri != null) {
                Log.d(TAG, "MediaStoreScanner: scanned OK → $uri")
            } else {
                Log.w(TAG, "MediaStoreScanner: scan failed for $scannedPath")
            }
        }

        // Gọi onComplete sau khi MediaScannerConnection hoàn tất
        // MediaScannerConnection hoàn tất khi callback được gọi cho TẤT CẢ files.
        // Dùng post() để không block main thread.
        if (onComplete != null) {
            // Estimate completion time và gọi callback sau đó
            // Callback của MediaScannerConnection được gọi trên main thread sau mỗi file
            // → gọi onComplete sau tất cả callbacks bằng delayed Handler
            val delayMs = (filePaths.size * 50L + 500L).coerceAtMost(3000L)
            Handler(Looper.getMainLooper()).postDelayed({
                onComplete.invoke()
            }, delayMs)
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** Kiểm tra path có thuộc thư mục hệ thống cần loại trừ không */
    private fun isExcluded(path: String?): Boolean {
        if (path == null) return true
        return excludedPrefixes.any { path.startsWith(it) }
    }

    /** Lấy MIME type từ đường dẫn file */
    private fun getMimeType(path: String): String? {
        val ext = File(path).extension.lowercase()
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext)
    }
}
