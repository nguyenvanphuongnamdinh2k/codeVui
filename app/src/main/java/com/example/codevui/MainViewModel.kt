package com.example.codevui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.codevui.model.FileType

class MainViewModel : ViewModel() {
    val screenStack = mutableStateListOf<Screen>(Screen.Home)

    // Storage info cho DrawerPane
    var storageUsed by mutableStateOf("")
    var storageTotal by mutableStateOf("")

    // Drawer collapse state (survive rotation)
    var isDrawerExpanded by mutableStateOf(true)

    val currentScreen: Screen get() = screenStack.last()

    fun navigateTo(screen: Screen) {
        screenStack.add(screen)
    }

    fun navigateReplace(screen: Screen) {
        // Replace current screen (dùng cho drawer navigation — không stack)
        if (screenStack.size > 1) {
            screenStack.removeLast()
        }
        screenStack.add(screen)
    }

    fun goBack(): Boolean {
        if (screenStack.size <= 1) return false
        screenStack.removeLast()
        return true
    }
}

sealed class Screen {
    data object Home : Screen()
    data class FileList(val fileType: FileType, val title: String) : Screen()
    data class Browse(
        val sessionId: Long = System.currentTimeMillis(),
        val initialPath: String? = null  // Path để navigate đến khi mở
    ) : Screen()
    data class Archive(val filePath: String, val fileName: String, val fullPath: String = filePath, val isPreviewMode: Boolean = false) : Screen()
    data class RecentFiles(val initialSelectedPath: String? = null) : Screen()
    data object Favorites : Screen()
    data object Search : Screen()
    data object Trash : Screen()
    data object StorageManager : Screen()
    data object Duplicates : Screen()
    data object LargeFiles : Screen()
    data object UnusedApps : Screen()
    data class TextEditor(val filePath: String, val fileName: String) : Screen()
    data class Recommend(val cardType: com.example.codevui.model.RecommendType? = null) : Screen()

}
