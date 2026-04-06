package com.example.codevui.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * HighlightedText — highlight matching query parts in blue
 * Reusable cho search results: file name, folder name
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color(0xFF1A1A1A),
    highlightColor: Color = Color(0xFF1A73E8),
    maxLines: Int = 1
) {
    if (query.isBlank()) {
        Text(
            text = text,
            modifier = modifier,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val annotated = buildAnnotatedString {
        var remaining = text
        val queryLower = query.lowercase()

        while (remaining.isNotEmpty()) {
            val index = remaining.lowercase().indexOf(queryLower)
            if (index == -1) {
                withStyle(SpanStyle(color = color, fontWeight = fontWeight)) {
                    append(remaining)
                }
                break
            }

            // Before match
            if (index > 0) {
                withStyle(SpanStyle(color = color, fontWeight = fontWeight)) {
                    append(remaining.substring(0, index))
                }
            }

            // Match — highlighted
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold)) {
                append(remaining.substring(index, index + query.length))
            }

            remaining = remaining.substring(index + query.length)
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
