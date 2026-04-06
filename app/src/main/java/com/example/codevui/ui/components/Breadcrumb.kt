package com.example.codevui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Breadcrumb — Reusable multi-segment navigation breadcrumb
 * Single segment:  🏠 > Ảnh          659 MB
 * Multi segment:   🏠 > Bộ nhớ trong > DCIM
 */
@Composable
fun Breadcrumb(
    segments: List<String>,
    modifier: Modifier = Modifier,
    trailing: String = "",
    count: Int? = null,
    compact: Boolean = false,
    onHomeClick: () -> Unit = {},
    onSegmentClick: (Int) -> Unit = {}
) {
    val hPad = if (compact) 10.dp else 16.dp
    val vPad = if (compact) 6.dp else 12.dp
    val iconSize = if (compact) 16.dp else 20.dp
    val fontSize = if (compact) 12.sp else 14.sp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = hPad, vertical = vPad)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "Home",
            tint = Color(0xFF888888),
            modifier = Modifier
                .size(iconSize)
                .clickable(onClick = onHomeClick)
        )

        segments.forEachIndexed { index, segment ->
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(iconSize)
            )

            val isLast = index == segments.lastIndex
            Text(
                text = segment,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                color = if (isLast) Color(0xFF1A73E8) else Color(0xFF888888),
                modifier = Modifier.clickable(enabled = !isLast) {
                    onSegmentClick(index)
                }
            )
        }

        // Count badge (e.g. "1203 ảnh") + trailing size
        if (count != null || trailing.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            if (count != null) {
                Text(
                    text = count.toString(),
                    fontSize = if (compact) 11.sp else 13.sp,
                    color = Color(0xFF888888)
                )
                Spacer(Modifier.width(4.dp))
            }
            if (trailing.isNotEmpty()) {
                Text(
                    text = trailing,
                    fontSize = if (compact) 11.sp else 13.sp,
                    color = Color(0xFF888888)
                )
            }
        }
    }
}

/**
 * Convenience overload — single segment (backwards compatible)
 */
@Composable
fun Breadcrumb(
    title: String,
    totalSize: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onHomeClick: () -> Unit = {}
) {
    Breadcrumb(
        segments = listOf(title),
        trailing = totalSize,
        count = null,
        modifier = modifier,
        compact = compact,
        onHomeClick = onHomeClick
    )
}
