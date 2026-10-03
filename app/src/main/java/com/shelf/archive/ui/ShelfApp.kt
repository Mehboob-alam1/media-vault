package com.shelf.archive.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shelf.archive.ui.screens.DetailScreen
import com.shelf.archive.ui.screens.ImportScreen
import com.shelf.archive.ui.screens.LibraryScreen
import com.shelf.archive.ui.screens.SettingsScreen

@Composable
fun ShelfApp(viewModel: ShelfViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.notice?.id) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(notice.text)
        viewModel.consumeNotice()
    }
    val selected = state.files.firstOrNull { it.id == state.selectedId }
    val copyLink: (String) -> Unit = { url ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Download URL", url))
        viewModel.report("Download link copied.")
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        BackHandler(enabled = !expanded && selected != null) {
            viewModel.clearSelection()
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!expanded && selected == null) {
                    ShelfBottomBar(state.tab, viewModel::selectTab)
                }
            },
        ) { padding ->
            if (expanded) {
                Row(Modifier.padding(padding).fillMaxSize()) {
                    ShelfRail(state.tab, viewModel::selectTab)
                    Box(Modifier.weight(1.1f).fillMaxSize()) {
                        when (state.tab) {
                            ShelfTab.LIBRARY -> LibraryScreen(state, viewModel, copyLink)
                            ShelfTab.IMPORT -> ImportScreen(state, viewModel)
                            ShelfTab.SETTINGS -> SettingsScreen(state, viewModel)
                        }
                    }
                    if (state.tab == ShelfTab.LIBRARY) {
                        Box(Modifier.weight(0.9f).fillMaxSize()) {
                            if (selected == null) {
                                Text(
                                    "Choose a file to see its download link.",
                                    modifier = Modifier.padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                DetailScreen(selected, viewModel, onBack = null)
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when {
                        selected != null -> DetailScreen(
                            file = selected,
                            viewModel = viewModel,
                            onBack = viewModel::clearSelection,
                        )
                        state.tab == ShelfTab.LIBRARY -> LibraryScreen(state, viewModel, copyLink)
                        state.tab == ShelfTab.IMPORT -> ImportScreen(state, viewModel)
                        else -> SettingsScreen(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfBottomBar(tab: ShelfTab, onSelect: (ShelfTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = tab == ShelfTab.LIBRARY,
            onClick = { onSelect(ShelfTab.LIBRARY) },
            icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
            label = { Text("Library") },
        )
        NavigationBarItem(
            selected = tab == ShelfTab.IMPORT,
            onClick = { onSelect(ShelfTab.IMPORT) },
            icon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
            label = { Text("Import") },
        )
        NavigationBarItem(
            selected = tab == ShelfTab.SETTINGS,
            onClick = { onSelect(ShelfTab.SETTINGS) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text("Firebase") },
        )
    }
}

@Composable
private fun ShelfRail(tab: ShelfTab, onSelect: (ShelfTab) -> Unit) {
    NavigationRail {
        NavigationRailItem(
            selected = tab == ShelfTab.LIBRARY,
            onClick = { onSelect(ShelfTab.LIBRARY) },
            icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
            label = { Text("Library") },
        )
        NavigationRailItem(
            selected = tab == ShelfTab.IMPORT,
            onClick = { onSelect(ShelfTab.IMPORT) },
            icon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
            label = { Text("Import") },
        )
        NavigationRailItem(
            selected = tab == ShelfTab.SETTINGS,
            onClick = { onSelect(ShelfTab.SETTINGS) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text("Firebase") },
        )
    }
}
