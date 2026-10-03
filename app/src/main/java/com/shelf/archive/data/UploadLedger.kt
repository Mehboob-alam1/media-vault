package com.shelf.archive.data

import android.content.Context
import java.io.File

class UploadLedger(context: Context) {
    private val file = File(context.filesDir, "uploaded-keys.txt")
    private val keys = LinkedHashSet<String>()

    init {
        if (file.exists()) {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) keys.add(line)
                }
            }
        }
    }

    fun contains(fingerprint: String): Boolean = fingerprint in keys

    @Synchronized
    fun mark(fingerprint: String) {
        if (!keys.add(fingerprint)) return
        file.parentFile?.mkdirs()
        file.appendText(fingerprint + "\n")
    }
}
