package com.shelf.archive.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.shelf.archive.domain.FileKind
import com.shelf.archive.domain.FileKinds
import com.shelf.archive.domain.StagedFile

data class ScanBatch(
    val files: List<StagedFile>,
    val truncated: Boolean,
)

object WhatsAppLocations {
    val images: Uri = document(
        "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
    )
    val businessImages: Uri = document(
        "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images",
    )

    private fun document(path: String): Uri =
        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", path)
}

object WhatsAppFinder {
    const val LIMIT = 400

    fun queryGallery(context: Context): ScanBatch {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            MediaStore.Images.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            pathColumn,
        )
        val selection = "$pathColumn LIKE ? OR $pathColumn LIKE ?"
        val args = arrayOf("%WhatsApp%", "%whatsapp%")
        val files = mutableListOf<StagedFile>()
        var truncated = false
        context.contentResolver.query(
            collection,
            projection,
            selection,
            args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                if (files.size >= LIMIT) {
                    truncated = true
                    break
                }
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: "image"
                val mime = cursor.getString(mimeIndex) ?: FileKinds.guessMime(name)
                val size = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                files += StagedFile(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    name = name,
                    mimeType = mime,
                    sizeBytes = size.coerceAtLeast(0),
                    source = "whatsapp",
                    category = FileKinds.classify(mime, name, "whatsapp"),
                )
            }
        }
        return ScanBatch(files, truncated)
    }

    fun scanTree(context: Context, tree: Uri): ScanBatch {
        try {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // The picker still grants temporary access for this result.
        }
        val root = DocumentFile.fromTreeUri(context, tree)
            ?: error("That folder couldn't be opened.")
        val files = mutableListOf<StagedFile>()
        walk(root, files, 0)
        return ScanBatch(files, files.size >= LIMIT)
    }

    private fun walk(directory: DocumentFile, out: MutableList<StagedFile>, depth: Int) {
        if (depth > 12 || out.size >= LIMIT) return
        val children = try {
            directory.listFiles()
        } catch (_: Exception) {
            return
        }
        for (child in children) {
            if (out.size >= LIMIT) return
            if (child.isDirectory) {
                walk(child, out, depth + 1)
                continue
            }
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val mime = child.type ?: FileKinds.guessMime(name)
            val category = FileKinds.classify(mime, name, "whatsapp")
            if (category == FileKind.OTHER) continue
            out += StagedFile(
                uri = child.uri.toString(),
                name = name,
                mimeType = mime,
                sizeBytes = child.length().coerceAtLeast(0),
                source = "whatsapp",
                category = category,
            )
        }
    }
}
