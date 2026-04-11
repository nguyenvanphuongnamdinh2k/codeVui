package com.example.codevui.ui.common.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dialog đổi tên nhiều file cùng lúc (Windows-style).
 * Mỗi item hiển thị tên cũ → TextField để sửa tên mới.
 * Nút "Đổi tên" chỉ enable khi có ít nhất 1 item thay đổi.
 */
@Composable
fun BatchRenameDialog(
    items: List<RenameItem>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit  // Map<oldPath, newName>
) {
    // Map từ path → tên mới (mutable)
    var editedNames by remember(items) {
        mutableStateOf(items.associate { it.path to it.originalName })
    }

    val hasChanges = remember(editedNames) {
        items.any { editedNames[it.path] != it.originalName }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val results = editedNames.filter { (path, name) ->
                        name.isNotBlank() && name != items.find { it.path == path }?.originalName
                    }
                    onConfirm(results)
                },
                enabled = hasChanges
            ) {
                Text("Đổi tên")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
        title = {
            Text(
                text = if (items.size == 1) "Đổi tên" else "Đổi tên ${items.size} mục",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nhập tên mới cho từng mục:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        BatchRenameItemRow(
                            index = index + 1,
                            originalName = item.originalName,
                            newName = editedNames[item.path] ?: item.originalName,
                            onNewNameChange = { newName ->
                                editedNames = editedNames + (item.path to newName)
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(0.9f)
    )
}

@Composable
private fun BatchRenameItemRow(
    index: Int,
    originalName: String,
    newName: String,
    onNewNameChange: (String) -> Unit
) {
    val isChanged = newName != originalName

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index
        Text(
            text = "$index.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            // Original name
            Text(
                text = originalName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            // Input field
            OutlinedTextField(
                value = newName,
                onValueChange = onNewNameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                // Highlight border khi có thay đổi
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isChanged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = if (isChanged) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

/**
 * Item data cho BatchRenameDialog
 */
data class RenameItem(
    val path: String,        // Full path (để rename)
    val originalName: String  // Tên gốc (để so sánh)
)
