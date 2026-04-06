package com.example.codevui.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.codevui.data.FileRepository

/**
 * SortBar — Reusable sort + filter controls
 * Left side: "Cần thiết / Tất cả" filter dropdown (only at root / "Bộ nhớ trong")
 * Right side: sort type (Ngày/Tên/Kích thước) + direction (↑/↓)
 */
@Composable
fun SortBar(
    sortBy: FileRepository.SortBy,
    ascending: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showEssentialFilter: Boolean = false,      // true = show filter at root
    showEssentialOnly: Boolean = false,         // current filter state
    onSortChanged: (FileRepository.SortBy) -> Unit = {},
    onToggleDirection: () -> Unit = {},
    onFilterChanged: (Boolean) -> Unit = {}       // true = essential only
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val hPad = if (compact) 12.dp else 20.dp
    val vPad = if (compact) 4.dp else 8.dp
    val fontSize = if (compact) 12.sp else 13.sp
    val iconSize = if (compact) 14.dp else 16.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = hPad, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left: "Cần thiết / Tất cả" filter (only at root / "Bộ nhớ trong") ──
        if (showEssentialFilter) {
            Box {
                Row(
                    modifier = Modifier.clickable { showFilterMenu = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showEssentialOnly) "Cần thiết" else "Tất cả",
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1A73E8)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(iconSize)
                    )
                }

                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    // "Tất cả" option
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Tất cả",
                                    color = if (!showEssentialOnly) Color(0xFF1A73E8) else Color(0xFF333333)
                                )
                                if (!showEssentialOnly) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF1A73E8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onFilterChanged(false)
                            showFilterMenu = false
                        }
                    )
                    // "Cần thiết" option
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Cần thiết",
                                    color = if (showEssentialOnly) Color(0xFF1A73E8) else Color(0xFF333333)
                                )
                                if (showEssentialOnly) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF1A73E8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onFilterChanged(true)
                            showFilterMenu = false
                        }
                    )
                }
            }
        } else {
            // Placeholder spacer to keep right-side controls aligned
            Spacer(Modifier.width(80.dp))
        }

        Spacer(Modifier.weight(1f))

        // ── Right: Sort type selector ──
        Box {
            Row(
                modifier = Modifier.clickable { showSortMenu = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(iconSize)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = when (sortBy) {
                        FileRepository.SortBy.DATE -> "Ngày"
                        FileRepository.SortBy.NAME -> "Tên"
                        FileRepository.SortBy.SIZE -> "Kích thước"
                    },
                    fontSize = fontSize,
                    color = Color(0xFF666666)
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                FileRepository.SortBy.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when (sort) {
                                    FileRepository.SortBy.DATE -> "Ngày"
                                    FileRepository.SortBy.NAME -> "Tên"
                                    FileRepository.SortBy.SIZE -> "Kích thước"
                                }
                            )
                        },
                        onClick = {
                            onSortChanged(sort)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.width(if (compact) 4.dp else 8.dp))

        // ── Sort direction toggle ──
        IconButton(
            onClick = onToggleDirection,
            modifier = Modifier.size(if (compact) 28.dp else 32.dp)
        ) {
            Icon(
                imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (ascending) "Tăng dần" else "Giảm dần",
                tint = Color(0xFF888888),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
