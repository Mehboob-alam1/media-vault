package com.shelf.archive.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shelf.archive.data.WhatsAppLocations
import com.shelf.archive.domain.FileKind
import com.shelf.archive.domain.formatBytes
import com.shelf.archive.ui.KindGlyph
import com.shelf.archive.ui.SectionLabel
import com.shelf.archive.ui.ShelfHeader
import com.shelf.archive.ui.ShelfUiState
import com.shelf.archive.ui.ShelfViewModel
import com.shelf.archive.ui.hasImagePermission
import com.shelf.archive.ui.imageReadPermission

@Composable
fun ImportScreen(state: ShelfUiState, viewModel: ShelfViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        viewModel.stage(uris, "gallery")
    }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.stage(uris, "files")
    }
    val whatsAppFolder = rememberLauncherForActivityResult(OpenDocumentTreeAt(WhatsAppLocations.images)) { uri ->
        if (uri != null) viewModel.scanTree(uri)
    }
    val businessFolder = rememberLauncherForActivityResult(OpenDocumentTreeAt(WhatsAppLocations.businessImages)) { uri ->
        if (uri != null) viewModel.scanTree(uri)
    }
    val photosPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (hasImagePermission(context)) viewModel.scanWhatsAppGallery()
        else viewModel.report("Photo access is off. Choose the WhatsApp folder instead.")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ShelfHeader(
                title = "Import",
                subtitle = "Gallery photos, WhatsApp images, PDFs, Office files, and text. Keep Shelf open while an upload runs.",
            )
        }
        item {
            ActionCard(
                icon = Icons.Outlined.Image,
                title = "Gallery",
                body = "Photos from the camera roll and other albums.",
                onClick = {
                    gallery.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                },
            )
        }
        item {
            ActionCard(
                icon = Icons.Outlined.FolderOpen,
                title = "WhatsApp images",
                body = "Scan images the gallery already has, or open the WhatsApp media folder if they are hidden.",
                onClick = {
                    if (hasImagePermission(context)) viewModel.scanWhatsAppGallery()
                    else photosPermission.launch(imageReadPermission())
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { whatsAppFolder.launch(Unit) }) { Text("WhatsApp folder") }
                TextButton(onClick = { businessFolder.launch(Unit) }) { Text("Business folder") }
            }
        }
        item {
            ActionCard(
                icon = Icons.Outlined.Description,
                title = "PDF, Office, and text",
                body = "Word, Excel, PowerPoint, OpenDocument, PDF, and .txt files. Other types are skipped.",
                onClick = { documents.launch(arrayOf("*/*")) },
            )
        }
        if (state.discovering) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Looking through files…")
                }
            }
        }
        if (state.discovered.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("${state.discoveredSelected.size} of ${state.discovered.size} selected")
                    Row {
                        TextButton(onClick = { viewModel.setDiscoveredSelected(true) }) { Text("All") }
                        TextButton(onClick = { viewModel.setDiscoveredSelected(false) }) { Text("None") }
                    }
                }
            }
            items(state.discovered.chunked(3), key = { row -> row.joinToString { it.uri } }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { file ->
                        DiscoveredThumb(
                            name = file.name,
                            uri = file.uri,
                            image = file.category == FileKind.IMAGE || file.category == FileKind.WHATSAPP,
                            selected = file.uri in state.discoveredSelected,
                            onClick = { viewModel.toggleDiscovered(file.uri) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::addDiscoveredToStaged, enabled = state.discoveredSelected.isNotEmpty()) {
                        Text("Add selected")
                    }
                    TextButton(onClick = viewModel::clearDiscovered) { Text("Clear") }
                }
            }
        }
        if (state.staged.isNotEmpty()) {
            item { SectionLabel("Ready to upload") }
            items(state.staged, key = { it.uri }) { file ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${file.category.label} · ${formatBytes(file.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.removeStaged(file.uri) }, enabled = !state.uploading) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove ${file.name}")
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = viewModel::uploadStaged,
                    enabled = !state.uploading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.uploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(if (state.uploading) "Uploading…" else "Upload ${state.staged.size} to Firebase")
                }
            }
        }
        if (state.uploads.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel("This batch")
                    if (!state.uploading) {
                        TextButton(onClick = viewModel::dismissUploads) { Text("Dismiss") }
                    }
                }
            }
            items(state.uploads, key = { it.uri }) { upload ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(upload.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        upload.error != null -> Text(upload.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        upload.done -> Text("Stored with its download URL.", style = MaterialTheme.typography.bodySmall)
                        else -> LinearProgressIndicator(
                            progress = { upload.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DiscoveredThumb(
    name: String,
    uri: String,
    image: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(width = if (selected) 2.dp else 1.dp, color = border, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (image) {
            AsyncImage(
                model = uri,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            KindGlyph(FileKind.PDF, name)
        }
    }
}

private class OpenDocumentTreeAt(private val initial: Uri) : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            }
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}
