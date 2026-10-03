package com.shelf.archive.domain

data class FirebaseConfig(
    val projectId: String = "",
    val applicationId: String = "",
    val apiKey: String = "",
    val storageBucket: String = "",
    val databaseUrl: String = "",
) {
    fun normalized(): FirebaseConfig = copy(
        projectId = projectId.trim(),
        applicationId = applicationId.trim(),
        apiKey = apiKey.trim(),
        storageBucket = storageBucket.trim(),
        databaseUrl = databaseUrl.trim().trimEnd('/'),
    )

    fun validationError(): String? {
        val config = normalized()
        if (!config.projectId.matches(Regex("^[a-z0-9-]{4,}$"))) {
            return "Project ID should look like my-project-id."
        }
        if (!config.applicationId.matches(Regex("^1:\\d+:android:[0-9a-fA-F]+$"))) {
            return "App ID should look like 1:123456789:android:abc123."
        }
        if (config.apiKey.length < 20) {
            return "API key looks too short. Copy it from google-services.json."
        }
        if (config.storageBucket.contains(' ') || !config.storageBucket.contains('.')) {
            return "Storage bucket should look like my-project.appspot.com."
        }
        val url = config.databaseUrl
        val hostOk = url.startsWith("https://") &&
            (url.contains("firebaseio.com") || url.contains("firebasedatabase.app"))
        if (!hostOk) {
            return "Database URL should be the Realtime Database address, starting with https://."
        }
        return null
    }

    companion object {
        fun databaseUrlFor(projectId: String): String =
            "https://$projectId-default-rtdb.firebaseio.com"
    }
}

data class StagedFile(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: String,
    val category: FileKind,
)

data class StoredFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val category: FileKind,
    val sizeBytes: Long,
    val storagePath: String,
    val downloadUrl: String,
    val createdAt: Long,
    val source: String,
    val device: String,
)

fun storedFileFromMap(id: String, values: Map<String, Any?>): StoredFile? {
    val name = values["name"] as? String ?: return null
    val downloadUrl = values["downloadUrl"] as? String ?: return null
    if (name.isBlank() || downloadUrl.isBlank()) return null
    return StoredFile(
        id = id,
        name = name,
        mimeType = values["mimeType"] as? String ?: "application/octet-stream",
        category = FileKind.fromWire(values["category"] as? String),
        sizeBytes = longValue(values["sizeBytes"]),
        storagePath = values["storagePath"] as? String ?: "",
        downloadUrl = downloadUrl,
        createdAt = longValue(values["createdAt"]),
        source = values["source"] as? String ?: "files",
        device = values["device"] as? String ?: "",
    )
}

private fun longValue(raw: Any?): Long = when (raw) {
    is Long -> raw
    is Int -> raw.toLong()
    is Double -> raw.toLong()
    is Float -> raw.toLong()
    is String -> raw.toLongOrNull() ?: 0L
    else -> 0L
}

enum class LibraryFilter(val label: String) {
    ALL("All"),
    IMAGES("Images"),
    WHATSAPP("WhatsApp"),
    PDF("PDFs"),
    OFFICE("Office"),
    TEXT("Text"),
}

fun LibraryFilter.accepts(file: StoredFile): Boolean = when (this) {
    LibraryFilter.ALL -> true
    LibraryFilter.IMAGES -> file.category == FileKind.IMAGE || file.category == FileKind.WHATSAPP
    LibraryFilter.WHATSAPP -> file.category == FileKind.WHATSAPP || file.source == "whatsapp"
    LibraryFilter.PDF -> file.category == FileKind.PDF
    LibraryFilter.OFFICE -> file.category == FileKind.OFFICE
    LibraryFilter.TEXT -> file.category == FileKind.TEXT
}

fun StoredFile.matchesQuery(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return name.contains(trimmed, ignoreCase = true) ||
        downloadUrl.contains(trimmed, ignoreCase = true) ||
        category.label.contains(trimmed, ignoreCase = true) ||
        mimeType.contains(trimmed, ignoreCase = true) ||
        device.contains(trimmed, ignoreCase = true)
}

fun formatBytes(size: Long): String {
    if (size < 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = size.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    if (unit == 0) return "${size} B"
    val tenths = kotlin.math.round(value * 10).toLong()
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0L) "$whole ${units[unit]}" else "$whole.$fraction ${units[unit]}"
}

fun safeStorageName(raw: String): String {
    val leaf = raw.substringAfterLast('/').substringAfterLast('\\').trim()
    val cleaned = leaf
        .replace(Regex("[^A-Za-z0-9._() -]"), "_")
        .replace(Regex("_+"), "_")
        .trim(' ', '.', '_')
    val base = cleaned.ifBlank { "file" }
    if (base.length <= 80) return base
    val ext = base.substringAfterLast('.', "")
    if (ext.isNotEmpty() && ext.length <= 8 && '.' in base) {
        val stem = base.substringBeforeLast('.')
        val keep = (80 - ext.length - 1).coerceAtLeast(1)
        return stem.take(keep) + "." + ext
    }
    return base.take(80)
}
