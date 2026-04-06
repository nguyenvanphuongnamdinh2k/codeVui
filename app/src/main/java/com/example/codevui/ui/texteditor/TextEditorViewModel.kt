package com.example.codevui.ui.texteditor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.codevui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val log = Logger("TextEditorVM")

const val FONT_SIZE_MIN = 10f
const val FONT_SIZE_MAX = 32f
const val FONT_SIZE_DEFAULT = 14f
const val FONT_SIZE_STEP = 2f
private const val KEY_FONT_SIZE = "font_size"
private const val KEY_OFFSET_X = "offset_x"
private const val KEY_OFFSET_Y = "offset_y"

data class TextEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)

class TextEditorViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TextEditorUiState())
    val uiState: StateFlow<TextEditorUiState> = _uiState.asStateFlow()

    var content by mutableStateOf("")
        private set

    // fontSize survive rotation và theme change qua SavedStateHandle
    var fontSize by mutableFloatStateOf(
        savedStateHandle.get<Float>(KEY_FONT_SIZE) ?: FONT_SIZE_DEFAULT
    )
        private set

    val canZoomIn: Boolean get() = fontSize < FONT_SIZE_MAX
    val canZoomOut: Boolean get() = fontSize > FONT_SIZE_MIN

    // Pan offset — persist qua rotation
    var offsetX by mutableFloatStateOf(savedStateHandle.get<Float>(KEY_OFFSET_X) ?: 0f)
        private set
    var offsetY by mutableFloatStateOf(savedStateHandle.get<Float>(KEY_OFFSET_Y) ?: 0f)
        private set

    private var filePath: String = ""
    private var originalContent: String = ""

    val isDirty: Boolean get() = content != originalContent

    fun load(path: String) {
        if (filePath == path) return
        filePath = path
        viewModelScope.launch {
            _uiState.value = TextEditorUiState(isLoading = true)
            val text = withContext(Dispatchers.IO) {
                try {
                    File(path).readText()
                } catch (e: Exception) {
                    log.e("Failed to read file: $path", e)
                    null
                }
            }
            if (text != null) {
                content = text
                originalContent = text
                _uiState.value = TextEditorUiState(isLoading = false)
            } else {
                _uiState.value = TextEditorUiState(isLoading = false, errorMessage = "Không thể đọc file")
            }
        }
    }

    fun onContentChange(newText: String) {
        content = newText
    }

    fun save() {
        if (!isDirty) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveSuccess = false)
            val ok = withContext(Dispatchers.IO) {
                try {
                    File(filePath).writeText(content)
                    true
                } catch (e: Exception) {
                    log.e("Failed to save file: $filePath", e)
                    false
                }
            }
            if (ok) {
                originalContent = content
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
                log.d("Saved: $filePath")
            } else {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = "Không thể lưu file")
            }
        }
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun zoomIn() {
        if (canZoomIn) {
            fontSize = (fontSize + FONT_SIZE_STEP).coerceAtMost(FONT_SIZE_MAX)
            savedStateHandle[KEY_FONT_SIZE] = fontSize
        }
    }

    fun zoomOut() {
        if (canZoomOut) {
            fontSize = (fontSize - FONT_SIZE_STEP).coerceAtLeast(FONT_SIZE_MIN)
            savedStateHandle[KEY_FONT_SIZE] = fontSize
        }
    }

    /**
     * Pinch zoom tại centroid: scale fontSize, đồng thời điều chỉnh offset
     * sao cho điểm dưới centroid không dịch chuyển.
     *
     * Công thức: offset' = centroid - (centroid - offset) * (newFontSize / oldFontSize)
     */
    fun onPinchZoom(scaleFactor: Float, centroid: Offset, contentSize: Offset, viewportSize: Offset) {
        val oldFontSize = fontSize
        val newFontSize = (oldFontSize * scaleFactor).coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)
        val ratio = newFontSize / oldFontSize

        val newOffsetX = centroid.x - (centroid.x - offsetX) * ratio
        val newOffsetY = centroid.y - (centroid.y - offsetY) * ratio

        fontSize = newFontSize
        savedStateHandle[KEY_FONT_SIZE] = fontSize

        applyOffset(newOffsetX, newOffsetY, contentSize, viewportSize)
    }

    fun onPan(deltaX: Float, deltaY: Float, contentSize: Offset, viewportSize: Offset) {
        applyOffset(offsetX + deltaX, offsetY + deltaY, contentSize, viewportSize)
    }

    /** Clamp offset: không kéo content ra ngoài viewport */
    private fun applyOffset(x: Float, y: Float, contentSize: Offset, viewportSize: Offset) {
        val maxX = (contentSize.x - viewportSize.x).coerceAtLeast(0f)
        val maxY = (contentSize.y - viewportSize.y).coerceAtLeast(0f)
        offsetX = x.coerceIn(-maxX, 0f)
        offsetY = y.coerceIn(-maxY, 0f)
        savedStateHandle[KEY_OFFSET_X] = offsetX
        savedStateHandle[KEY_OFFSET_Y] = offsetY
    }

    fun doubleTapZoomIn(tapPoint: Offset, contentSize: Offset, viewportSize: Offset) {
        val oldFontSize = fontSize
        val newFontSize = FONT_SIZE_MAX
        val ratio = newFontSize / oldFontSize
        fontSize = newFontSize
        savedStateHandle[KEY_FONT_SIZE] = fontSize
        val newOffsetX = tapPoint.x - (tapPoint.x - offsetX) * ratio
        val newOffsetY = tapPoint.y - (tapPoint.y - offsetY) * ratio
        applyOffset(newOffsetX, newOffsetY, contentSize, viewportSize)
    }

    fun doubleTapZoomOut(tapPoint: Offset, contentSize: Offset, viewportSize: Offset) {
        val oldFontSize = fontSize
        val newFontSize = FONT_SIZE_DEFAULT
        val ratio = newFontSize / oldFontSize
        fontSize = newFontSize
        savedStateHandle[KEY_FONT_SIZE] = fontSize
        val newOffsetX = tapPoint.x - (tapPoint.x - offsetX) * ratio
        val newOffsetY = tapPoint.y - (tapPoint.y - offsetY) * ratio
        applyOffset(newOffsetX, newOffsetY, contentSize, viewportSize)
    }

    fun setOffsetDirect(x: Float? = null, y: Float? = null) {
        if (x != null) offsetX = x
        if (y != null) offsetY = y
    }

    fun resetOffset() {
        offsetX = 0f
        offsetY = 0f
        savedStateHandle[KEY_OFFSET_X] = 0f
        savedStateHandle[KEY_OFFSET_Y] = 0f
    }
}
