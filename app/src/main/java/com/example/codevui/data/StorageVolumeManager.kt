package com.example.codevui.data

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume as AndroidStorageVolume
import com.example.codevui.util.Logger
import com.example.codevui.data.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * StorageVolume — thông tin 1 ổ lưu trữ
 * Mirror từ MyFiles StorageVolumeManager
 */
data class StorageVolume(
    val domainType: Int,           // DomainType constant (INTERNAL=1, SD=2, USB=...)
    val path: String,              // Root path (VD: /storage/emulated/0)
    val displayName: String,        // Tên hiển thị (VD: "Bộ nhớ trong", "Thẻ SD")
    val description: String,        // Mô tả (VD: "Bộ nhớ trong", "SD Card")
    val isRemovable: Boolean,       // Có thể tháo lắp không (SD/USB)
    val isEmulated: Boolean,       // Là internal emulated storage
    val isMounted: Boolean,         // Có đang mounted không
    val totalBytes: Long,          // Tổng dung lượng
    val freeBytes: Long             // Dung lượng trống
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedPercent: Int get() = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0
}

/**
 * Singleton quản lý các ổ lưu trữ (Internal, SD Card, USB).
 * Mirror từ MyFiles StorageVolumeManager.
 *
 * Dùng StorageManager.getStorageVolumes() (API 24+) để lấy danh sách ổ.
 * Fallback về Environment.getExternalStorageDirectory() cho các API cũ.
 */
object StorageVolumeManager {

    private val log = Logger("StorageVolumeManager")

    // ── DomainType constants (đồng nhất với MyFiles DomainType) ─────────────────
    const val UNKNOWN = 0
    const val INTERNAL_STORAGE = 1
    const val EXTERNAL_SD = 2
    const val EXTERNAL_USB_DRIVE_A = 3
    const val EXTERNAL_USB_DRIVE_B = 4
    const val EXTERNAL_USB_DRIVE_C = 5
    const val EXTERNAL_USB_DRIVE_D = 6
    const val EXTERNAL_USB_DRIVE_E = 7
    const val EXTERNAL_USB_DRIVE_F = 8
    const val INTERNAL_APP_CLONE = 9

    // USB drives range
    const val EXTERNAL_USB_DRIVE_START = EXTERNAL_USB_DRIVE_A
    const val EXTERNAL_USB_DRIVE_END = EXTERNAL_USB_DRIVE_F

    private val _volumes = MutableStateFlow<List<StorageVolume>>(emptyList())
    val volumes: StateFlow<List<StorageVolume>> = _volumes.asStateFlow()

    private var initialized = false

    /**
     * Refresh danh sách ổ lưu trữ
     */
    fun refresh(context: Context) {
        log.d("refresh: start")
        val volumeList = loadVolumes(context)
        _volumes.value = volumeList
        log.d("refresh: found ${volumeList.size} volumes — $volumeList")
        initialized = true
    }

    /**
     * Lấy danh sách ổ đang mounted (bỏ qua ổ không mount)
     */
    fun getMountedVolumes(context: Context): List<StorageVolume> {
        if (!initialized) refresh(context)
        return _volumes.value.filter { it.isMounted }
    }

    /**
     * Lấy ổ Internal Storage
     */
    fun getInternalStorage(context: Context): StorageVolume? {
        return getMountedVolumes(context).find { it.domainType == INTERNAL_STORAGE }
    }

    /**
     * Lấy SD Card (nếu có)
     */
    fun getSdCard(context: Context): StorageVolume? {
        return getMountedVolumes(context).find { it.domainType == EXTERNAL_SD }
    }

    /**
     * Lấy tất cả USB drives đang connected
     */
    fun getUsbDrives(context: Context): List<StorageVolume> {
        return getMountedVolumes(context).filter { it.domainType in EXTERNAL_USB_DRIVE_START..EXTERNAL_USB_DRIVE_END }
    }

    /**
     * Kiểm tra ổ có đang mounted không
     */
    fun mounted(domainType: Int): Boolean {
        return _volumes.value.any { it.domainType == domainType && it.isMounted }
    }

    /**
     * Kiểm tra ổ có đang connected không (kể cả chưa mount)
     */
    fun connected(domainType: Int): Boolean {
        return _volumes.value.any { it.domainType == domainType }
    }

    /**
     * Kiểm tra là internal storage
     */
    fun isInternal(domainType: Int): Boolean = domainType == INTERNAL_STORAGE || domainType == INTERNAL_APP_CLONE

    /**
     * Kiểm tra là SD card
     */
    fun isSd(domainType: Int): Boolean = domainType == EXTERNAL_SD

    /**
     * Kiểm tra là USB drive
     */
    fun isUsb(domainType: Int): Boolean = domainType in EXTERNAL_USB_DRIVE_START..EXTERNAL_USB_DRIVE_END

    /**
     * Kiểm tra là removable storage (SD hoặc USB)
     */
    fun isRemovable(domainType: Int): Boolean = isSd(domainType) || isUsb(domainType)

    /**
     * Lấy StorageVolume theo domainType
     */
    fun getVolume(domainType: Int): StorageVolume? {
        return _volumes.value.find { it.domainType == domainType }
    }

