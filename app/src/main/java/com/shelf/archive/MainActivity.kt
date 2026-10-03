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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.shelf.archive.sync.SyncService
import com.shelf.archive.ui.hasImagePermission
import com.shelf.archive.ui.theme.ShelfTheme

class MainActivity : ComponentActivity() {
    private var askedRuntimePermissions = false
    private var askedAllFiles = false

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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        beginSync()
    }

    private fun beginSync() {
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
