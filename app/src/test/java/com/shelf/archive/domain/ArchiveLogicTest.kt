package com.shelf.archive.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveLogicTest {
    @Test
    fun classifiesGalleryImagesSeparatelyFromWhatsApp() {
        assertEquals(FileKind.IMAGE, FileKinds.classify("image/jpeg", "IMG.jpg", "gallery"))
        assertEquals(FileKind.WHATSAPP, FileKinds.classify("image/jpeg", "IMG.jpg", "whatsapp"))
        assertEquals(FileKind.WHATSAPP, FileKinds.classify("application/octet-stream", "photo.HEIC", "whatsapp"))
    }

    @Test
    fun classifiesDocumentsByMimeOrExtension() {
        assertEquals(FileKind.PDF, FileKinds.classify("application/pdf", "scan.pdf", "files"))
        assertEquals(FileKind.PDF, FileKinds.classify("application/octet-stream", "scan.PDF", "files"))
        assertEquals(
            FileKind.OFFICE,
            FileKinds.classify(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "notes.docx",
                "files",
            ),
        )
        assertEquals(FileKind.OFFICE, FileKinds.classify("application/octet-stream", "budget.xlsx", "files"))
        assertEquals(FileKind.OFFICE, FileKinds.classify("application/octet-stream", "deck.pptx", "files"))
        assertEquals(FileKind.TEXT, FileKinds.classify("text/plain", "readme.txt", "files"))
        assertEquals(FileKind.TEXT, FileKinds.classify("application/octet-stream", "notes.txt", "files"))
        assertEquals(FileKind.TEXT, FileKinds.classify("text/csv", "table.csv", "files"))
        assertEquals(FileKind.OTHER, FileKinds.classify("application/zip", "archive.zip", "files"))
        assertEquals(FileKind.PDF, FileKinds.classify("application/pdf", "wa.pdf", "whatsapp"))
    }

    @Test
    fun bundledGoogleServicesMatchesThisApp() {
        val file = File("google-services.json")
        assertTrue(file.exists())
        val parsed = GoogleServicesParser.parse(file.readText(), "com.shelf.archive")
        assertEquals("com.shelf.archive", parsed.packageName)
        assertNull(parsed.warning)
        assertNull(parsed.config.normalized().validationError())
    }

    @Test
    fun fingerprintStaysStableForTheSameFile() {
        val first = uploadFingerprint("/storage/emulated/0/Download/scan.pdf", 1200, 10, "scan.pdf")
        val again = uploadFingerprint("  /storage/emulated/0/Download/scan.pdf", 1200, 10, "scan.pdf")
        assertEquals(first, again)
        val edited = uploadFingerprint("/storage/emulated/0/Download/scan.pdf", 1400, 10, "scan.pdf")
        assertTrue(first != edited)
    }

    @Test
    fun sanitizesStorageNames() {
        assertEquals("secret.txt", safeStorageName("../secret.txt"))
        assertEquals("holiday photo.jpg", safeStorageName("holiday photo.jpg"))
        assertEquals("file", safeStorageName("***"))
        assertEquals("report.pdf", safeStorageName("C:\\docs\\report.pdf"))
        val long = "a".repeat(90) + ".docx"
        val safe = safeStorageName(long)
        assertTrue(safe.length <= 80)
        assertTrue(safe.endsWith(".docx"))
    }

    @Test
    fun formatsSizes() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("2 MB", formatBytes(2L * 1024 * 1024))
    }

    @Test
    fun filtersLibraryAndSearch() {
        val photo = sample(category = FileKind.IMAGE, source = "gallery", name = "beach.jpg")
        val whatsApp = sample(category = FileKind.WHATSAPP, source = "whatsapp", name = "chat.png")
        val pdf = sample(
            category = FileKind.PDF,
            source = "whatsapp",
            name = "invoice.pdf",
            url = "https://example.test/invoice",
        )
        assertTrue(LibraryFilter.IMAGES.accepts(photo))
        assertTrue(LibraryFilter.IMAGES.accepts(whatsApp))
        assertTrue(LibraryFilter.WHATSAPP.accepts(whatsApp))
        assertTrue(LibraryFilter.WHATSAPP.accepts(pdf))
        assertTrue(!LibraryFilter.WHATSAPP.accepts(photo))
        assertTrue(pdf.matchesQuery("invoice"))
        assertTrue(pdf.matchesQuery("example.test"))
        assertTrue(!photo.matchesQuery("invoice"))
    }

    @Test
    fun readsNumbersFirebaseMayReturnAsDoubles() {
        val file = storedFileFromMap(
            "abc",
            mapOf(
                "name" to "note.txt",
                "downloadUrl" to "https://firebasestorage.googleapis.com/note",
                "mimeType" to "text/plain",
                "category" to "text",
                "sizeBytes" to 42.0,
                "storagePath" to "library/abc/note.txt",
                "createdAt" to 1_700_000_000_000.0,
                "source" to "files",
                "device" to "Google Pixel",
            ),
        )
        requireNotNull(file)
        assertEquals(42L, file.sizeBytes)
        assertEquals(FileKind.TEXT, file.category)
        assertEquals(1_700_000_000_000L, file.createdAt)
        assertNull(storedFileFromMap("x", mapOf("name" to "missing-url.txt")))
    }

    @Test
    fun parsesGoogleServicesJson() {
        val parsed = GoogleServicesParser.parse(SAMPLE_JSON, "com.shelf.archive")
        assertEquals("shelf-demo", parsed.config.projectId)
        assertEquals("1:555:android:abc", parsed.config.applicationId)
        assertEquals("AIzaSyExampleKeyValue123456", parsed.config.apiKey)
        assertEquals("shelf-demo.appspot.com", parsed.config.storageBucket)
        assertEquals("https://shelf-demo-default-rtdb.firebaseio.com", parsed.config.databaseUrl)
        assertNull(parsed.warning)
        assertNull(parsed.config.validationError())
    }

    @Test
    fun buildsDatabaseUrlWhenTheJsonOmitsIt() {
        val json = SAMPLE_JSON.replace(
            "\"firebase_url\": \"https://shelf-demo-default-rtdb.firebaseio.com\",\n",
            "",
        )
        val parsed = GoogleServicesParser.parse(json, "com.other.app")
        assertEquals("https://shelf-demo-default-rtdb.firebaseio.com", parsed.config.databaseUrl)
        assertTrue(parsed.warning!!.contains("com.shelf.archive"))
    }

    @Test
    fun rejectsAFileThatIsNotGoogleServices() {
        val error = runCatching { GoogleServicesParser.parse("{ \"hello\": 1 }", "com.shelf.archive") }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun explainsCommonFirebaseFailures() {
        val auth = explainFirebaseFailure("The identity provider is disabled for this project. OPERATION_NOT_ALLOWED")
        assertTrue(auth.contains("Anonymous"))
        val rules = explainFirebaseFailure("Permission denied", FirebaseFailureHint.UNAUTHORIZED)
        assertTrue(rules.contains("rules"))
        val network = explainFirebaseFailure("Unable to resolve host firebaseio.com")
        assertTrue(network.contains("internet"))
    }

    @Test
    fun validatesConfig() {
        val bad = FirebaseConfig(
            projectId = "Bad_ID",
            applicationId = "nope",
            apiKey = "short",
            storageBucket = "bucket",
            databaseUrl = "http://example.com",
        )
        assertTrue(bad.validationError() != null)
    }

    private fun sample(
        category: FileKind,
        source: String,
        name: String,
        url: String = "https://firebasestorage.googleapis.com/$name",
    ) = StoredFile(
        id = name,
        name = name,
        mimeType = "application/octet-stream",
        category = category,
        sizeBytes = 10,
        storagePath = "library/$name",
        downloadUrl = url,
        createdAt = 0,
        source = source,
        device = "Pixel",
    )

    private companion object {
        val SAMPLE_JSON = """
            {
              "project_info": {
                "project_number": "555",
                "firebase_url": "https://shelf-demo-default-rtdb.firebaseio.com",
                "project_id": "shelf-demo",
                "storage_bucket": "shelf-demo.appspot.com"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:555:android:abc",
                    "android_client_info": { "package_name": "com.shelf.archive" }
                  },
                  "api_key": [{ "current_key": "AIzaSyExampleKeyValue123456" }]
                }
              ]
            }
        """.trimIndent()
    }
}
