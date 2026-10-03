package com.shelf.archive.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.shelf.archive.domain.FileKind
import com.shelf.archive.domain.FileKinds
import com.shelf.archive.domain.StagedFile

data class DescribeBatch(
    val files: List<StagedFile>,
    val unreadable: Int,
)

object LocalFileReader {
    fun describeAll(context: Context, uris: List<Uri>, sourceHint: String): DescribeBatch {
        val files = mutableListOf<StagedFile>()
        var unreadable = 0
        for (uri in uris) {
            try {
                files += describe(context, uri, sourceHint)
            } catch (_: Exception) {
                unreadable++
            }
        }
        return DescribeBatch(files, unreadable)
    }

    fun describe(context: Context, uri: Uri, sourceHint: String): StagedFile {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Photo picker URIs cannot be persisted. They stay readable for this process.
        }
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        var location: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: name
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
                val relative = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                if (relative >= 0) location = cursor.getString(relative)
                if (location == null) {
                    val data = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (data >= 0) location = cursor.getString(data)
                }
            }
        }
        if (size <= 0L) {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length >= 0) size = descriptor.length
            }
        }
        val mime = context.contentResolver.getType(uri) ?: FileKinds.guessMime(name)
        val source = if (
            sourceHint == "whatsapp" ||
            location?.contains("whatsapp", ignoreCase = true) == true
        ) {
            "whatsapp"
        } else {
            sourceHint
        }
        return StagedFile(
            uri = uri.toString(),
            name = name,
            mimeType = mime,
            sizeBytes = size.coerceAtLeast(0),
            source = source,
            category = FileKinds.classify(mime, name, source),
        )
    }
}
