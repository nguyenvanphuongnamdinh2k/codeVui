package com.example.codevui.ui.storage

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codevui.data.DomainType
import com.example.codevui.util.formatStorageSize
import com.example.codevui.model.RecommendCard
import com.example.codevui.model.RecommendType
import com.example.codevui.ui.recommend.RecommendScreen
import com.example.codevui.ui.recommend.RecommendViewModel
import com.example.codevui.util.formatFileSize
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter

// ─────────────────────────────────────────────────────────
// Color palette for storage bar segments (Samsung My Files style)
// ─────────────────────────────────────────────────────────
private val COLOR_VIDEO       = Color(0xFFE91E63)  // Pink       → Video
private val COLOR_IMAGE      = Color(0xFFFF8A80)  // Coral      → Ảnh
private val COLOR_ARCHIVE    = Color(0xFF4CAF50)  // Green      → File đã nén
private val COLOR_APK        = Color(0xFFAED581)  // Lime       → Các file cài đặt
private val COLOR_DOC        = Color(0xFF2196F3)  // Blue       → Tài liệu
private val COLOR_AUDIO      = Color(0xFFCE93D8)  // Lavender   → File âm thanh
private val COLOR_APPS       = Color(0xFF00BCD4)  // Cyan       → Ứng dụng
private val COLOR_SYSTEM     = Color(0xFF7B1FA2)  // Dark purple → Hệ thống
private val COLOR_OTHER      = Color(0xFF546E7A)  // Blue-grey  → File khác
private val COLOR_FREE       = Color(0xFFEEEEEE)  // Light gray → Free space

