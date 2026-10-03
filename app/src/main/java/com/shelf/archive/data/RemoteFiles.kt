package com.shelf.archive.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.shelf.archive.domain.safeStorageName
import java.io.File
import java.net.URL

object RemoteFiles {
    fun destination(context: Context, id: String, url: String, name: String): File {
        val directory = File(context.cacheDir, "previews").apply { mkdirs() }
        val hash = url.hashCode().toUInt().toString(16)
        return File(directory, "$id-$hash-${safeStorageName(name)}")
    }

    fun download(url: String, destination: File, maxBytes: Long) {
        if (destination.exists() && destination.length() > 0L) return
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".part")
        val connection = URL(url).openConnection().apply {
            connectTimeout = 20_000
            readTimeout = 60_000
        }
        connection.getInputStream().use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        partial.delete()
                        error("This file is too large to preview here. Open the download link instead.")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
    }

    fun open(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(view, "Open with"))
        } catch (_: ActivityNotFoundException) {
            error("No app on this phone can open this file. The download link still works.")
        }
    }
}
