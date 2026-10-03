package com.shelf.archive.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.shelf.archive.domain.FileKind
import com.shelf.archive.domain.FileKinds
import com.shelf.archive.domain.StagedFile
import com.shelf.archive.domain.uploadFingerprint
import java.io.File

data class DiscoveredFile(
    val staged: StagedFile,
    val fingerprint: String,
)

object DeviceScanner {
    private const val MAX_FILES = 20_000
    private const val MAX_DEPTH = 14

    fun scan(context: Context): List<DiscoveredFile> {
        val found = LinkedHashMap<String, DiscoveredFile>()
        if (canWalkStorage(context)) {
            for (root in storageRoots(context)) {
                walk(root, found, 0)
            }
        } else {
            if (canReadImages(context)) {
                scanImages(context, found)
            }
            scanIndexedDocuments(context, found)
        }
        return found.values.toList()
    }

    private fun scanImages(context: Context, found: MutableMap<String, DiscoveredFile>) {
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
            MediaStore.Images.Media.DATE_MODIFIED,
            pathColumn,
        )
        query(context, collection, projection, null, null) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "image"
            val size = longOrZero(cursor, MediaStore.Images.Media.SIZE)
            val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                ?: FileKinds.guessMime(name)
            val modified = longOrZero(cursor, MediaStore.Images.Media.DATE_MODIFIED)
            val location = cursor.getString(cursor.getColumnIndexOrThrow(pathColumn))
            val path = storagePath(location, preferRelative = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            add(
                found = found,
                uri = ContentUris.withAppendedId(collection, id),
                name = name,
                mime = mime,
                size = size,
                modified = modified,
                path = path.ifBlank { location.orEmpty() },
            )
        }
    }

    private fun scanIndexedDocuments(context: Context, found: MutableMap<String, DiscoveredFile>) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA,
        )
        query(context, collection, projection, null, null) { cursor ->
            val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                ?: return@query
            val mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))
                ?: FileKinds.guessMime(name)
            val kind = FileKinds.classify(mime, name, "device")
            if (kind != FileKind.PDF && kind != FileKind.OFFICE && kind != FileKind.TEXT) return@query
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
            val size = longOrZero(cursor, MediaStore.Files.FileColumns.SIZE)
            val modified = longOrZero(cursor, MediaStore.Files.FileColumns.DATE_MODIFIED)
            val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
            add(
                found = found,
                uri = ContentUris.withAppendedId(collection, id),
                name = name,
                mime = mime,
                size = size,
                modified = modified,
                path = data ?: name,
            )
        }
    }

    private fun walk(directory: File, found: MutableMap<String, DiscoveredFile>, depth: Int) {
        if (depth > MAX_DEPTH || found.size >= MAX_FILES) return
        if (shouldSkipDirectory(directory)) return
        val children = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return
        for (child in children) {
            if (found.size >= MAX_FILES) return
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                walk(child, found, depth + 1)
            } else {
                val name = child.name
                val mime = FileKinds.guessMime(name)
                val length = child.length()
                add(
                    found = found,
                    uri = Uri.fromFile(child),
                    name = name,
                    mime = mime,
                    size = length,
                    modified = child.lastModified(),
                    path = child.absolutePath,
                )
            }
        }
    }

    private fun add(
        found: MutableMap<String, DiscoveredFile>,
        uri: Uri,
        name: String,
        mime: String,
        size: Long,
        modified: Long,
        path: String,
    ) {
        if (found.size >= MAX_FILES) return
        if (size <= 0L || size > FileKinds.MAX_UPLOAD_BYTES) return
        val source = if (path.contains("whatsapp", ignoreCase = true)) "whatsapp" else "device"
        val category = FileKinds.classify(mime, name, source)
        if (category == FileKind.OTHER) return
        val fingerprint = uploadFingerprint(path.ifBlank { uri.toString() }, size, modified, name)
        if (fingerprint in found) return
        found[fingerprint] = DiscoveredFile(
            staged = StagedFile(
                uri = uri.toString(),
                name = name,
                mimeType = mime,
                sizeBytes = size,
                source = source,
                category = category,
            ),
            fingerprint = fingerprint,
        )
    }

    private fun query(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        read: (android.database.Cursor) -> Unit,
    ) {
        try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) read(cursor)
            }
        } catch (_: SecurityException) {
            // Permission not granted for this collection.
        } catch (_: IllegalArgumentException) {
            // This Android build does not expose one of the columns.
        }
    }

    private fun longOrZero(cursor: android.database.Cursor, column: String): Long {
        val index = cursor.getColumnIndex(column)
        if (index < 0 || cursor.isNull(index)) return 0L
        return cursor.getLong(index)
    }

    private fun storagePath(location: String?, preferRelative: Boolean): String {
        if (location.isNullOrBlank()) return ""
        if (!preferRelative || location.startsWith("/")) return location
        return "/storage/emulated/0/${location.trimStart('/')}"
    }

    private fun shouldSkipDirectory(directory: File): Boolean {
        val path = directory.absolutePath
        return path.contains("/Android/data") || path.contains("/Android/obb")
    }

    private fun storageRoots(context: Context): List<File> {
        val roots = LinkedHashSet<File>()
        Environment.getExternalStorageDirectory()?.let { directory ->
            if (directory.exists()) roots.add(directory)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(StorageManager::class.java)
            manager?.storageVolumes?.forEach { volume ->
                volume.directory?.let { directory ->
                    if (directory.exists()) roots.add(directory)
                }
            }
        }
        return roots.toList()
    }

    fun canReadImages(context: Context): Boolean = com.shelf.archive.ui.hasImagePermission(context)

    fun canWalkStorage(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            canReadImages(context)
        }
    }
}
