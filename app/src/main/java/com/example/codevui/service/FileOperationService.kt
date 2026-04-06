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
import androidx.core.app.NotificationCompat
import com.example.codevui.MainActivity
import com.example.codevui.R
import com.example.codevui.data.FileOperations
import com.example.codevui.data.FileOperations.OperationType
import com.example.codevui.data.FileOperations.ProgressState
import com.example.codevui.data.MediaStoreScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileOperationService : Service() {

    // ── Binder để ViewModel lấy state ────────────────────────────────────────

    inner class LocalBinder : Binder() {
        fun getService(): FileOperationService = this@FileOperationService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder = binder

    // ── Coroutine scope riêng — không phụ thuộc ViewModel ────────────────────

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var operationJob: Job? = null

    // ── State expose ra ngoài cho ViewModel collect ───────────────────────────

    private val _operationState = MutableStateFlow<ProgressState?>(null)
    val operationState: StateFlow<ProgressState?> = _operationState.asStateFlow()

    private val _operationTitle = MutableStateFlow("")
    val operationTitle: StateFlow<String> = _operationTitle.asStateFlow()

    // ── Notification ──────────────────────────────────────────────────────────

    private lateinit var notificationManager: NotificationManager
    private var currentActionName = ""
    private var destinationPath: String? = null  // Lưu folder đích để navigate
    private var compressedFilePath: String? = null  // Lưu compressed file path để preview

    companion object {
        const val CHANNEL_ID = "file_operation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_CANCEL = "com.example.codevui.ACTION_CANCEL_OPERATION"
        const val ACTION_OPEN_FOLDER = "com.example.codevui.ACTION_OPEN_FOLDER"
        const val ACTION_PREVIEW_ARCHIVE = "com.example.codevui.ACTION_PREVIEW_ARCHIVE"
        const val EXTRA_FOLDER_PATH = "folder_path"
        const val EXTRA_ARCHIVE_PATH = "archive_path"
        const val EXTRA_ARCHIVE_NAME = "archive_name"

        fun startCopy(context: Context, sourcePaths: List<String>, destDir: String) {
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = "COPY"
                putStringArrayListExtra("sources", ArrayList(sourcePaths))
                putExtra("dest", destDir)
            }
            context.startForegroundService(intent)
        }

        fun startMove(context: Context, sourcePaths: List<String>, destDir: String) {
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = "MOVE"
                putStringArrayListExtra("sources", ArrayList(sourcePaths))
                putExtra("dest", destDir)
            }
            context.startForegroundService(intent)
        }

        fun startCompress(context: Context, sourcePaths: List<String>, zipName: String?) {
            val intent = Intent(context, FileOperationService::class.java).apply {
                action = "COMPRESS"
                putStringArrayListExtra("sources", ArrayList(sourcePaths))
                if (zipName != null) putExtra("zipName", zipName)
            }
            context.startForegroundService(intent)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelOperation()
                return START_NOT_STICKY
            }
        }

        val sources = intent?.getStringArrayListExtra("sources") ?: return START_NOT_STICKY
        val dest = intent.getStringExtra("dest")
        val zipName = intent.getStringExtra("zipName")

        // Lưu destination path để dùng cho notification
        destinationPath = dest

        val (title, actionName, flow) = when (intent.action) {
            "COPY" -> Triple(
                "Đang sao chép...", "Sao chép",
                FileOperations.execute(sources, dest!!, OperationType.COPY)
            )
            "MOVE" -> Triple(
                "Đang di chuyển...", "Di chuyển",
                FileOperations.execute(sources, dest!!, OperationType.MOVE)
            )
            "COMPRESS" -> {
                // Compress lưu vào thư mục của file đầu tiên
                destinationPath = java.io.File(sources.first()).parent
                Triple(
                    "Đang nén mục...", "Nén",
                    FileOperations.compressToZip(sources, zipName)
                )
            }
            else -> return START_NOT_STICKY
        }

        currentActionName = actionName
        _operationTitle.value = title

        // Start foreground ngay với notification ban đầu
        startForeground(NOTIFICATION_ID, buildNotification(title, 0, 0, 0))

        operationJob?.cancel()
        operationJob = serviceScope.launch {
            flow.collect { state ->
                _operationState.value = state
                when (state) {
                    is ProgressState.Counting -> {
                        updateNotification(title, 0, 0, 0)
                    }
                    is ProgressState.Running -> {
                        updateNotification(title, state.percent, state.done, state.total)
                    }
                    is ProgressState.Done -> {
                        // Xóa notification progress TRƯỚC
                        notificationManager.cancel(NOTIFICATION_ID)

                        // Scan MediaStore sau khi operation hoàn thành
                        if (actionName == "Nén") {
                            // Compress: scan file zip mới tạo
                            val zipPath = state.outputPath
                            val zipFile = zipPath?.let { java.io.File(it) }
                            if (zipFile?.exists() == true) {
                                MediaStoreScanner.scanNewFile(
                                    applicationContext,
                                    parentPath = zipFile.parent,
                                    newFilePath = zipPath
                                )
                            }
                        } else {
                            // Copy / Move: scan thư mục đích + source để MediaStore cập nhật
                            MediaStoreScanner.scanSourceAndDest(
                                applicationContext,
                                sourcePath = sources.firstOrNull(),
                                destPath = destinationPath
                            )
                        }

                        // Build message kết quả
                        // Lưu compressed file path nếu là compress operation
                        if (actionName == "Nén" && state.outputPath != null) {
                            compressedFilePath = state.outputPath
                        }
                        val msg = buildDoneMessage(actionName, state)

                        // Hiện notification kết quả
                        showResultNotification(msg, actionName == "Nén")
                        operationJob = null
                        stopSelf()
                    }
                    is ProgressState.Error -> {
                        // Xóa notification progress
                        notificationManager.cancel(NOTIFICATION_ID)
                        // Error: vẫn scan phòng trường hợp có file được tạo trước khi lỗi
                        destinationPath?.let { dest ->
                            MediaStoreScanner.scanDirectory(applicationContext, dest)
                        }
                        val msg = "Lỗi: ${state.message}"
                        showResultNotification(msg, false)
                        operationJob = null
                        stopSelf()
                    }
                    else -> {}
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ── Public API cho ViewModel ──────────────────────────────────────────────

    fun cancelOperation() {
        operationJob?.cancel()
        operationJob = null
        _operationState.value = null
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    fun isRunning(): Boolean {
        val s = _operationState.value
        return s is ProgressState.Running || s is ProgressState.Counting
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "File Operations",
            NotificationManager.IMPORTANCE_LOW // LOW = không có âm thanh
        ).apply {
            description = "Tiến trình copy/move/nén file"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        title: String,
        percent: Int,
        done: Int,
        total: Int
    ): Notification {
        // Tap notification → mở app
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Nút Thoát trong notification
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FileOperationService::class.java).apply {
                action = ACTION_CANCEL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (total > 0) "$done/$total" else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setProgress(100, percent, total == 0) // indeterminate khi chưa biết total
            .setContentIntent(openAppIntent)
            .addAction(0, "Thoát", cancelIntent)
            .setOngoing(true)         // không swipe được
            .setOnlyAlertOnce(true)   // không rung/sound mỗi lần update
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, percent: Int, done: Int, total: Int) {
        val notification = buildNotification(title, percent, done, total)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showResultNotification(message: String, isCompressOperation: Boolean = false) {
        // Tạo PendingIntent để mở folder đích khi tap notification
        val openFolderIntent = destinationPath?.let { path ->
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).apply {
                    action = ACTION_OPEN_FOLDER
                    putExtra(EXTRA_FOLDER_PATH, path)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("File của bạn")
            .setContentText(message)
            .setContentIntent(openFolderIntent) // Tap notification → mở folder
            .setAutoCancel(true)
            .setTimeoutAfter(5000) // 5 giây
            .setSilent(true)

        // Add preview action for compress operations
        if (isCompressOperation && compressedFilePath != null) {
            val file = java.io.File(compressedFilePath!!)
            val previewIntent = PendingIntent.getActivity(
                this, 1,
                Intent(this, MainActivity::class.java).apply {
                    action = ACTION_PREVIEW_ARCHIVE
                    putExtra(EXTRA_ARCHIVE_PATH, compressedFilePath)
                    putExtra(EXTRA_ARCHIVE_NAME, file.name)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Xem trước", previewIntent)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun buildDoneMessage(actionName: String, state: ProgressState.Done): String {
        return when {
            state.failed == 0 -> "$actionName ${state.success} mục thành công"
            state.success == 0 -> "$actionName thất bại (${state.failed} mục lỗi)"
            else -> "$actionName ${state.success} thành công, ${state.failed} mục lỗi"
        }
    }
}