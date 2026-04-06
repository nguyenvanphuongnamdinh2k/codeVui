package com.example.codevui.data

import android.content.Context
import com.example.codevui.model.RecentFile
import com.example.codevui.util.Logger

/**
 * FavoriteAction — utility để thêm/xóa yêu thích cho RecentFile items.
 * Dùng trong SelectionActionHandler và các nơi cần toggle favorite.
 */
object FavoriteAction {

    private val log = Logger("FavoriteAction")

    /**
     * Thêm 1 RecentFile vào favorites.
     */
    suspend fun addToFavorites(context: Context, file: RecentFile): Boolean {
        return FavoriteManager.addFavorite(
            context = context,
            path = file.path,
            name = file.name,
            size = file.size,
            mimeType = file.type.mimePrefix.takeIf { it.isNotEmpty() },
            isDirectory = false,
            dateModified = file.dateModified
        )
    }

    /**
     * Thêm nhiều RecentFile vào favorites.
     */
    suspend fun addToFavorites(context: Context, files: List<RecentFile>): Int {
        var success = 0
        for (file in files) {
            if (addToFavorites(context, file)) success++
        }
        log.d("addToFavorites: ${files.size} files, success=$success")
        return success
    }

    /**
     * Xóa 1 file khỏi favorites.
     */
    suspend fun removeFromFavorites(context: Context, path: String): Boolean {
        return FavoriteManager.removeFavorite(context, path)
    }

    /**
     * Xóa nhiều file khỏi favorites.
     */
    suspend fun removeFromFavorites(context: Context, paths: List<String>): Int {
        return FavoriteManager.removeFavorites(context, paths)
    }

    /**
     * Toggle yêu thích — thêm nếu chưa có, xóa nếu đã có.
     * @return true = thêm thành công, false = xóa thành công
     */
    suspend fun toggleFavorite(context: Context, file: RecentFile): Boolean {
        return FavoriteManager.toggleFavorite(
            context = context,
            path = file.path,
            name = file.name,
            size = file.size,
            mimeType = file.type.mimePrefix.takeIf { it.isNotEmpty() },
            isDirectory = false,
            dateModified = file.dateModified
        )
    }

    /**
     * Kiểm tra 1 path có trong favorites chưa.
     */
    suspend fun isFavorite(context: Context, path: String): Boolean {
        return FavoriteManager.isFavorite(context, path)
    }
}
