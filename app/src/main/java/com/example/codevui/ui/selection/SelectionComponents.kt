package com.example.codevui.ui.selection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SelectionTopBar — thay thế TopAppBar khi ở selection mode
 * Hiển thị: ○ Tất cả | "Đã chọn X" | Thoát
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onExit: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            Row(
                modifier = Modifier
                    .clickable(onClick = onSelectAll)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionCheckbox(
                    isSelected = selectedCount == totalCount && totalCount > 0,
                    onClick = onSelectAll
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Tất cả",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }
        },
        title = {
            Text(
                text = "Đã chọn $selectedCount",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
        },
        actions = {
            TextButton(onClick = onExit) {
                Text(
                    text = "Thoát",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White
        )
    )
}

/**
 * SelectionBottomBar — bottom action bar khi có items được chọn
 * Hiển thị: Di chuyển | Sao chép | Chia sẻ | Xóa | N.hơn (popup menu)
 *
 * Menu visibility theo MyFiles pattern:
 * - Nén: null = ẩn (khi có archive files được chọn)
 * - Giải nén: null = ẩn (khi không có archive)
 * - Thêm vào yêu thích: null = ẩn (khi tất cả đã favorite)
 * - Xóa khỏi yêu thích: null = ẩn (khi không có favorite)
 */
@Composable
fun SelectionBottomBar(
    modifier: Modifier = Modifier,
    onMove: () -> Unit = {},
    onCopy: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopyToClipboard: () -> Unit = {},  // Copy text paths (old behavior)
    onCopyToFileClipboard: () -> Unit = {},  // Copy files to clipboard (new)
    onDetails: () -> Unit = {},
    onRename: () -> Unit = {},
    onCompress: (() -> Unit)? = null,   // null = ẩn (khi có archive files)
    onExtract: (() -> Unit)? = null,    // null = ẩn (khi không có archive)
    onAddToFavorites: (() -> Unit)? = null,  // null = ẩn (khi đã favorite hết)
    onRemoveFromFavorites: (() -> Unit)? = null,  // null = ẩn (khi không có favorite)
    onAddToHomeScreen: () -> Unit = {}
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shadowElevation = 8.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomAction(Icons.Outlined.DriveFileMove, "Di chuyển", onClick = onMove)
            BottomAction(Icons.Outlined.ContentCopy, "Sao chép", onClick = onCopy)
            BottomAction(Icons.Outlined.Share, "Chia sẻ", onClick = onShare)
            BottomAction(Icons.Outlined.Delete, "Xóa", onClick = onDelete)

            // N.hơn with DropdownMenu
            Box {
                BottomAction(Icons.Outlined.MoreHoriz, "N.hơn", onClick = { showMoreMenu = true })

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    MoreMenuItem(
                        icon = Icons.Outlined.ContentPaste,
                        text = "Sao chép vào bộ nhớ tạm",
                        onClick = {
                            showMoreMenu = false
                            onCopyToFileClipboard()
                        }
                    )
                    MoreMenuItem(
                        icon = Icons.Outlined.Info,
                        text = "Chi tiết",
                        onClick = {
                            showMoreMenu = false
                            onDetails()
                        }
                    )
                    MoreMenuItem(
                        icon = Icons.Outlined.Edit,
                        text = "Đổi tên",
                        onClick = {
                            showMoreMenu = false
                            onRename()
                        }
                    )
                    // Nén — chỉ hiển thị khi onCompress != null (MyFiles pattern)
                    onCompress?.let {
                        MoreMenuItem(
                            icon = Icons.Outlined.FolderZip,
                            text = "Nén",
                            onClick = {
                                showMoreMenu = false
                                it()
                            }
                        )
                    }
                    // Giải nén — chỉ hiển thị khi onExtract != null (MyFiles pattern)
                    onExtract?.let {
                        MoreMenuItem(
                            icon = Icons.Outlined.FolderOpen,
                            text = "Giải nén",
                            onClick = {
                                showMoreMenu = false
                                it()
                            }
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = Color(0xFFE0E0E0)
                    )
                    // Thêm vào yêu thích — chỉ hiển thị khi onAddToFavorites != null (MyFiles pattern)
                    onAddToFavorites?.let {
                        MoreMenuItem(
                            icon = Icons.Outlined.FavoriteBorder,
                            text = "Thêm vào mục yêu thích",
                            onClick = {
                                showMoreMenu = false
                                it()
                            }
                        )
                    }
                    // Xóa khỏi yêu thích — chỉ hiển thị khi onRemoveFromFavorites != null (MyFiles pattern)
                    onRemoveFromFavorites?.let {
                        MoreMenuItem(
                            icon = Icons.Filled.Favorite,
                            text = "Xóa khỏi yêu thích",
                            onClick = {
                                showMoreMenu = false
                                it()
                            }
                        )
                    }
                    MoreMenuItem(
                        icon = Icons.Outlined.Home,
                        text = "Thêm vào Màn hình chờ",
                        onClick = {
                            showMoreMenu = false
                            onAddToHomeScreen()
                        }
                    )
                }
            }
        }
    }
}

/**
 * MoreMenuItem — single item in "N.hơn" dropdown menu
 */
@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = Color(0xFF444444),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(text, fontSize = 15.sp, color = Color(0xFF1A1A1A))
            }
        },
        onClick = onClick
    )
}

/**
 * BottomAction — single action button in bottom bar (reusable)
 */
@Composable
private fun BottomAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF444444),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF444444)
        )
    }
}

/**
 * SelectionCheckbox — circular checkbox giống Samsung
 * Supports tri-state: unselected, fully selected, partially selected (indeterminate)
 * @param showBackground if true, shows filled background for PARTIAL (indeterminate box).
 *                        if false, PARTIAL shows nothing instead of indeterminate box.
 */
@Composable
fun SelectionCheckbox(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    triState: TriState? = null,  // null = tự suy từ isSelected, NONE/PARTIAL/ALL = override
    showBackground: Boolean = true
) {
    // Nếu triState không được truyền (null), tự suy từ isSelected
    val effectiveState = triState ?: if (isSelected) TriState.ALL else TriState.NONE

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .then(
                when (effectiveState) {
                    TriState.ALL -> Modifier.background(Color(0xFF1A73E8))
                    TriState.PARTIAL -> if (showBackground) {
                        Modifier.background(Color(0xFF1A73E8).copy(alpha = 0.5f))
                    } else {
                        Modifier.border(2.dp, Color(0xFFCCCCCC), CircleShape)
                    }
                    TriState.NONE -> Modifier.border(2.dp, Color(0xFFCCCCCC), CircleShape)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (effectiveState) {
            TriState.ALL -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            TriState.PARTIAL -> {
                if (showBackground) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }
            }
            TriState.NONE -> { /* empty */ }
        }
    }
}

enum class TriState {
    NONE, PARTIAL, ALL
}