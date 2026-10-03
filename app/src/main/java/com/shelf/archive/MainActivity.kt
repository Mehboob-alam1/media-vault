package com.shelf.archive

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelf.archive.data.ConfigStore
import com.shelf.archive.sync.SyncService
import com.shelf.archive.ui.ShelfViewModel
import com.shelf.archive.ui.hasImagePermission
import com.shelf.archive.ui.screens.SettingsScreen
import com.shelf.archive.ui.theme.ShelfTheme

class MainActivity : ComponentActivity() {
    private val store by lazy { ConfigStore(this) }
    private var askedRuntimePermissions = false
    private var askedAllFiles = false
    private val needsSetup = mutableStateOf(false)
    private val settingsVisible = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        beginSync()
    }

    private val allFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        beginSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val missingConfig = store.read() == null
        needsSetup.value = missingConfig
        settingsVisible.value = missingConfig
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            ShelfTheme {
                if (settingsVisible.value) {
                    SetupScreen(
                        closeWhenConnected = needsSetup.value,
                        onConnected = {
                            needsSetup.value = false
                            settingsVisible.value = false
                            beginSync()
                        },
                        onClose = {
                            settingsVisible.value = store.read() == null
                        },
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = {
                                    settingsVisible.value = true
                                })
                            },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (store.read() == null) {
            settingsVisible.value = true
            return
        }
        if (!settingsVisible.value) beginSync()
    }

    private fun beginSync() {
        if (store.read() == null || settingsVisible.value) return
        val missing = missingPermissions()
        if (missing.isNotEmpty() && !askedRuntimePermissions) {
            askedRuntimePermissions = true
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            if (!askedAllFiles) {
                askedAllFiles = true
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
                allFilesLauncher.launch(intent)
                return
            }
        }
        if (!hasImagePermission(this) && !canWalkAllFiles()) return
        ContextCompat.startForegroundService(this, Intent(this, SyncService::class.java))
    }

    private fun missingPermissions(): List<String> {
        val needed = mutableListOf<String>()
        if (!hasImagePermission(this)) {
            needed += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        return needed
    }

    private fun canWalkAllFiles(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }
}

@Composable
private fun SetupScreen(
    closeWhenConnected: Boolean,
    onConnected: () -> Unit,
    onClose: () -> Unit,
) {
    val viewModel: ShelfViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.connected, closeWhenConnected) {
        if (closeWhenConnected && state.connected) onConnected()
    }
    BackHandler(onBack = onClose)
    SettingsScreen(state, viewModel)
}
