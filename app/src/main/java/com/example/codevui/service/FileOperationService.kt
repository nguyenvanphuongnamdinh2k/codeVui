package com.example.codevui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.codevui.MainActivity
import com.example.codevui.R
import com.example.codevui.data.ArchiveReader
import com.example.codevui.data.FileOperations
import com.example.codevui.data.FileOperations.OperationType
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.data.MediaStoreScanner
import com.example.codevui.model.ArchiveEntry
import com.example.codevui.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * ForegroundService quản lý multi-operation (copy/move/compress/extract).
 *
 * Mỗi operation có ID riêng, chạy song song tối đa MAX_OPERATION_COUNT.
 * ViewModel observe operationsMap để hiển thị progress cho từng operation.
 *
 * Pattern từ Samsung MyFiles: OperationManager + OperationData + WakeLock.
 */
class FileOperationService : Service() {

    // ── Binder ──────────────────────────────────────────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): FileOperationService = this@FileOperationService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ── Coroutine scope ─────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Multi-operation state ───────────────────────────────────────────────

    /**
     * Mỗi operation đang chạy: operationId → OperationInfo.
     * ConcurrentHashMap cho thread-safety (giống MyFiles OperationManager.operationArray).
     */
    private val operationJobs = ConcurrentHashMap<Int, Job>()

    /**
     * State expose ra ViewModel — map of operationId → OperationInfo.
     * ViewModel collect flow này để hiển thị danh sách operation đang chạy.
     */
    private val _operationsMap = MutableStateFlow<Map<Int, OperationInfo>>(emptyMap())
    val operationsMap: StateFlow<Map<Int, OperationInfo>> = _operationsMap.asStateFlow()

    /**
     * Notification ─────────────────────────────────────────────────────────
     */
    private lateinit var notificationManager: NotificationManager

    /**
     * WakeLock — giữ CPU không ngủ khi operation chạy (MyFiles pattern).
     */
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private val log = Logger("FileOperationService")

        const val CHANNEL_ID = "file_operation_channel"
        const val SUMMARY_NOTIFICATION_ID = 99999 // Summary notification (MyFiles pattern)

        const val ACTION_CANCEL = "com.example.codevui.ACTION_CANCEL_OPERATION"
        const val ACTION_OPEN_FOLDER = "com.example.codevui.ACTION_OPEN_FOLDER"
        const val ACTION_PREVIEW_ARCHIVE = "com.example.codevui.ACTION_PREVIEW_ARCHIVE"
        const val EXTRA_FOLDER_PATH = "folder_path"
        const val EXTRA_ARCHIVE_PATH = "archive_path"
        const val EXTRA_ARCHIVE_NAME = "archive_name"
        const val EXTRA_OPERATION_ID = "operation_id"

        /** Tối đa operation chạy song song (MyFiles = 5, CodeVui giữ 3 cho nhẹ) */
        const val MAX_OPERATION_COUNT = 3

        /** Throttle notification update (ms) — tránh spam hệ thống */
        private const val NOTIFICATION_THROTTLE_MS = 500L

        private val nextOperationId = AtomicInteger(1)

