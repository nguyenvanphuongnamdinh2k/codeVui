package com.example.codevui.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce

/**
 * MediaStoreObserver — observe mọi thay đổi từ MediaStore (xóa/thêm/sửa file bởi bất kỳ app nào).
 *
 * Trả về Flow<Unit> emit mỗi khi có thay đổi.
 * Dùng debounce để tránh reload quá nhiều lần liên tiếp.
 *
 * Usage trong Repository:
 *   fun observeChanges(): Flow<Unit> = MediaStoreObserver.observe(context)
 */
object MediaStoreObserver {

    private const val DEBOUNCE_MS = 500L

    /**
     * Observe tất cả thay đổi của MediaStore external storage.
     * Flow tự cleanup ContentObserver khi bị cancel.
     */
    fun observe(context: Context): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit) // emit signal — không block nếu channel full
            }
        }

        // Register observe toàn bộ MediaStore external
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri("external"),
            true, // notifyForDescendants = true → catch mọi sub-URI
            observer
        )

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
        .conflate()              // bỏ events thừa nếu chưa kịp xử lý
        .debounce(DEBOUNCE_MS)   // chờ 500ms im lặng rồi mới emit → tránh reload liên tục
}