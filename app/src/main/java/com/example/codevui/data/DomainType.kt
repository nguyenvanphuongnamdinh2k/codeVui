package com.example.codevui.data

/**
 * DomainType constants cho các loại storage.
 * Mirror từ MyFiles DomainType.
 *
 * Các giá trị:
 * - 0: UNKNOWN
 * - 1: INTERNAL_STORAGE
 * - 2: EXTERNAL_SD (SD Card)
 * - 3-8: EXTERNAL_USB_DRIVE_A-F
 * - 9: INTERNAL_APP_CLONE
 */
object DomainType {
    // ═══════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════

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

    // Internal storage range
    const val INTERNAL_STORAGE_START = INTERNAL_STORAGE
    const val INTERNAL_STORAGE_END = INTERNAL_APP_CLONE

    // ═══════════════════════════════════════════════════════
    // is* helpers — đồng nhất với MyFiles
    // ═══════════════════════════════════════════════════════

    @JvmStatic
    fun isLocalStorage(domainType: Int): Boolean =
        domainType in INTERNAL_STORAGE_START..INTERNAL_STORAGE_END

    @JvmStatic
    fun isInternalStorage(domainType: Int): Boolean =
        domainType == INTERNAL_STORAGE || domainType == INTERNAL_APP_CLONE

    @JvmStatic
    fun isSd(domainType: Int): Boolean = domainType == EXTERNAL_SD

    @JvmStatic
    fun isUsb(domainType: Int): Boolean =
        domainType in EXTERNAL_USB_DRIVE_START..EXTERNAL_USB_DRIVE_END

    @JvmStatic
    fun isRemovable(domainType: Int): Boolean =
        domainType == EXTERNAL_SD || isUsb(domainType)

    @JvmStatic
    fun isUnknown(domainType: Int): Boolean = domainType == UNKNOWN

    // ═══════════════════════════════════════════════════════
    // StorageVolumeManager aliases — delegate để backward compat
    // ═══════════════════════════════════════════════════════

    @JvmStatic
    fun mounted(domainType: Int): Boolean = StorageVolumeManager.mounted(domainType)

    @JvmStatic
    fun connected(domainType: Int): Boolean = StorageVolumeManager.connected(domainType)

    @JvmStatic
    fun getVolume(domainType: Int): StorageVolume? = StorageVolumeManager.getVolume(domainType)

    @JvmStatic
    fun getRootPath(domainType: Int): String? = StorageVolumeManager.getRootPath(domainType)
}