// ─────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    viewModel: StorageManagerViewModel = viewModel(),
    onBack: () -> Unit = {},
    onNavigateToCategory: (String) -> Unit = {},
    onNavigateToLargeFiles: () -> Unit = {},
    onNavigateToDuplicates: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToOldScreenshots: () -> Unit = {},
    onNavigateToUnusedApps: () -> Unit = {},
    onNavigateToRecommend: (RecommendType) -> Unit = {},
    onVolumeSelect: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var carouselPage by remember { mutableIntStateOf(0) }

    Log.d("StorageScreen", "StorageManagerScreen recomposition: totalFormatted=${uiState.totalFormatted}, storageInfo.totalBytes=${uiState.storageInfo?.totalBytes}, volumes=${uiState.volumes.map { "${it.domainType}:${it.totalBytes}" }}")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý lưu trữ", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Volume Selector (Internal / SD / USB) ──────────────────
            VolumeSelector(
                volumes = uiState.volumes,
                selectedDomainType = uiState.selectedVolumeDomainType,
                onVolumeSelect = onVolumeSelect
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }

            uiState.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(20.dp)
                )
            }

            // ── Per-volume storage card ────────────────────────────────
            if (uiState.selectedVolumeDomainType == DomainType.INTERNAL_STORAGE) {
                // Internal: dùng full analysis đã load sẵn
                InternalStorageCard(
                    uiState = uiState,
                    onCategoryClick = onNavigateToCategory
                )
            } else {
                // SD/USB: dùng breakdown từ fileBreakdowns map
                ExternalStorageCard(
                    volumeState = uiState.volumes.find { it.domainType == uiState.selectedVolumeDomainType },
                    breakdown = uiState.fileBreakdowns[uiState.selectedVolumeDomainType],
                    onCategoryClick = onNavigateToCategory
                )
            }

            // ── Quick Actions Carousel ─────────────────────────────
            QuickActionsCarousel(
                currentPage = carouselPage,
                onPageChange = { carouselPage = it },
                duplicateBytes = uiState.duplicateBytes,
                largeFilesBytes = uiState.largeFilesBytes,
                onDuplicateClick = onNavigateToDuplicates,
                onLargeFilesClick = onNavigateToLargeFiles
            )

            // ── Suggestions ─────────────────────────────────────────
            SuggestionsSection(
                uiState = uiState,
                onUnusedAppsClick = onNavigateToUnusedApps,
                onOldScreenshotsClick = onNavigateToOldScreenshots,
                onNavigateToDuplicates = onNavigateToDuplicates,
                onNavigateToLargeFiles = onNavigateToLargeFiles
            )

            // ── Recommend Cards (MyFiles style) ─────────────────────
            RecommendCardsSection(
                uiState = uiState,
                onCardClick = onNavigateToRecommend
            )

            // ── Storage Categories ──────────────────────────────────
            StorageCategoriesSection(
                uiState = uiState,
                onCategoryClick = onNavigateToCategory,
                onTrashClick = onNavigateToTrash,
                onUnusedAppsClick = onNavigateToUnusedApps
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────
// Volume Selector (Internal / SD / USB tabs)
// Mirror MyFiles: switch giữa các ổ lưu trữ
// ─────────────────────────────────────────────────────────
@Composable
private fun VolumeSelector(
    volumes: List<VolumeStorageState>,
    selectedDomainType: Int,
    onVolumeSelect: (Int) -> Unit
) {
    if (volumes.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        volumes.forEach { volume ->
            val isSelected = volume.domainType == selectedDomainType

            val icon: ImageVector = when {
                DomainType.isInternalStorage(volume.domainType) -> Icons.Filled.SdStorage
                DomainType.isSd(volume.domainType) -> Icons.Filled.SdStorage
                DomainType.isUsb(volume.domainType) -> Icons.Filled.Usb
                else -> Icons.Filled.SdStorage
            }

            val label = volume.displayName

            FilterChip(
                selected = isSelected,
                onClick = { onVolumeSelect(volume.domainType) },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) Color(0xFF2196F3) else Color(0xFF888888)
                        )
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFE3F2FD),
                    selectedLabelColor = Color(0xFF1976D2)
                ),
                border = if (isSelected) {
                    FilterChipDefaults.filterChipBorder(
                        borderColor = Color(0xFF2196F3),
                        selectedBorderColor = Color(0xFF2196F3),
                        enabled = true,
                        selected = isSelected
                    )
                } else {
                    FilterChipDefaults.filterChipBorder(
                        borderColor = Color(0xFFE0E0E0),
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = false
                    )
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// External Storage Card (SD / USB)
// Simplified view cho external volumes
// ─────────────────────────────────────────────────────────
@Composable
private fun ExternalStorageCard(
    volumeState: VolumeStorageState?,
    breakdown: com.example.codevui.data.VolumeFileBreakdown?,
    onCategoryClick: (String) -> Unit
) {
    if (volumeState == null) {
        // Volume not loaded yet
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
        return
    }

    val usedPercent = volumeState.usedPercent
    val usedFormatted = formatStorageSize(volumeState.usedBytes)
    val totalFormatted = formatStorageSize(volumeState.totalBytes)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = volumeState.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                if (volumeState.isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$usedPercent% đã dùng",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF555555)
                )
                Text(
                    text = "$usedFormatted / $totalFormatted",
                    fontSize = 13.sp,
                    color = Color(0xFF888888)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Simple progress bar
            val progress by animateFloatAsState(
                targetValue = usedPercent / 100f,
                label = "storageProgress"
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF2196F3),
                trackColor = Color(0xFFE0E0E0),
            )

            Spacer(Modifier.height(16.dp))

            // File breakdown list (if available)
            if (breakdown != null) {
                FileCategoryRow(Color(0xFFE91E63), "Video", breakdown.videoBytes, onCategoryClick)
                FileCategoryRow(Color(0xFFFF8A80), "Ảnh", breakdown.imageBytes, onCategoryClick)
                FileCategoryRow(Color(0xFFCE93D8), "Âm thanh", breakdown.audioBytes, onCategoryClick)
                FileCategoryRow(Color(0xFF4CAF50), "File đã nén", breakdown.archiveBytes, onCategoryClick)
                FileCategoryRow(Color(0xFFAED581), "Các file cài đặt", breakdown.apkBytes, onCategoryClick)
                FileCategoryRow(Color(0xFF2196F3), "Tài liệu", breakdown.docBytes, onCategoryClick)
                FileCategoryRow(Color(0xFF9E9E9E), "Thùng rác", breakdown.trashBytes) { onCategoryClick("Thùng rác") }
            } else {
                // Placeholder rows while loading
                FileCategoryRow(Color(0xFFE91E63), "Video", 0L, onCategoryClick)
                FileCategoryRow(Color(0xFFFF8A80), "Ảnh", 0L, onCategoryClick)
                FileCategoryRow(Color(0xFF4CAF50), "File đã nén", 0L, onCategoryClick)
            }

            // Error message
            volumeState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Internal Storage Card
// ─────────────────────────────────────────────────────────
@Composable
private fun InternalStorageCard(
    uiState: StorageManagerUiState,
    onCategoryClick: (String) -> Unit
) {
    Log.d("StorageCard", "InternalStorageCard: totalFormatted=${uiState.totalFormatted}, storageInfo.totalBytes=${uiState.storageInfo?.totalBytes}, volumes[0].totalBytes=${uiState.volumes.firstOrNull()?.totalBytes}")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bộ nhớ trong",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.usedPercent}% đã dùng",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF555555)
                )
                Text(
                    text = "${uiState.usedFormatted} / ${uiState.totalFormatted}",
                    fontSize = 13.sp,
                    color = Color(0xFF888888)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Segmented storage bar (9 segments)
            FullStorageBar(uiState = uiState)

            Spacer(Modifier.height(16.dp))

            // Expandable file category list
            FileCategoryListExpandable(
                uiState = uiState,
                onCategoryClick = onCategoryClick
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// Full 9-segment storage bar
// ─────────────────────────────────────────────────────────
@Composable
private fun FullStorageBar(uiState: StorageManagerUiState) {
    val total = uiState.storageInfo?.totalBytes ?: 1L
    if (total <= 0) return

    // Build segments: only include non-zero ones
    val allSegments = listOf(
        COLOR_VIDEO   to uiState.videoBytes,
        COLOR_IMAGE   to uiState.imageBytes,
        COLOR_ARCHIVE to uiState.archiveBytes,
        COLOR_APK     to uiState.apkBytes,
        COLOR_DOC     to uiState.docBytes,
        COLOR_AUDIO   to uiState.audioBytes,
        COLOR_APPS    to uiState.appsBytes,
        COLOR_SYSTEM  to uiState.systemBytes,
        COLOR_OTHER   to uiState.otherBytes,
    )

    val nonZeroSegments = allSegments.filter { (_, bytes) -> bytes > 0 }
    val usedBytes = nonZeroSegments.sumOf { (_, bytes) -> bytes }
    val freeBytes = (total - usedBytes).coerceAtLeast(0L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
    ) {
        nonZeroSegments.forEach { (color, bytes) ->
            val weight = (bytes.toFloat() / total).coerceAtLeast(0f)
            if (weight > 0.0005f) {
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
        // Free space
        val freeWeight = (freeBytes.toFloat() / total).coerceAtLeast(0f)
        if (freeWeight > 0.0005f) {
            Box(
                modifier = Modifier
                    .weight(freeWeight)
                    .fillMaxHeight()
                    .background(COLOR_FREE)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// Expandable file category list
// Collapsed: Video, Ảnh, File đã nén, Các file cài đặt, Tài liệu, File âm thanh
// Expanded: + Ứng dụng, Hệ thống, File khác, Thùng rác
// ─────────────────────────────────────────────────────────
@Composable
private fun FileCategoryListExpandable(
    uiState: StorageManagerUiState,
    onCategoryClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        // ── Always visible rows ───────────────────────────────
        FileCategoryRow(COLOR_VIDEO,   "Video",                  uiState.videoBytes,    onCategoryClick)
        FileCategoryRow(COLOR_IMAGE,   "Ảnh",                    uiState.imageBytes,    onCategoryClick)
        FileCategoryRow(COLOR_ARCHIVE, "File đã nén",            uiState.archiveBytes,  onCategoryClick)
        FileCategoryRow(COLOR_APK,    "Các file cài đặt",       uiState.apkBytes,     onCategoryClick)
        FileCategoryRow(COLOR_DOC,    "Tài liệu",               uiState.docBytes,     onCategoryClick)
        FileCategoryRow(COLOR_AUDIO,  "File âm thanh",           uiState.audioBytes,   onCategoryClick)

        // ── Expandable rows ─────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                FileCategoryRow(COLOR_APPS,   "Ứng dụng",           uiState.appsBytes,   onCategoryClick)
                FileCategoryRow(COLOR_SYSTEM, "Hệ thống",           uiState.systemBytes, onCategoryClick)
                FileCategoryRow(COLOR_OTHER,  "File khác",           uiState.otherBytes,  onCategoryClick)
                FileCategoryRow(Color(0xFF9E9E9E), "Thùng rác",    uiState.trashBytes, { onCategoryClick("Thùng rác") })
            }
        }

        // ── Expand / Collapse toggle ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "Hiện ít hơn" else "Hiện thêm",
                fontSize = 13.sp,
                color = Color(0xFF2196F3)
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded)
                    Icons.Filled.KeyboardArrowUp
                else
                    Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FileCategoryRow(
    dotColor: Color,
    label: String,
    bytes: Long,
    onClick: (String) -> Unit
) {
    // Disable click khi không có dữ liệu để tránh mở page trống
    val isEmpty = bytes <= 0L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isEmpty) Modifier
                else Modifier.clickable { onClick(label) }
            )
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isEmpty) Color(0xFFDDDDDD) else dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (isEmpty) Color(0xFFAAAAAA) else Color(0xFF444444),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatStorageSize(bytes),
            fontSize = 13.sp,
            color = if (isEmpty) Color(0xFFBBBBBB) else Color(0xFF888888)
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (isEmpty) Color(0xFFEEEEEE) else Color(0xFFCCCCCC),
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────
// Quick Actions Carousel
// ─────────────────────────────────────────────────────────
@Composable
private fun QuickActionsCarousel(
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    duplicateBytes: Long,
    largeFilesBytes: Long,
    onDuplicateClick: () -> Unit,
    onLargeFilesClick: () -> Unit
) {
    val items = listOf(
        QuickActionItem(
            icon = Icons.Outlined.FileCopy,
            label = "File trùng lặp",
            size = formatStorageSize(duplicateBytes),
            onClick = onDuplicateClick
        ),
        QuickActionItem(
            icon = Icons.Outlined.CloudUpload,
            label = "File lớn",
            size = formatStorageSize(largeFilesBytes),
            onClick = onLargeFilesClick
        )
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { item -> QuickActionCard(item = item) }
        }

        // Carousel dots
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentPage) 8.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (index == currentPage) Color(0xFF666666) else Color(0xFFCCCCCC)
                        )
                )
            }
        }
    }
}

