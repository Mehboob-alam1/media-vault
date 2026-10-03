package com.shelf.archive.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

fun imageReadPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

fun hasImagePermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 34) {
        val full = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
        val selected = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        return full == PackageManager.PERMISSION_GRANTED || selected == PackageManager.PERMISSION_GRANTED
    }
    return ContextCompat.checkSelfPermission(context, imageReadPermission()) == PackageManager.PERMISSION_GRANTED
}
