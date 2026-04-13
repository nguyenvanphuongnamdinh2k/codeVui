package com.example.codevui.data

import android.content.Context
import android.content.pm.PackageManager
import com.example.codevui.util.Logger

private val log = Logger("DefaultAppManager")

/**
 * Quản lý ứng dụng mặc định để mở file theo extension.
 * Lưu preference vào SharedPreferences — persist qua các session.
 */
object DefaultAppManager {

    private const val PREFS_NAME = "codevui_default_apps"

    /**
     * Lấy package name của app mặc định cho extension này.
     * Trả về null nếu chưa có hoặc app đã bị gỡ.
     */
    fun getDefaultApp(context: Context, extension: String): DefaultAppInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pkg = prefs.getString("pkg_$extension", null) ?: return null
        val activity = prefs.getString("act_$extension", null) ?: return null

        // Kiểm tra app vẫn còn được cài đặt
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            DefaultAppInfo(packageName = pkg, activityName = activity)
        } catch (e: PackageManager.NameNotFoundException) {
            // App đã bị gỡ → xóa preference
            log.d("Default app $pkg cho .$extension đã bị gỡ, xóa preference")
            clearDefaultApp(context, extension)
            null
        }
    }

    /**
     * Lưu app mặc định cho extension.
     */
    fun setDefaultApp(context: Context, extension: String, packageName: String, activityName: String) {
        log.d("Đặt default app cho .$extension: $packageName/$activityName")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("pkg_$extension", packageName)
            .putString("act_$extension", activityName)
            .apply()
    }

    /**
     * Xóa app mặc định cho extension.
     */
    fun clearDefaultApp(context: Context, extension: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("pkg_$extension")
            .remove("act_$extension")
            .apply()
    }
}

/**
 * Thông tin app mặc định đã lưu.
 */
data class DefaultAppInfo(
    val packageName: String,
    val activityName: String
)
