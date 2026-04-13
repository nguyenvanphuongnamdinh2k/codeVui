package com.example.codevui.ui.common.dialogs

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.codevui.model.RecentFile
import com.example.codevui.util.Logger
import java.io.File

private val log = Logger("OpenWithDialog")

// ── Data class ───────────────────────────────────────────────────────────────

data class AppInfo(
    val name: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable,
    val subtitle: String? = null   // tên app nếu khác với tên activity
)

// ── Composable Dialog ─────────────────────────────────────────────────────────

/**
 * Bottom sheet chọn ứng dụng mở file — Samsung MyFiles style.
 *
 * Flow:
 *  1. User tap vào icon app → app được highlight
 *  2. "Chỉ một lần" → mở file, KHÔNG lưu preference
 *  3. "Luôn chọn"   → lưu app mặc định cho extension này, mở file
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenWithDialog(
    file: RecentFile,
    onDismiss: () -> Unit,
    onJustOnce: (AppInfo) -> Unit,
    onAlways: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val apps = remember(file.path) { queryAppsForFile(context, file) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }

    // Nếu không có app nào → toast và dismiss
    LaunchedEffect(apps) {
        if (apps.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                "Không tìm thấy ứng dụng để mở file này",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            onDismiss()
        }
    }
    if (apps.isEmpty()) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        // ── Header: "Mở bằng" + info icon ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mở bằng",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { /* TODO: info về default apps */ }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Thông tin",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Grid apps — 4 cột ────────────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(apps, key = { it.packageName + it.activityName }) { app ->
                AppGridItem(
                    app = app,
                    isSelected = selectedApp?.packageName == app.packageName &&
                            selectedApp?.activityName == app.activityName,
                    onSelect = { selectedApp = app }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Nút "Chỉ một lần" | "Luôn chọn" ─────────────────────────────
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        val selected = selectedApp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            // Chỉ một lần
            TextButton(
                onClick = { selected?.let { onJustOnce(it) } },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                enabled = selected != null
            ) {
                Text(
                    text = "Chỉ một lần",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (selected != null)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Separator dọc
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight(0.6f)
                    .align(Alignment.CenterVertically)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Luôn chọn
            TextButton(
                onClick = { selected?.let { onAlways(it) } },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                enabled = selected != null
            ) {
                Text(
                    text = "Luôn chọn",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected != null)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }

        // Padding cho navigation bar
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

// ── App grid item ─────────────────────────────────────────────────────────────

@Composable
private fun AppGridItem(
    app: AppInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val bitmap = remember(app.packageName + app.activityName) {
        app.icon.toBitmapSafe(size = 108)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App icon
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = app.name,
            modifier = Modifier.size(52.dp)
        )

        Spacer(Modifier.height(6.dp))

        // App name
        Text(
            text = app.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Subtitle (tên app gốc, nếu khác)
        if (app.subtitle != null) {
            Text(
                text = app.subtitle,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 12.sp
            )
        }
    }
}

// ── Helper functions ──────────────────────────────────────────────────────────

/**
 * Query danh sách app có thể mở file này từ PackageManager.
 */
fun queryAppsForFile(context: android.content.Context, file: RecentFile): List<AppInfo> {
    val ext = file.path.substringAfterLast('.', "").lowercase()
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

    return try {
        val javaFile = File(file.path)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", javaFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            0
        }

        context.packageManager
            .queryIntentActivities(intent, flags)
            .filter { it.activityInfo != null }
            .map { resolveInfo ->
                val activityLabel = resolveInfo.loadLabel(context.packageManager).toString()
                val appLabel = resolveInfo.activityInfo.applicationInfo
                    .loadLabel(context.packageManager).toString()

                // Hiện subtitle nếu tên activity khác tên app
                val subtitle = if (activityLabel != appLabel) appLabel else null

                AppInfo(
                    name = activityLabel,
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    icon = resolveInfo.loadIcon(context.packageManager),
                    subtitle = subtitle
                )
            }
            .sortedBy { it.name }
    } catch (e: Exception) {
        log.e("Lỗi khi query apps cho file ${file.path}", e)
        emptyList()
    }
}

/**
 * Mở file bằng DefaultAppInfo đã lưu (không cần icon).
 */
fun openFileWithSavedDefault(
    context: android.content.Context,
    file: RecentFile,
    defaultApp: com.example.codevui.data.DefaultAppInfo
) {
    openFileWithApp(
        context, file,
        AppInfo(
            name = defaultApp.packageName,
            packageName = defaultApp.packageName,
            activityName = defaultApp.activityName,
            icon = android.graphics.drawable.ColorDrawable()
        )
    )
}

/**
 * Mở file bằng một app cụ thể (theo packageName + activityName).
 */
fun openFileWithApp(context: android.content.Context, file: RecentFile, appInfo: AppInfo) {
    val ext = file.path.substringAfterLast('.', "").lowercase()
    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

    try {
        val javaFile = File(file.path)
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", javaFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setClassName(appInfo.packageName, appInfo.activityName)
        }
        context.startActivity(intent)
        log.d("Mở ${file.name} bằng ${appInfo.name} (${appInfo.packageName})")
    } catch (e: Exception) {
        log.e("Lỗi khi mở file ${file.path} bằng ${appInfo.packageName}", e)
        android.widget.Toast.makeText(
            context,
            "Không thể mở file bằng ${appInfo.name}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Chuyển Drawable thành Bitmap để hiển thị trong Compose.
 * Xử lý cả AdaptiveIconDrawable (API 26+) và BitmapDrawable thường.
 */
private fun Drawable.toBitmapSafe(size: Int = 108): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bitmap
}
