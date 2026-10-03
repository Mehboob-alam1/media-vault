package com.shelf.archive.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.archive.data.RemoteFiles
import com.shelf.archive.domain.StoredFile
import com.shelf.archive.domain.formatBytes
import com.shelf.archive.ui.MetaRow
import com.shelf.archive.ui.ShelfViewModel
import com.shelf.archive.ui.absoluteTime
import com.shelf.archive.ui.preview.FilePreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    file: StoredFile,
    viewModel: ShelfViewModel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember(file.id) { mutableStateOf(false) }
    var opening by remember(file.id) { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(file.name, maxLines = 1, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FilePreview(file)
            Text(
                text = file.downloadUrl,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    copy(context, file.downloadUrl)
                    viewModel.report("Download link copied.")
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text(" Copy link", modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(onClick = { share(context, file) }) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text(" Share")
                }
            }
            OutlinedButton(
                onClick = { openLink(context, file.downloadUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                Text("  Open download link")
            }
            OutlinedButton(
                onClick = {
                    if (opening) return@OutlinedButton
                    opening = true
                    scope.launch {
                        try {
                            val destination = withContext(Dispatchers.IO) {
                                val fileOnDisk = RemoteFiles.destination(
                                    context,
                                    file.id,
                                    file.downloadUrl,
                                    file.name,
                                )
                                RemoteFiles.download(
                                    file.downloadUrl,
                                    fileOnDisk,
                                    maxBytes = 100L * 1024 * 1024,
                                )
                                fileOnDisk
                            }
                            RemoteFiles.open(context, destination, file.mimeType)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            viewModel.report(error.message ?: "Couldn't open this file.")
                        } finally {
                            opening = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !opening,
            ) {
                Text(if (opening) "Downloading…" else "Open in another app")
            }
            MetaRow("Type", file.category.label)
            MetaRow("Size", formatBytes(file.sizeBytes))
            MetaRow("Added", absoluteTime(file.createdAt))
            MetaRow("Source", sourceLabel(file.source))
            if (file.device.isNotBlank()) MetaRow("Phone", file.device)
            MetaRow("Database", "library/files/${file.id}")
            MetaRow("Storage", file.storagePath.ifBlank { "—" })
            TextButton(onClick = viewModel::refreshSelectedLink) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("  Refresh download link")
            }
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("  Delete from Firebase", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this file?") },
            text = {
                Text("Shelf deletes it from Firebase Storage and removes the database record. The copy on this phone stays.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
        )
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "gallery" -> "Gallery"
    "whatsapp" -> "WhatsApp"
    "files" -> "Files"
    else -> source
}

private fun copy(context: Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Download URL", url))
}

private fun share(context: Context, file: StoredFile) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        putExtra(Intent.EXTRA_TEXT, file.downloadUrl)
    }
    context.startActivity(Intent.createChooser(send, "Share download link"))
}

private fun openLink(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
