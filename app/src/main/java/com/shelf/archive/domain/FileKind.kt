package com.shelf.archive.domain

enum class FileKind(val wire: String, val label: String) {
    IMAGE("image", "Image"),
    WHATSAPP("whatsapp", "WhatsApp"),
    PDF("pdf", "PDF"),
    OFFICE("office", "Office"),
    TEXT("text", "Text"),
    OTHER("other", "File"),
    ;

    companion object {
        fun fromWire(value: String?): FileKind =
            entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

object FileKinds {
    const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "jfif",
    )
    private val textExtensions = setOf("txt", "text", "log", "md", "csv")
    private val officeExtensions = setOf(
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf",
    )
    private val officeMimes = setOf(
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
        "application/rtf",
        "text/rtf",
    )

    fun guessMime(name: String): String {
        return when (extension(name)) {
            "jpg", "jpeg", "jfif" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "bmp" -> "image/bmp"
            "dng" -> "image/x-adobe-dng"
            "pdf" -> "application/pdf"
            "txt", "text", "log", "md" -> "text/plain"
            "csv" -> "text/csv"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "rtf" -> "application/rtf"
            else -> "application/octet-stream"
        }
    }

    fun classify(mime: String, name: String, source: String): FileKind {
        val effective = if (mime.isBlank() || mime.equals("application/octet-stream", true)) {
            guessMime(name)
        } else {
            mime
        }
        val normalized = effective.lowercase()
        val ext = extension(name)
        val image = normalized.startsWith("image/") || ext in imageExtensions
        if (image && source.equals("whatsapp", true)) return FileKind.WHATSAPP
        if (image) return FileKind.IMAGE
        if (normalized == "application/pdf" || normalized == "application/x-pdf" || ext == "pdf") {
            return FileKind.PDF
        }
        if (normalized in officeMimes || ext in officeExtensions) return FileKind.OFFICE
        if (normalized.startsWith("text/") || ext in textExtensions) return FileKind.TEXT
        return FileKind.OTHER
    }

    fun extension(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    fun extensionLabel(name: String): String {
        val ext = extension(name).uppercase()
        return if (ext.isBlank()) "FILE" else ext.take(4)
    }
}
