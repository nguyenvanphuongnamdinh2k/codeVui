package com.example.codevui.ui.texteditor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.util.Logger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private val log = Logger("TextEditorScreen")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    filePath: String,
    fileName: String,
    viewModel: TextEditorViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showDiscardDialog by remember { mutableStateOf(false) }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    // Animatable chỉ dùng cho fling — pan/pinch cập nhật viewModel trực tiếp
    val animOffsetX = remember { Animatable(viewModel.offsetX) }
    val animOffsetY = remember { Animatable(viewModel.offsetY) }
    val flingDecay = remember { exponentialDecay<Float>(frictionMultiplier = 1.5f) }

    LaunchedEffect(filePath) { viewModel.load(filePath) }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Đã lưu")
            viewModel.clearSaveSuccess()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    BackHandler(enabled = viewModel.isDirty) { showDiscardDialog = true }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Bỏ thay đổi?") },
            text = { Text("File chưa được lưu. Bạn có muốn thoát không?") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Thoát", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Ở lại") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = fileName, fontSize = 16.sp, maxLines = 1)
                        if (viewModel.isDirty) {
                            Text(text = "Chưa lưu", fontSize = 11.sp, color = Color(0xFFFF9800))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.isDirty) showDiscardDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.zoomOut() }, enabled = viewModel.canZoomOut) {
                        Icon(
                            Icons.Default.TextDecrease, contentDescription = "Thu nhỏ",
                            tint = if (viewModel.canZoomOut) LocalContentColor.current else Color(0xFFBBBBBB)
                        )
                    }
                    Text(
                        text = "${viewModel.fontSize.toInt()}",
                        fontSize = 13.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    IconButton(onClick = { viewModel.zoomIn() }, enabled = viewModel.canZoomIn) {
                        Icon(
                            Icons.Default.TextIncrease, contentDescription = "Phóng to",
                            tint = if (viewModel.canZoomIn) LocalContentColor.current else Color(0xFFBBBBBB)
                        )
                    }
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).align(Alignment.CenterVertically),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { keyboardController?.hide(); viewModel.save() }, enabled = viewModel.isDirty) {
                            Icon(
                                Icons.Default.Save, contentDescription = "Lưu",
                                tint = if (viewModel.isDirty) MaterialTheme.colorScheme.primary
                                       else Color(0xFFBBBBBB)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.White
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .clipToBounds()
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(Unit) {
                            coroutineScope {
                                val scope = this
                                var lastTapTime = 0L
                                var lastTapPosition = Offset.Zero
                                awaitEachGesture {
                                    // Chờ ngón đầu tiên, không quan tâm consumed
                                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                                    log.d("gesture: down finger")

                                    // Dừng fling đang chạy
                                    scope.launch { animOffsetX.stop() }
                                    scope.launch { animOffsetY.stop() }

                                    val velocityTracker = VelocityTracker()
                                    var zoom = 1f
                                    var pan = Offset.Zero
                                    var pastTouchSlop = false
                                    val touchSlop = viewConfiguration.touchSlop

                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pointerCount = event.changes.count { it.pressed }

                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = true)

                                        zoom *= zoomChange
                                        pan += panChange

                                        if (!pastTouchSlop) {
                                            val zoomDist = abs(zoom - 1f) * viewConfiguration.touchSlop * 3f
                                            val panDist = pan.getDistance()
                                            log.d("gesture: slop check pointers=$pointerCount zoom=$zoom zoomDist=$zoomDist panDist=$panDist slop=$touchSlop")
                                            if (zoomDist > touchSlop || panDist > touchSlop) {
                                                pastTouchSlop = true
                                                zoom = 1f
                                                pan = Offset.Zero
                                                log.d("gesture: pastTouchSlop=true")
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            val vp = Offset(viewportSize.width.toFloat(), viewportSize.height.toFloat())
                                            val cs = Offset(contentSize.width.toFloat(), contentSize.height.toFloat())

                                            if (pointerCount >= 2) {
                                                // Pinch zoom + pan — cập nhật viewModel trực tiếp, không qua Animatable
                                                log.d("gesture: pinch pointers=$pointerCount zoomChange=$zoomChange pan=$panChange")
                                                if (zoomChange != 1f) {
                                                    viewModel.onPinchZoom(zoomChange, centroid, cs, vp)
                                                }
                                                if (panChange != Offset.Zero) {
                                                    viewModel.onPan(panChange.x, panChange.y, cs, vp)
                                                }
                                                event.changes.forEach { it.consume() }
                                            } else {
                                                // Pan 1 ngón — cập nhật viewModel trực tiếp
                                                if (panChange != Offset.Zero) {
                                                    log.d("gesture: pan1 delta=$panChange offset=(${viewModel.offsetX},${viewModel.offsetY})")
                                                    viewModel.onPan(panChange.x, panChange.y, cs, vp)
                                                    val change = event.changes.firstOrNull()
                                                    if (change != null && change.positionChanged()) {
                                                        change.consume()
                                                    }
                                                }
                                                velocityTracker.addPosition(
                                                    event.changes.first().uptimeMillis,
                                                    event.changes.first().position
                                                )
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    // Double-tap detection: chỉ khi không di chuyển (tap thuần)
                                    if (!pastTouchSlop) {
                                        val now = System.currentTimeMillis()
                                        val tapPos = firstDown.position
                                        val isDoubleTap = (now - lastTapTime) < 300L &&
                                            (tapPos - lastTapPosition).getDistance() < viewConfiguration.touchSlop * 3f
                                        if (isDoubleTap) {
                                            log.d("gesture: double-tap at $tapPos fontSize=${viewModel.fontSize}")
                                            val vp = Offset(viewportSize.width.toFloat(), viewportSize.height.toFloat())
                                            val cs = Offset(contentSize.width.toFloat(), contentSize.height.toFloat())
                                            if (viewModel.fontSize < FONT_SIZE_MAX) {
                                                viewModel.doubleTapZoomIn(tapPos, cs, vp)
                                            } else {
                                                viewModel.doubleTapZoomOut(tapPos, cs, vp)
                                            }
                                            lastTapTime = 0L
                                        } else {
                                            lastTapTime = now
                                            lastTapPosition = tapPos
                                        }
                                        return@awaitEachGesture
                                    }

                                    // Fling sau khi nhấc ngón
                                    val velocity = velocityTracker.calculateVelocity()
                                    log.d("gesture: up velocity=$velocity")
                                    val vp = Offset(viewportSize.width.toFloat(), viewportSize.height.toFloat())
                                    val cs = Offset(contentSize.width.toFloat(), contentSize.height.toFloat())
                                    val maxX = (cs.x - vp.x).coerceAtLeast(0f)
                                    val maxY = (cs.y - vp.y).coerceAtLeast(0f)

                                    scope.launch {
                                        // Sync animatable với vị trí hiện tại trước khi fling
                                        animOffsetX.snapTo(viewModel.offsetX)
                                        animOffsetX.updateBounds(lowerBound = -maxX, upperBound = 0f)
                                        animOffsetX.animateDecay(velocity.x, flingDecay) {
                                            viewModel.setOffsetDirect(x = value)
                                        }
                                    }
                                    scope.launch {
                                        animOffsetY.snapTo(viewModel.offsetY)
                                        animOffsetY.updateBounds(lowerBound = -maxY, upperBound = 0f)
                                        animOffsetY.animateDecay(velocity.y, flingDecay) {
                                            viewModel.setOffsetDirect(y = value)
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Layout(
                        content = {
                            BasicTextField(
                                value = viewModel.content,
                                onValueChange = { viewModel.onContentChange(it) },
                                modifier = Modifier
                                    .onSizeChanged { contentSize = it }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                textStyle = TextStyle(
                                    fontSize = viewModel.fontSize.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF1A1A1A),
                                    lineHeight = (viewModel.fontSize * 1.6f).sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        }
                    ) { measurables, constraints ->
                        val placeable = measurables.first().measure(
                            constraints.copy(
                                minWidth = 0, minHeight = 0,
                                maxWidth = Int.MAX_VALUE, maxHeight = Int.MAX_VALUE
                            )
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(
                                x = viewModel.offsetX.toInt(),
                                y = viewModel.offsetY.toInt()
                            )
                        }
                    }
                }
            }
        }
    }
}