    /**
     * Lấy root path của 1 domain
     */
    fun getRootPath(domainType: Int): String? {
        return getVolume(domainType)?.path
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private: load volumes from system
    // ══════════════════════════════════════════════════════════════════════════

    private fun loadVolumes(context: Context): List<StorageVolume> {
        val result = mutableListOf<StorageVolume>()

        // ── 1. Internal Storage ───────────────────────────────────────────
        val internalPath = Environment.getExternalStorageDirectory().absolutePath
        log.d("loadVolumes: internalPath=$internalPath")
        val internalStats = getStorageStats(internalPath)
        val correctedTotal = FileRepository.correctStorageSize(internalStats.total)
        log.d("loadVolumes: internalStats rawTotal=${internalStats.total}, correctedTotal=$correctedTotal")
        result.add(
            StorageVolume(
                domainType = INTERNAL_STORAGE,
                path = internalPath,
                displayName = "Bộ nhớ trong",
                description = "Bộ nhớ trong",
                isRemovable = false,
                isEmulated = Environment.isExternalStorageEmulated(),
                isMounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED,
                totalBytes = correctedTotal,
                freeBytes = internalStats.free
            )
        )

        // ── 2. App Clone Storage (Samsung) ────────────────────────────────
        // /data/user/19999/ hoặc /data/user/0/ android payload clone
        // Skip — cần thêm check Samsung-specific

        // ── 3. SD Card & USB via StorageManager ──────────────────────────
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val storageVolumes = storageManager.storageVolumes

                for (volume in storageVolumes) {
                    val volInfo = parseStorageVolume(context, volume)
                    if (volInfo != null) {
                        // Tránh duplicate internal
                        if (volInfo.domainType != INTERNAL_STORAGE || result.none { it.domainType == INTERNAL_STORAGE }) {
                            result.add(volInfo)
                        }
                    }
                }
            } catch (e: Exception) {
                log.e("loadVolumes: failed to get storage volumes", e)
            }
        }

        // ── 4. Legacy: scan /storage/ directory cho extra mounts ──────────
        result.addAll(loadLegacyVolumes())

        // Deduplicate by domainType
        return result.groupBy { it.domainType }.mapValues { (_, list) -> list.first() }.values.toList()
    }

    /**
     * Parse Android StorageVolume thành StorageVolume
     */
    private fun parseStorageVolume(context: Context, volume: AndroidStorageVolume): StorageVolume? {
        return try {
            val path = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                volume.directory?.absolutePath
            } else {
                // reflect: StorageVolume.getPath()
                val method = volume.javaClass.getMethod("getPath")
                method.invoke(volume) as? String
            } ?: return null

            val isRemovable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                volume.isRemovable
            } else {
                val method = volume.javaClass.getMethod("isRemovable")
                method.invoke(volume) as? Boolean ?: false
            }

            val isEmulated = volume.isEmulated
            val displayName = volume.getDescription(context) ?: path.substringAfterLast("/")

            val domainType = when {
                isEmulated && !isRemovable -> INTERNAL_STORAGE
                path.contains("/storage/internal") -> INTERNAL_STORAGE
                path.contains("extCard") || path.contains("/sdcard") -> EXTERNAL_SD
                path.contains("/usb") -> {
                    // Determine USB slot (A-F)
                    val usbLetter = path.substringAfterLast("/").filter { it.isLetter() }.firstOrNull()?.uppercaseChar() ?: 'A'
                    EXTERNAL_USB_DRIVE_A + (usbLetter - 'A')
                }
                else -> UNKNOWN
            }

            val stats = getStorageStats(path)

            StorageVolume(
                domainType = domainType,
                path = path,
                displayName = displayName,
                description = displayName,
                isRemovable = isRemovable,
                isEmulated = isEmulated,
                isMounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED,
                totalBytes = stats.total,
                freeBytes = stats.free
            )
        } catch (e: Exception) {
            log.e("parseStorageVolume: failed", e)
            null
        }
    }

    /**
     * Load thêm volumes từ /storage/ directory (legacy fallback)
     */
    private fun loadLegacyVolumes(): List<StorageVolume> {
        val result = mutableListOf<StorageVolume>()
        val storageDir = java.io.File("/storage")

        if (!storageDir.exists() || !storageDir.canRead()) return result

        for (dir in storageDir.listFiles() ?: return result) {
            if (!dir.isDirectory || dir.name == "self" || dir.name == "emulated") continue

            val path = dir.absolutePath
            val domainType = when {
                dir.name.startsWith("ext") || dir.name == "sdcard" -> EXTERNAL_SD
                dir.name.startsWith("usb") -> {
                    val numStr = dir.name.removePrefix("usb")
                    val num = numStr.toIntOrNull() ?: 0
                    EXTERNAL_USB_DRIVE_A + num
                }
                else -> continue
            }

            val stats = getStorageStats(path)
            result.add(
                StorageVolume(
                    domainType = domainType,
                    path = path,
                    displayName = when (domainType) {
                        EXTERNAL_SD -> "Thẻ SD"
                        else -> "USB ${dir.name.removePrefix("usb")}"
                    },
                    description = when (domainType) {
                        EXTERNAL_SD -> "Thẻ SD"
                        else -> "USB Drive"
                    },
                    isRemovable = true,
                    isEmulated = false,
                    isMounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED,
                    totalBytes = stats.total,
                    freeBytes = stats.free
                )
            )
        }

        return result
    }

    /**
     * Lấy stats (total/free) từ StatFs
     */
    private fun getStorageStats(path: String): StorageStats {
        return try {
            val stat = android.os.StatFs(path)
            StorageStats(
                total = stat.totalBytes,
                free = stat.availableBytes
            )
        } catch (e: Exception) {
            log.e("getStorageStats: failed for $path", e)
            StorageStats(0L, 0L)
        }
    }

    private data class StorageStats(val total: Long, val free: Long)

    /**
     * Check xem có USB storage connected không
     */
    fun isUsbStorageConnected(): Boolean {
        return _volumes.value.any { isUsb(it.domainType) }
    }
}
