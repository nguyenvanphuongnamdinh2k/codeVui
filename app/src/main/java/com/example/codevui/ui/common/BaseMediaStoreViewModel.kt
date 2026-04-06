package com.example.codevui.ui.common

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.example.codevui.data.MediaStoreObserver
import kotlinx.coroutines.launch

/**
 * BaseMediaStoreViewModel — extend BaseFileOperationViewModel,
 * thêm khả năng tự động reload khi MediaStore thay đổi từ bên ngoài.
 *
 * Subclass chỉ cần override [reload] như bình thường — sẽ được gọi tự động.
 *
 * Hierarchy:
 *   AndroidViewModel
 *     └── BaseFileOperationViewModel  (copy/move/zip + service binding)
 *           └── BaseMediaStoreViewModel  (auto-reload khi external change)
 *                 └── BrowseViewModel, RecentFilesViewModel, FileListViewModel, ...
 */
abstract class BaseMediaStoreViewModel(
    application: Application
) : BaseFileOperationViewModel(application) {

    init {
        observeMediaStore()
    }

    private fun observeMediaStore() {
        viewModelScope.launch {
            MediaStoreObserver.observe(getApplication())
                .collect {
                    // Chỉ reload nếu không đang chạy operation của chính mình
                    // (tránh reload 2 lần khi chính app copy/move xong)
                    if (!isOperationRunning()) {
                        reload()
                    }
                }
        }
    }

    /**
     * Subclass implement logic reload data của screen đó.
     * Được gọi tự động khi:
     *   1. Operation của app hoàn thành (từ BaseFileOperationViewModel)
     *   2. MediaStore thay đổi từ app bên ngoài
     */
    abstract fun reload()
}