private data class QuickActionItem(
    val icon: ImageVector,
    val label: String,
    val size: String,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionCard(item: QuickActionItem) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable { item.onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color(0xFF666666),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )
            Text(
                text = item.size,
                fontSize = 12.sp,
                color = Color(0xFF888888)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
// Suggestions Section
// ─────────────────────────────────────────────────────────
@Composable
private fun SuggestionsSection(
    uiState: StorageManagerUiState,
    onUnusedAppsClick: () -> Unit,
    onOldScreenshotsClick: () -> Unit,
    onNavigateToDuplicates: () -> Unit,
    onNavigateToLargeFiles: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Đề xuất",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF333333),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // Suggestion 1: Archive unused apps
                SuggestionCard(
                    title = "Giải phóng ${formatStorageSize(uiState.unusedAppsBytes)} dung lượng bằng cách lưu trữ ${countUnusedApps(uiState.unusedAppsBytes)} ứng dụng không dùng tới. Dữ liệu cá nhân và cài đặt của bạn sẽ được lưu lại.",
                    buttonText = "Lưu trữ ứng dụng",
                    onClick = onUnusedAppsClick
                )

                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

                // Suggestion 2: Old screenshots
                if (uiState.oldScreenshotsBytes > 0) {
                    SuggestionCard(
                        title = "Nhận ${formatStorageSize(uiState.oldScreenshotsBytes)} dung lượng bằng cách xóa ảnh chụp màn hình cũ khỏi thư mục Screenshots.",
                        buttonText = "Xem lại ảnh chụp màn hình cũ",
                        onClick = onOldScreenshotsClick
                    )
                    HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                }

                // Expandable: more suggestions
                AnimatedVisibility(visible = expanded) {
                    Column {
                        SuggestionCard(
                            title = "Nhận ${formatStorageSize(uiState.duplicateBytes)} dung lượng bằng cách xóa file trùng lặp.",
                            buttonText = "Xem file trùng lặp",
                            onClick = onNavigateToDuplicates
                        )
                        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                        SuggestionCard(
                            title = "Nhận ${formatStorageSize(uiState.largeFilesBytes)} dung lượng bằng cách xóa file lớn không cần thiết.",
                            buttonText = "Xem file lớn",
                            onClick = onNavigateToLargeFiles
                        )
                    }
                }

                // "See more" toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Ẩn bớt" else "Xem thêm",
                        fontSize = 13.sp,
                        color = Color(0xFF2196F3)
                    )
                    Icon(
                        imageVector = if (expanded)
                            Icons.Filled.KeyboardArrowUp
                        else
                            Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    title: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = Color(0xFF444444),
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(text = buttonText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ─────────────────────────────────────────────────────────
// Storage Categories (bottom section — Trash, Unused apps)
// ─────────────────────────────────────────────────────────
@Composable
private fun StorageCategoriesSection(
    uiState: StorageManagerUiState,
    onCategoryClick: (String) -> Unit,
    onTrashClick: () -> Unit,
    onUnusedAppsClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        StorageCategoryItem(
            icon = Icons.Outlined.DeleteOutline,
            iconColor = Color(0xFF888888),
            iconBg = Color(0xFFF0F0F0),
            title = "Thùng rác",
            subtitle = "Các file nằm trong Thùng rác trong các ứng dụng khác nhau",
            size = formatStorageSize(uiState.trashBytes),
            onClick = onTrashClick
        )

        StorageCategoryItem(
            icon = Icons.Outlined.Apps,
            iconColor = Color(0xFF888888),
            iconBg = Color(0xFFF0F0F0),
            title = "Ứng dụng không dùng",
            subtitle = "Các ứng dụng bạn không dùng trong 30 ngày qua",
            size = formatStorageSize(uiState.unusedAppsBytes),
            onClick = onUnusedAppsClick
        )
    }
}

@Composable
private fun StorageCategoryItem(
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    size: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF222222)
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = size,
                fontSize = 13.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.End
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Recommend Cards Section (MyFiles style)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RecommendCardsSection(
    uiState: StorageManagerUiState,
    onCardClick: (RecommendType) -> Unit
) {
    val cards = uiState.recommendCards

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "File đề xuất",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF333333)
            )

            if (uiState.isLoadingRecommend) {
                CircularProgressIndicator(
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(16.dp),
                    color = Color(0xFF2196F3)
                )
            }
        }

        if (cards.isEmpty() && !uiState.isLoadingRecommend) {
            // Không có card nào
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "Không có gì cần dọn dẹp",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF444444)
                        )
                        Text(
                            "Dung lượng của bạn đang ở mức tốt",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
            return@Column
        }

        // Card list
        cards.forEach { card ->
            RecommendStorageCard(
                card = card,
                onClick = { onCardClick(card.type) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RecommendStorageCard(
    card: RecommendCard,
    onClick: () -> Unit
) {
    val (icon, iconBg) = getRecommendCardIcon(card.type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222222)
                )
                Text(
                    text = card.description,
                    fontSize = 11.sp,
                    color = Color(0xFF888888),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            // Size
            Text(
                text = formatStorageSize(card.sizeBytes),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2196F3)
            )

            Spacer(Modifier.width(6.dp))

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun getRecommendCardIcon(type: RecommendType): Pair<ImageVector, Color> {
    return when (type) {
        RecommendType.OLD_MEDIA_FILES -> Icons.Outlined.Image to Color(0xFF9C27B0)
        RecommendType.UNNECESSARY_FILES -> Icons.Outlined.Archive to Color(0xFF4CAF50)
        RecommendType.SCREENSHOT_FILES -> Icons.Outlined.PhotoCamera to Color(0xFFFF9800)
        RecommendType.DOWNLOAD_FILES -> Icons.Outlined.Download to Color(0xFF2196F3)
        RecommendType.COMPRESSED_FILES -> Icons.Outlined.FolderOpen to Color(0xFF607D8B)
    }
}

// ─────────────────────────────────────────────────────────
// Helper
// ─────────────────────────────────────────────────────────
private fun countUnusedApps(bytes: Long): Int {
    // Approximate: each app ~50 MB average
    return (bytes / (50L * 1024 * 1024)).toInt().coerceAtLeast(1)
}
