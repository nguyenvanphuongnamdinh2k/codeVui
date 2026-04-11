package com.example.codevui.data

/**
 * StorageTypeForTrash — xác định trash type dựa trên storage domain.
 * Mirror từ MyFiles StorageTypeForTrash.
 *
 * Trash được chia theo domain:
 * - INTERNAL (1): Thùng rác của Internal Storage
 * - EXTERNAL_SD (2): Thùng rác của SD Card
 * - INTERNAL_AND_SD (3): Cả hai
 * - NONE (0): Không có trash
 */
object StorageTypeForTrash {

    const val NONE = 0
    const val INTERNAL = 1
    const val EXTERNAL_SD = 2
    const val INTERNAL_AND_SD = INTERNAL or EXTERNAL_SD

    /**
     * Lấy trash type dựa trên domainType
     */
    @JvmStatic
    fun getStorageTypeForTrash(domainType: Int): Int {
        return when {
            DomainType.isInternalStorage(domainType) -> INTERNAL
            DomainType.isSd(domainType) -> EXTERNAL_SD
            DomainType.isUsb(domainType) -> NONE  // USB không có trash riêng
            else -> NONE
        }
    }

    /**
     * Kiểm tra có phải internal trash không
     */
    @JvmStatic
    fun isInternalTrash(storageTypeForTrash: Int): Boolean =
        storageTypeForTrash == INTERNAL

    /**
     * Kiểm tra có phải SD trash không
     */
    @JvmStatic
    fun isSDTrash(storageTypeForTrash: Int): Boolean =
        storageTypeForTrash == EXTERNAL_SD

    /**
     * Kiểm tra có phải cả internal + SD trash không
     */
    @JvmStatic
    fun isInternalAndSDTrash(storageTypeForTrash: Int): Boolean =
        storageTypeForTrash == INTERNAL_AND_SD

    /**
     * Kiểm tra selected storage và no-space storage
     * Dùng để quyết định xóa vào đâu khi hết chỗ
     */
    @JvmStatic
    fun isFullOnlySdOrInternal(
        selectedStorageType: Int,
        noSpaceStorageType: Int
    ): Boolean {
        return (isInternalAndSDTrash(selectedStorageType)
                && (isInternalTrash(noSpaceStorageType) || isSDTrash(noSpaceStorageType)))
    }

    /**
     * Lấy tên hiển thị của trash type
     */
    fun getTrashDisplayName(storageTypeForTrash: Int): String {
        return when (storageTypeForTrash) {
            INTERNAL -> "Thùng rác (Bộ nhớ trong)"
            EXTERNAL_SD -> "Thùng rác (Thẻ SD)"
            INTERNAL_AND_SD -> "Thùng rác (Bộ nhớ trong + Thẻ SD)"
            NONE -> "Không có thùng rác"
            else -> "Thùng rác"
        }
    }
}
