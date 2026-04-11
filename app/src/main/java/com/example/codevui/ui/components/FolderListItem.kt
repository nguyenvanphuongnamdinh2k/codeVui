package com.example.codevui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.example.codevui.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.codevui.model.FolderItem
import com.example.codevui.ui.selection.SelectionCheckbox
import com.example.codevui.util.formatDateFull

/**
 * FolderListItem — Reusable folder row
 * Supports: normal mode + selection mode (checkbox + long press)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListItem(
    folder: FolderItem,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isLandscape: Boolean = false,
    isFavorite: Boolean = false,
    isRenamed: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onToggleSelect: () -> Unit = {}
) {
    val verticalPadding = if (isLandscape) 6.dp else 12.dp
    val horizontalPadding = if (isLandscape) 12.dp else 20.dp
    val iconSize = if (isLandscape) 40.dp else 56.dp
    val emojiSize = if (isLandscape) 28.sp else 36.sp
    val titleFontSize = if (isLandscape) 13.sp else 15.sp
    val metaFontSize = if (isLandscape) 11.sp else 13.sp
    val spacerWidth = if (isLandscape) 10.dp else 16.dp
    val innerSpacerHeight = if (isLandscape) 2.dp else 4.dp

    // Animate background khi vừa rename (xanh nhạt → transparent sau 2.5s)
    val animatedBg by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFFF0F6FF)
            isRenamed -> Color(0xFFE8F5E9)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 600),
        label = "folderRenameHighlight"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onToggleSelect() else onClick()
                    },
                    onLongClick = {
                        if (!isSelectionMode) onLongClick()
                    }
                )
                .background(animatedBg)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox khi selection mode
            if (isSelectionMode) {
                SelectionCheckbox(
                    isSelected = isSelected,
                    onClick = onToggleSelect
                )
                Spacer(Modifier.width(if (isLandscape) 8.dp else 12.dp))
            }

            // Folder icon
            FolderIcon(size = iconSize, emojiSize = emojiSize)

            Spacer(Modifier.width(spacerWidth))

            // Folder name + date
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF1A1A1A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(innerSpacerHeight))
                Text(
                    text = formatDateFull(folder.dateModified),
                    fontSize = metaFontSize,
                    color = Color(0xFF999999)
                )
            }

            // Item count
            Text(
                text = "${folder.itemCount} mục",
                fontSize = metaFontSize,
                color = Color(0xFF999999)
            )

            // Favorite star (giống MyFiles — hiện cả ở selection mode)
            if (isFavorite) {
                Spacer(Modifier.width(if (isLandscape) 8.dp else 12.dp))
                Icon(
                    painter = painterResource(id = R.drawable.favorite_icon),
                    contentDescription = "Yêu thích",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(if (isLandscape) 16.dp else 18.dp)
                )
            }
        }
        if (showDivider) {
            val dividerStart = when {
                isSelectionMode && isLandscape -> 92.dp
                isSelectionMode -> 132.dp
                isLandscape -> 62.dp
                else -> 92.dp
            }
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = dividerStart,
                    end = horizontalPadding
                ),
                thickness = 0.5.dp,
                color = Color(0xFFF0F0F0)
            )
        }
    }
}

@Composable
private fun FolderIcon(
    size: androidx.compose.ui.unit.Dp = 56.dp,
    emojiSize: androidx.compose.ui.unit.TextUnit = 36.sp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "📁", fontSize = emojiSize)
    }
}
