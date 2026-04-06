package com.example.codevui.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FavoriteEntity — lưu trữ file/folder được đánh dấu yêu thích trong Room DB.
 *
 * Bảng favorites:
 *   - file_id    (primary key, unique) — đường dẫn đã được hash hoặc URI
 *   - name       — tên file/folder hiển thị
 *   - path       — đường dẫn tuyệt đối đầy đủ
 *   - size       — kích thước file (0 cho folder)
 *   - mimeType   — MIME type (null cho folder)
 *   - isDirectory — true cho folder, false cho file
 *   - dateModified — thời gian sửa đổi cuối (epoch seconds)
 *   - addedAt    — thời gian được thêm vào yêu thích (epoch seconds)
 *   - sortOrder  — thứ tự sắp xếp (drag-drop reorder)
 *
 * Hành vi:
 *   - Path unique: không thể thêm cùng 1 file 2 lần
 *   - Khi file bị xóa hoặc di chuyển → tự động bị loại khỏi favorites (lazy validation)
 *   - Reorder: cập nhật sortOrder khi user kéo thả
 */
@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["sortOrder"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey
    val fileId: String,        // SHA-256 hash của path (để ngắn gọn hơn path thuần)
    val name: String,
    val path: String,           // đường dẫn tuyệt đối đầy đủ
    val size: Long,             // 0 cho folder
    val mimeType: String?,      // null cho folder
    val isDirectory: Boolean,
    val dateModified: Long,     // epoch seconds từ MediaStore
    val addedAt: Long,          // epoch seconds — thời điểm thêm vào yêu thích
    val sortOrder: Int          // thứ tự hiển thị (0 = đầu tiên)
)
