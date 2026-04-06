package com.example.codevui.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.codevui.Screen

/**
 * AdaptiveLayout — landscape: DrawerPane (collapsible) + content
 *                   portrait: content only
 */
@Composable
fun AdaptiveLayout(
    currentScreen: Screen,
    isDrawerExpanded: Boolean,
    onToggleDrawer: () -> Unit,
    storageUsed: String = "",
    storageTotal: String = "",
    onNavigate: (Screen) -> Unit,
    content: @Composable () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
        ) {
            DrawerPane(
                currentScreen = currentScreen,
                isExpanded = isDrawerExpanded,
                onToggleExpand = onToggleDrawer,
                storageUsed = storageUsed,
                storageTotal = storageTotal,
                onNavigate = onNavigate
            )
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 0.5.dp,
                color = Color(0xFFE0E0E0)
            )
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    } else {
        content()
    }
}