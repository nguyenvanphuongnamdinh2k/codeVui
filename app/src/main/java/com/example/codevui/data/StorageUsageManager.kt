package com.example.codevui.data

import android.content.Context
import android.os.StatFs
import com.example.codevui.util.Logger

/**
 * StorageUsageManager — lấy thông tin used/total/free size của mỗi storage volume.
 * Mirror từ MyFiles StorageUsageManager.
 */
object StorageUsageManager {

    private val log = Logger("StorageUsageManager")

    /**
     * Lấy used size của 1 domain
     */
    fun getStorageUsedSize(context: Context, domainType: Int): Long {
        val path = DomainType.getRootPath(domainType) ?: return 0L
        return try {
            val stat = StatFs(path)
            stat.totalBytes - stat.availableBytes
        } catch (e: Exception) {
            log.e("getStorageUsedSize: failed for domain=$domainType, path=$path", e)
            0L
        }
    }

    /**
     * Lấy total size của 1 domain
     */
    fun getStorageTotalSize(domainType: Int): Long {
        val path = DomainType.getRootPath(domainType) ?: return 0L
        return try {
            val stat = StatFs(path)
            stat.totalBytes
        } catch (e: Exception) {
            log.e("getStorageTotalSize: failed for domain=$domainType, path=$path", e)
            0L
        }
    }

    /**
     * Lấy free space của 1 domain
     */
    fun getStorageFreeSpace(domainType: Int): Long {
        val path = DomainType.getRootPath(domainType) ?: return 0L
        return try {
            val stat = StatFs(path)
            stat.availableBytes
        } catch (e: Exception) {
            log.e("getStorageFreeSpace: failed for domain=$domainType, path=$path", e)
            0L
        }
    }

    /**
     * Lấy StorageUsageInfo cho 1 domain
     */
    fun getStorageUsageInfo(context: Context, domainType: Int): StorageUsageInfo {
        val path = DomainType.getRootPath(domainType) ?: return StorageUsageInfo()
        return try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            StorageUsageInfo(
                usedByteSize = total - free,
                totalByteSize = total,
                displayText = null  // Caller sẽ format
            )
        } catch (e: Exception) {
            log.e("getStorageUsageInfo: failed for domain=$domainType, path=$path", e)
            StorageUsageInfo()
        }
    }

    /**
     * Lấy StorageVolume info cho 1 domain
     */
    fun getStorageVolumeInfo(context: Context, domainType: Int): StorageVolume? {
        StorageVolumeManager.refresh(context)
        return StorageVolumeManager.getVolume(domainType)
    }
}

/**
 * Thông tin usage của 1 storage
 */
data class StorageUsageInfo(
    val usedByteSize: Long = 0L,
    val totalByteSize: Long = 0L,
    val displayText: String? = null  // Null = chưa format, caller format theo Locale
) {
    val freeByteSize: Long get() = (totalByteSize - usedByteSize).coerceAtLeast(0L)
    val usedPercent: Int get() = if (totalByteSize > 0) ((usedByteSize.toDouble() / totalByteSize) * 100).toInt().coerceIn(0, 100) else 0
}
