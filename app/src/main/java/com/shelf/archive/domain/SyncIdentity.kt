package com.shelf.archive.domain

fun uploadFingerprint(path: String, sizeBytes: Long, modifiedAt: Long, name: String): String {
    return "$sizeBytes:$modifiedAt:${path.trim()}:$name"
}
