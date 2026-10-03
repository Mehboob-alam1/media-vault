package com.shelf.archive.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.shelf.archive.R
import com.shelf.archive.domain.FileKind
import com.shelf.archive.domain.LibraryFilter
import com.shelf.archive.domain.StoredFile
import com.shelf.archive.domain.accepts
import com.shelf.archive.domain.formatBytes
import com.shelf.archive.domain.matchesQuery
import com.shelf.archive.ui.KindGlyph
import com.shelf.archive.ui.LinkLine
import com.shelf.archive.ui.ShelfHeader
import com.shelf.archive.ui.ShelfTab
import com.shelf.archive.ui.ShelfUiState
import com.shelf.archive.ui.ShelfViewModel
import com.shelf.archive.ui.relativeTime

@Composable
fun LibraryScreen(
    state: ShelfUiState,
    viewModel: ShelfViewModel,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = state.files.filter { file ->
        state.filter.accepts(file) && file.matchesQuery(state.search)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val subtitle = when {
                state.connecting -> "Connecting to Firebase…"
                state.connected -> "${state.files.size} file${if (state.files.size == 1) "" else "s"} in library/files"
                else -> "Not connected"
            }
            ShelfHeader("Shelf", subtitle)
        }
        if (!state.connected && !state.connecting) {
            item {
                EmptyLibrary(
                    title = "Connect your Firebase project",
                    body = "Shelf uploads files to your Storage bucket and writes each download URL into Realtime Database.",
                    action = "Set up Firebase",
                    onAction = { viewModel.selectTab(ShelfTab.SETTINGS) },
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search name or download URL") },
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
        }
        state.libraryError?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.libraryLoading && state.files.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.connected && visible.isEmpty()) {
            item {
                val title = if (state.search.isNotBlank() || state.filter != LibraryFilter.ALL) {
                    "Nothing matches"
                } else {
                    "The library is empty"
                }
                val body = if (state.files.isEmpty()) {
                    "Import a gallery photo, a WhatsApp image, a PDF, an Office file, or a .txt file."
                } else {
                    "Try another filter, or clear the search."
                }
                EmptyLibrary(
                    title = title,
                    body = body,
                    action = "Import files",
                    onAction = { viewModel.selectTab(ShelfTab.IMPORT) },
                )
            }
        }
        items(visible, key = { it.id }) { file ->
            FileCard(
                file = file,
                onOpen = { viewModel.selectFile(file.id) },
                onCopy = { onCopy(file.downloadUrl) },
            )
        }
    }
}

@Composable
private fun FileCard(file: StoredFile, onOpen: () -> Unit, onCopy: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onCopy),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileThumb(file)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${file.category.label} · ${formatBytes(file.sizeBytes)} · ${relativeTime(file.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinkLine(file.downloadUrl)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy download link")
            }
        }
    }
}

@Composable
private fun FileThumb(file: StoredFile) {
    if (file.category == FileKind.IMAGE || file.category == FileKind.WHATSAPP) {
        AsyncImage(
            model = file.downloadUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        KindGlyph(file.category, file.name)
    }
}

@Composable
private fun EmptyLibrary(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_empty_shelf),
            contentDescription = null,
            modifier = Modifier.size(width = 160.dp, height = 96.dp),
        )
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}
