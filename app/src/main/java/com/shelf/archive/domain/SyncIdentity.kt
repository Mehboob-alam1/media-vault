package com.shelf.archive.domain

fun uploadFingerprint(path: String, sizeBytes: Long, modifiedAt: Long, name: String): String {
    return "$sizeBytes:$modifiedAt:${path.trim()}:$name"
}

fun isUploadDocument(category: FileKind): Boolean {
    return category == FileKind.PDF || category == FileKind.OFFICE || category == FileKind.TEXT
}

/**
 * Lower numbers upload first.
 * WhatsApp and WhatsApp Business documents, then other documents, then images.
 */
fun uploadPriority(category: FileKind, source: String): Int {
    val fromWhatsApp = source.equals("whatsapp", ignoreCase = true) || category == FileKind.WHATSAPP
    val document = isUploadDocument(category)
    return when {
        document && fromWhatsApp -> 0
        document -> 1
        fromWhatsApp -> 2
        else -> 3
    }
}