        fun startCopy(context: Context, sourcePaths: List<String>, destDir: String): Int {
            val opId = nextOperationId.getAndIncrement()
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = "COPY"
                putStringArrayListExtra("sources", ArrayList(sourcePaths))
                putExtra("dest", destDir)
                putExtra(EXTRA_OPERATION_ID, opId)
            }
            context.startForegroundService(intent)
            return opId
        }

        fun startMove(context: Context, sourcePaths: List<String>, destDir: String): Int {
            val opId = nextOperationId.getAndIncrement()
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = "MOVE"
                putStringArrayListExtra("sources", ArrayList(sourcePaths))
                putExtra("dest", destDir)
                putExtra(EXTRA_OPERATION_ID, opId)
            }
            context.startForegroundService(intent)
            return opId
        }

        fun startCompress(context: Context, sourcePaths: List<String>, zipName: String?): Int {
            val opId = nextOperationId.getAndIncrement()
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = "COMPRESS"
                putStringArrayListExtra("sources", ArrayList(sourcePaths))
                if (zipName != null) putExtra("zipName", zipName)
                putExtra(EXTRA_OPERATION_ID, opId)
            }
            context.startForegroundService(intent)
            return opId
        }

        /**
         * Move files to trash — chạy qua service để có notification + progress bar.
         * Nhanh hơn đáng kể so với chạy trực tiếp trên main coroutine vì dùng ForegroundService.
         */
        fun startTrash(context: Context, sourcePaths: List<String>): Int {
            val opId = nextOperationId.getAndIncrement()
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = "TRASH"
                putStringArrayListExtra("sources", ArrayList(sourcePaths))
                putExtra(EXTRA_OPERATION_ID, opId)
            }
            context.startForegroundService(intent)
            return opId
        }

        fun startExtract(
            context: Context,
            archivePath: String,
            entryPaths: List<String>,
            destPath: String,
            allEntries: List<ArchiveEntry>,
            password: String? = null
        ): Int {
            val opId = nextOperationId.getAndIncrement()
            // Lưu allEntries vào static cache vì Intent không chứa được object lớn
            pendingExtractData[opId] = ExtractData(archivePath, entryPaths, destPath, allEntries, password)
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = "EXTRACT"
                putExtra(EXTRA_OPERATION_ID, opId)
            }
            context.startForegroundService(intent)
            return opId
        }

        /** Temp cache cho extract data — xóa sau khi đọc xong */
        private val pendingExtractData = ConcurrentHashMap<Int, ExtractData>()
    }

    /** Data class chứa thông tin extract — dùng nội bộ */
    private data class ExtractData(
        val archivePath: String,
        val entryPaths: List<String>,
        val destPath: String,
        val allEntries: List<ArchiveEntry>,
        val password: String?
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        // Handle cancel action
        if (action == ACTION_CANCEL) {
            val opId = intent.getIntExtra(EXTRA_OPERATION_ID, -1)
            if (opId > 0) cancelOperation(opId) else cancelAllOperations()
            return START_NOT_STICKY
        }

        val opId = intent.getIntExtra(EXTRA_OPERATION_ID, -1)
        if (opId < 0) return START_NOT_STICKY

        // Kiểm tra giới hạn multi-operation
        if (operationJobs.size >= MAX_OPERATION_COUNT) {
            log.w("Max operation count reached ($MAX_OPERATION_COUNT), rejecting opId=$opId")
            return START_NOT_STICKY
        }

        // Start foreground ngay lập tức (Android yêu cầu trong 5s)
        startForeground(SUMMARY_NOTIFICATION_ID, buildSummaryNotification())
        acquireWakeLock()

        when (action) {
            "COPY" -> startCopyOrMove(intent, opId, OperationType.COPY)
            "MOVE" -> startCopyOrMove(intent, opId, OperationType.MOVE)
            "COMPRESS" -> startCompressOp(intent, opId)
            "EXTRACT" -> startExtractOp(opId)
            "TRASH" -> startTrashOp(intent, opId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    // ── Operation starters ────────────────────────────────────────────────────

    private fun startCopyOrMove(intent: Intent, opId: Int, type: OperationType) {
        val sources = intent.getStringArrayListExtra("sources") ?: return
        val dest = intent.getStringExtra("dest") ?: return

        val title = if (type == OperationType.COPY) "Đang sao chép..." else "Đang di chuyển..."
        val actionName = if (type == OperationType.COPY) "Sao chép" else "Di chuyển"
        val flow = FileOperations.execute(sources, dest, type)

        launchOperation(opId, title, actionName, dest, flow) { state ->
            // Scan MediaStore sau khi hoàn thành
            if (state is ProgressState.Done) {
                MediaStoreScanner.scanSourceAndDest(
                    applicationContext,
                    sourcePath = sources.firstOrNull(),
                    destPath = dest
                )
            } else if (state is ProgressState.Error) {
                MediaStoreScanner.scanDirectory(applicationContext, dest)
            }
        }
    }

    private fun startCompressOp(intent: Intent, opId: Int) {
        val sources = intent.getStringArrayListExtra("sources") ?: return
        val zipName = intent.getStringExtra("zipName")
        val destDir = java.io.File(sources.first()).parent ?: return

        val flow = FileOperations.compressToZip(sources, zipName)

        launchOperation(opId, "Đang nén mục...", "Nén", destDir, flow) { state ->
            if (state is ProgressState.Done) {
                val zipPath = state.outputPath
                val zipFile = zipPath?.let { java.io.File(it) }
                if (zipFile?.exists() == true) {
                    MediaStoreScanner.scanNewFile(
                        applicationContext,
                        parentPath = zipFile.parent,
                        newFilePath = zipPath
                    )
                }
            }
        }
    }

    private fun startExtractOp(opId: Int) {
        val data = pendingExtractData.remove(opId)
        if (data == null) {
            log.e("No extract data found for opId=$opId")
            return
        }

        val flow = FileOperations.extractArchive(
            archivePath = data.archivePath,
            entryPaths = data.entryPaths,
            destPath = data.destPath,
            allEntries = data.allEntries,
            password = data.password
        )

        launchOperation(opId, "Đang giải nén...", "Giải nén", data.destPath, flow) { state ->
            if (state is ProgressState.Done && state.success > 0) {
                MediaStoreScanner.scanNewFile(
                    applicationContext,
                    parentPath = data.destPath,
                    newFilePath = null
                )
            }
        }
    }

    private fun startTrashOp(intent: Intent, opId: Int) {
        val sources = intent.getStringArrayListExtra("sources") ?: return

        val flow = FileOperations.trashFiles(applicationContext, sources)

        launchOperation(opId, "Đang xóa...", "Xóa", sources.firstOrNull() ?: "", flow) { state ->
            // TrashManager đã scan bên trong moveToTrash()
        }
    }

    // ── Core launcher — quản lý 1 operation ──────────────────────────────────

    private fun launchOperation(
        opId: Int,
        title: String,
        actionName: String,
        destPath: String,
        flow: kotlinx.coroutines.flow.Flow<ProgressState>,
        onFinish: suspend (ProgressState) -> Unit
    ) {
        // Tạo OperationInfo ban đầu
        updateOperationInfo(opId, OperationInfo(
            id = opId,
            title = title,
            actionName = actionName,
            destPath = destPath,
            state = ProgressState.Counting
        ))

        // Hiện notification riêng cho operation này
        showOperationNotification(opId, title, 0, 0, 0)

        val job = serviceScope.launch {
            var lastNotifyTime = 0L

            flow.collect { state ->
                // Cập nhật state trong map
                updateOperationInfo(opId) { it.copy(state = state) }

                when (state) {
                    is ProgressState.Counting -> {
                        showOperationNotification(opId, title, 0, 0, 0)
                    }
                    is ProgressState.Running -> {
                        // Throttle notification updates (MyFiles: 20ms cho UI, 1300ms cho notification)
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime > NOTIFICATION_THROTTLE_MS) {
                            lastNotifyTime = now
                            showOperationNotification(opId, title, state.percent, state.done, state.total)
                        }
                    }
                    is ProgressState.Done -> {
                        onFinish(state)
                        notificationManager.cancel(opId)
                        showResultNotification(opId, actionName, destPath, state)
                        finishOperation(opId)
                    }
                    is ProgressState.Error -> {
                        onFinish(state)
                        notificationManager.cancel(opId)
                        showErrorNotification(opId, state.message)
                        finishOperation(opId)
                    }
                }
            }
        }

        operationJobs[opId] = job
    }

    // ── Operation state management ───────────────────────────────────────────

    private fun updateOperationInfo(opId: Int, info: OperationInfo) {
        _operationsMap.value = _operationsMap.value.toMutableMap().apply { put(opId, info) }
    }

    private fun updateOperationInfo(opId: Int, updater: (OperationInfo) -> OperationInfo) {
        val current = _operationsMap.value[opId] ?: return
        updateOperationInfo(opId, updater(current))
    }

    private fun finishOperation(opId: Int) {
        operationJobs.remove(opId)
        // Giữ lại OperationInfo (với Done/Error state) để ViewModel có thể đọc kết quả
        // ViewModel sẽ gọi clearOperation() sau khi xử lý xong

        // Nếu không còn operation nào đang chạy → stop service
        if (operationJobs.isEmpty()) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        } else {
            // Cập nhật summary notification
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, buildSummaryNotification())
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun cancelOperation(opId: Int) {
        operationJobs[opId]?.cancel()
        operationJobs.remove(opId)
        notificationManager.cancel(opId)
        _operationsMap.value = _operationsMap.value.toMutableMap().apply { remove(opId) }

        if (operationJobs.isEmpty()) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cancelAllOperations() {
        operationJobs.values.forEach { it.cancel() }
        operationJobs.clear()
        _operationsMap.value = emptyMap()
        notificationManager.cancelAll()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** ViewModel gọi sau khi đã xử lý kết quả (snackbar hiển thị xong v.v.) */
    fun clearOperation(opId: Int) {
        _operationsMap.value = _operationsMap.value.toMutableMap().apply { remove(opId) }
    }

    fun isRunning(): Boolean = operationJobs.isNotEmpty()

    fun canStartOperation(): Boolean = operationJobs.size < MAX_OPERATION_COUNT

    // ── WakeLock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "CodeVui::FileOperation"
            ).apply {
                acquire(60 * 60 * 1000L) // Timeout 1h cho safety
            }
            log.d("WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                log.d("WakeLock released")
            }
        }
        wakeLock = null
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "File Operations",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tiến trình copy/move/nén/giải nén file"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /** Summary notification — hiển thị khi có nhiều operation chạy */
    private fun buildSummaryNotification(): Notification {
        val count = operationJobs.size
        val text = if (count > 1) "Đang chạy $count thao tác" else "Đang xử lý file..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CodeVui")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setGroup("file_operations")
            .setGroupSummary(true)
            .build()
    }

    /** Notification riêng cho từng operation — có progress bar + nút cancel */
    private fun showOperationNotification(
        opId: Int,
        title: String,
        percent: Int,
        done: Int,
        total: Int
    ) {
        val cancelIntent = PendingIntent.getService(
            this, opId,
            Intent(this, FileOperationService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_OPERATION_ID, opId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (total > 0) "$done/$total" else "Đang chuẩn bị..."

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setProgress(100, percent, total == 0)
            .addAction(0, "Thoát", cancelIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setGroup("file_operations")
            .build()

        notificationManager.notify(opId, notification)
    }

    private fun showResultNotification(
        opId: Int,
        actionName: String,
        destPath: String,
        state: ProgressState.Done
    ) {
        val message = when {
            state.failed == 0 -> "$actionName ${state.success} mục thành công"
            state.success == 0 -> "$actionName thất bại (${state.failed} mục lỗi)"
            else -> "$actionName ${state.success} thành công, ${state.failed} mục lỗi"
        }

        val openFolderIntent = PendingIntent.getActivity(
            this, opId,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_FOLDER
                putExtra(EXTRA_FOLDER_PATH, destPath)
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("File của bạn")
            .setContentText(message)
            .setContentIntent(openFolderIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .setSilent(true)

        // Preview action cho compress
        if (actionName == "Nén" && state.outputPath != null) {
            val file = java.io.File(state.outputPath)
            val previewIntent = PendingIntent.getActivity(
                this, opId + 10000,
                Intent(this, MainActivity::class.java).apply {
                    action = ACTION_PREVIEW_ARCHIVE
                    putExtra(EXTRA_ARCHIVE_PATH, state.outputPath)
                    putExtra(EXTRA_ARCHIVE_NAME, file.name)
                    this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Xem trước", previewIntent)
        }

        // Dùng unique ID cho result notification
        notificationManager.notify(opId + 20000, builder.build())
    }

    private fun showErrorNotification(opId: Int, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Lỗi")
            .setContentText(message)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .setSilent(true)
            .build()

        notificationManager.notify(opId + 20000, notification)
    }
}

/**
 * Thông tin 1 operation đang/đã chạy — expose ra ViewModel.
 */
data class OperationInfo(
    val id: Int,
    val title: String,
    val actionName: String,
    val destPath: String,
    val state: ProgressState
)
