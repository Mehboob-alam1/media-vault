package com.shelf.archive.data

import android.content.Context
import android.net.Uri
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import com.shelf.archive.domain.FileKinds
import com.shelf.archive.domain.FirebaseConfig
import com.shelf.archive.domain.FirebaseFailureHint
import com.shelf.archive.domain.StagedFile
import com.shelf.archive.domain.StoredFile
import com.shelf.archive.domain.explainFirebaseFailure
import com.shelf.archive.domain.safeStorageName
import com.shelf.archive.domain.storedFileFromMap
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseVault(context: Context) {
    private val appContext = context.applicationContext
    private var listener: ValueEventListener? = null
    private var filesRef: DatabaseReference? = null
    private var activeUpload: UploadTask? = null

    suspend fun connect(config: FirebaseConfig): String {
        val normalized = config.normalized()
        withContext(Dispatchers.Main.immediate) {
            val current = FirebaseApp.getApps(appContext).firstOrNull()
            val sameProject = current != null &&
                current.options.apiKey == normalized.apiKey &&
                current.options.applicationId == normalized.applicationId &&
                current.options.storageBucket == normalized.storageBucket &&
                current.options.databaseUrl?.trimEnd('/') == normalized.databaseUrl
            if (!sameProject) {
                stopListening()
                FirebaseApp.getApps(appContext).toList().forEach { it.delete() }
                FirebaseApp.initializeApp(appContext, normalized.toOptions())
                try {
                    FirebaseDatabase.getInstance().setPersistenceEnabled(true)
                } catch (error: DatabaseException) {
                    val message = error.message.orEmpty()
                    if (!message.contains("persistence", ignoreCase = true)) throw error
                }
            }
        }
        return ensureSignedIn()
    }

    fun listen(onFiles: (List<StoredFile>) -> Unit, onError: (String) -> Unit) {
        stopListening()
        val ref = FirebaseDatabase.getInstance().reference.child("library").child("files")
        val valueListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val files = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val values = child.value as? Map<String, Any?> ?: return@mapNotNull null
                    storedFileFromMap(key, values)
                }.sortedByDescending { it.createdAt }
                onFiles(files)
            }

            override fun onCancelled(error: DatabaseError) {
                val hint = if (error.code == DatabaseError.PERMISSION_DENIED) {
                    FirebaseFailureHint.UNAUTHORIZED
                } else {
                    FirebaseFailureHint.NONE
                }
                onError(explainFirebaseFailure(error.message, hint))
            }
        }
        ref.addValueEventListener(valueListener)
        listener = valueListener
        filesRef = ref
    }

    fun stopListening() {
        val current = listener
        val ref = filesRef
        if (current != null && ref != null) {
            ref.removeEventListener(current)
        }
        listener = null
        filesRef = null
    }

    suspend fun upload(staged: StagedFile, onProgress: (Int) -> Unit) {
        if (staged.sizeBytes > FileKinds.MAX_UPLOAD_BYTES) {
            error("\"${staged.name}\" is larger than 100 MB.")
        }
        val cached = withContext(Dispatchers.IO) {
            copyToCache(Uri.parse(staged.uri), staged.name)
        }
        try {
            val length = cached.length()
            if (length == 0L) error("\"${staged.name}\" is empty.")
            if (length > FileKinds.MAX_UPLOAD_BYTES) error("\"${staged.name}\" is larger than 100 MB.")
            val id = FirebaseDatabase.getInstance().reference.child("library").child("files").push().key
                ?: error("Couldn't create a database id.")
            val safeName = safeStorageName(staged.name)
            val storagePath = "library/$id/$safeName"
            val ref = FirebaseStorage.getInstance().reference.child(storagePath)
            val metadata = StorageMetadata.Builder()
                .setContentType(staged.mimeType.ifBlank { "application/octet-stream" })
                .build()
            val task = ref.putFile(Uri.fromFile(cached), metadata)
            activeUpload = task
            task.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0) {
                    onProgress(((snapshot.bytesTransferred * 100) / total).toInt().coerceIn(0, 99))
                }
            }
            task.await()
            onProgress(100)
            val url = ref.downloadUrl.await().toString()
            val record = mapOf(
                "name" to staged.name,
                "mimeType" to staged.mimeType,
                "category" to staged.category.wire,
                "sizeBytes" to length,
                "storagePath" to storagePath,
                "downloadUrl" to url,
                "createdAt" to ServerValue.TIMESTAMP,
                "source" to staged.source,
                "device" to deviceLabel(),
            )
            try {
                FirebaseDatabase.getInstance().reference
                    .child("library")
                    .child("files")
                    .child(id)
                    .setValue(record)
                    .await()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                runCatching { ref.delete().await() }
                throw error
            }
        } finally {
            activeUpload = null
            cached.delete()
        }
    }

    suspend fun delete(file: StoredFile) {
        if (file.storagePath.isNotBlank()) {
            try {
                FirebaseStorage.getInstance().reference.child(file.storagePath).delete().await()
            } catch (error: StorageException) {
                if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
            }
        }
        FirebaseDatabase.getInstance().reference
            .child("library")
            .child("files")
            .child(file.id)
            .removeValue()
            .await()
    }

    suspend fun refreshUrl(file: StoredFile): String {
        if (file.storagePath.isBlank()) error("This record has no Storage path.")
        val url = FirebaseStorage.getInstance().reference.child(file.storagePath).downloadUrl.await().toString()
        FirebaseDatabase.getInstance().reference
            .child("library")
            .child("files")
            .child(file.id)
            .child("downloadUrl")
            .setValue(url)
            .await()
        return url
    }

    suspend fun disconnect() {
        cancelActiveUpload()
        stopListening()
        withContext(Dispatchers.Main.immediate) {
            runCatching { FirebaseAuth.getInstance().signOut() }
            FirebaseApp.getApps(appContext).toList().forEach { app ->
                runCatching { app.delete() }
            }
        }
    }

    fun cancelActiveUpload() {
        activeUpload?.cancel()
        activeUpload = null
    }

    private suspend fun ensureSignedIn(): String {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.uid?.let { return it }
        val user = auth.signInAnonymously().await().user
        return user?.uid ?: error("Firebase did not return a user.")
    }

    private fun copyToCache(uri: Uri, name: String): File {
        val directory = File(appContext.cacheDir, "uploads").apply { mkdirs() }
        val destination = File(directory, "${UUID.randomUUID()}-${safeStorageName(name)}")
        appContext.contentResolver.openInputStream(uri).use { input ->
            if (input == null) error("Couldn't read $name.")
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun deviceLabel(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { char -> char.uppercaseChar() }
        val model = Build.MODEL ?: "Android"
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
    }
}

private fun FirebaseConfig.toOptions(): FirebaseOptions {
    return FirebaseOptions.Builder()
        .setApiKey(apiKey)
        .setApplicationId(applicationId)
        .setProjectId(projectId)
        .setStorageBucket(storageBucket)
        .setDatabaseUrl(databaseUrl)
        .build()
}
