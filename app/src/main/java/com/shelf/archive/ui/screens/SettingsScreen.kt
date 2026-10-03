package com.shelf.archive.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.archive.domain.FirebaseConfig
import com.shelf.archive.domain.FirebaseRules
import com.shelf.archive.ui.NoticeCard
import com.shelf.archive.ui.SectionLabel
import com.shelf.archive.ui.ShelfHeader
import com.shelf.archive.ui.ShelfUiState
import com.shelf.archive.ui.ShelfViewModel

@Composable
fun SettingsScreen(state: ShelfUiState, viewModel: ShelfViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(state.config ?: FirebaseConfig()) }
    var revealKey by remember { mutableStateOf(false) }
    LaunchedEffect(state.importGeneration) {
        state.imported?.let { draft = it }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importGoogleServices(uri)
    }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShelfHeader(
            title = "Firebase",
            subtitle = if (state.connected) {
                "Connected. Every phone using this project shares one library."
            } else {
                "Shelf stores files in your project, not on a Shelf server."
            },
        )
        if (state.uid != null) {
            NoticeCard("Signed in anonymously as ${state.uid}. Records are under library/files, and each one has a downloadUrl.")
        }
        state.importWarning?.let { NoticeCard(it) }

        SectionLabel("1  Register this Android app")
        Text(
            "In Firebase, add an Android app with this package name. Paste the SHA-1 if the console asks for it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CopyRow("Package", state.packageName, context, viewModel)
        CopyRow("SHA-1", state.sha1, context, viewModel)

        SectionLabel("2  Turn on the products")
        Text(
            "Enable Authentication → Anonymous. Create a Realtime Database and a Storage bucket. Then publish both rule sets below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RuleBlock("Realtime Database rules", FirebaseRules.database, context, viewModel)
        RuleBlock("Storage rules", FirebaseRules.storage, context, viewModel)

        SectionLabel("3  Paste the project")
        OutlinedButton(
            onClick = { importJson.launch(arrayOf("application/json", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import google-services.json")
        }
        OutlinedTextField(
            value = draft.projectId,
            onValueChange = { draft = draft.copy(projectId = it) },
            label = { Text("Project ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.applicationId,
            onValueChange = { draft = draft.copy(applicationId = it) },
            label = { Text("Android app ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.apiKey,
            onValueChange = { draft = draft.copy(apiKey = it) },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { revealKey = !revealKey }) {
                    Icon(
                        if (revealKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (revealKey) "Hide API key" else "Show API key",
                    )
                }
            },
        )
        OutlinedTextField(
            value = draft.storageBucket,
            onValueChange = { draft = draft.copy(storageBucket = it) },
            label = { Text("Storage bucket") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.databaseUrl,
            onValueChange = { draft = draft.copy(databaseUrl = it) },
            label = { Text("Realtime Database URL") },
            supportingText = {
                Text("If the JSON has no firebase_url, copy the URL from the Realtime Database page. A region URL ends in firebasedatabase.app.")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { viewModel.saveAndConnect(draft) },
            enabled = !state.connecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.connecting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(if (state.connected) "Save and reconnect" else "Connect")
        }
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.firebase.google.com/")))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Firebase console")
        }
        if (state.connected) {
            TextButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Disconnect this phone")
            }
        }
        Text(
            "Anyone with a download link can fetch that file. The link is saved on the database record as downloadUrl.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CopyRow(label: String, value: String, context: Context, viewModel: ShelfViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(value.ifBlank { "Unavailable" }, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            TextButton(onClick = {
                copy(context, label, value)
                viewModel.report("$label copied.")
            }) { Text("Copy") }
        }
    }
}

@Composable
private fun RuleBlock(title: String, rules: String, context: Context, viewModel: ShelfViewModel) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Text(rules, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            TextButton(onClick = {
                copy(context, title, rules)
                viewModel.report("$title copied.")
            }) { Text("Copy rules") }
        }
    }
}

private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
}
