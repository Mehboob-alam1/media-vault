package com.shelf.archive.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shelf.archive.data.AppIdentity
import com.shelf.archive.data.ConfigStore
import com.shelf.archive.data.FirebaseVault
import com.shelf.archive.data.LocalFileReader
import com.shelf.archive.data.WhatsAppFinder
import com.shelf.archive.data.explainFirebase
import com.shelf.archive.domain.FileKind
import com.shelf.archive.domain.FirebaseConfig
import com.shelf.archive.domain.GoogleServicesParser
import com.shelf.archive.domain.LibraryFilter
import com.shelf.archive.domain.StagedFile
import com.shelf.archive.domain.StoredFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ShelfTab { LIBRARY, IMPORT, SETTINGS }

data class Notice(val id: Long, val text: String)

data class UploadStatus(
    val uri: String,
    val name: String,
    val percent: Int,
    val error: String? = null,
    val done: Boolean = false,
)

data class ShelfUiState(
    val tab: ShelfTab = ShelfTab.LIBRARY,
    val config: FirebaseConfig? = null,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val uid: String? = null,
    val files: List<StoredFile> = emptyList(),
    val libraryLoading: Boolean = false,
    val libraryError: String? = null,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val search: String = "",
    val selectedId: String? = null,
    val staged: List<StagedFile> = emptyList(),
    val discovered: List<StagedFile> = emptyList(),
    val discoveredSelected: Set<String> = emptySet(),
    val discovering: Boolean = false,
    val uploads: List<UploadStatus> = emptyList(),
    val uploading: Boolean = false,
    val notice: Notice? = null,
    val sha1: String = "",
    val packageName: String = "",
    val imported: FirebaseConfig? = null,
    val importGeneration: Int = 0,
    val importWarning: String? = null,
)

class ShelfViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConfigStore(application)
    private val vault = FirebaseVault(application)
    private val connectMutex = Mutex()
    private val _state = MutableStateFlow(
        ShelfUiState(
            sha1 = AppIdentity.sha1(application),
            packageName = application.packageName,
        ),
    )
    val state: StateFlow<ShelfUiState> = _state.asStateFlow()

    init {
        val saved = store.read()
        if (saved != null) {
            _state.update { it.copy(config = saved) }
            connect(saved)
        }
    }

    fun selectTab(tab: ShelfTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun selectFile(id: String) {
        _state.update { it.copy(selectedId = id) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedId = null) }
    }

    fun setFilter(filter: LibraryFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(search = query) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun report(text: String) {
        _state.update { it.copy(notice = Notice(System.nanoTime(), text)) }
    }

    fun saveAndConnect(config: FirebaseConfig) {
        val normalized = config.normalized()
        val error = normalized.validationError()
        if (error != null) {
            report(error)
            return
        }
        store.write(normalized)
        _state.update { it.copy(config = normalized) }
        connect(normalized)
    }

    fun disconnect() {
        viewModelScope.launch {
            vault.disconnect()
            store.clear()
            _state.update {
                it.copy(
                    config = null,
                    connected = false,
                    connecting = false,
                    uid = null,
                    files = emptyList(),
                    libraryLoading = false,
                    libraryError = null,
                    selectedId = null,
                )
            }
            report("Disconnected. Files already in Firebase stay there.")
        }
    }

    fun stage(uris: List<Uri>, sourceHint: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val batch = withContext(Dispatchers.IO) {
                LocalFileReader.describeAll(getApplication(), uris, sourceHint)
            }
            val keep = batch.files.filter { it.category != FileKind.OTHER }
            val skipped = batch.files.size - keep.size
            val notes = buildList {
                if (batch.unreadable > 0) {
                    add("Couldn't read ${batch.unreadable} file${if (batch.unreadable == 1) "" else "s"}.")
                }
                if (skipped > 0) {
                    add(
                        "Skipped $skipped unsupported file${if (skipped == 1) "" else "s"}. " +
                            "Shelf keeps images, PDFs, Office files, and text.",
                    )
                }
            }
            if (notes.isNotEmpty()) report(notes.joinToString(" "))
            if (keep.isEmpty()) return@launch
            _state.update { state ->
                val known = state.staged.map { it.uri }.toSet()
                state.copy(staged = state.staged + keep.filter { it.uri !in known })
            }
        }
    }

    fun removeStaged(uri: String) {
        if (_state.value.uploading) return
        _state.update { state -> state.copy(staged = state.staged.filterNot { it.uri == uri }) }
    }

    fun scanWhatsAppGallery() {
        viewModelScope.launch {
            _state.update { it.copy(discovering = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    WhatsAppFinder.queryGallery(getApplication())
                }
                showDiscovered(result.files, result.truncated, emptyMessage = GALLERY_EMPTY)
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                _state.update { it.copy(discovering = false) }
                report("Photo access is off, so Shelf can't scan the gallery. Choose the WhatsApp folder instead.")
            } catch (error: Exception) {
                _state.update { it.copy(discovering = false) }
                report(error.message ?: "Couldn't scan the gallery.")
            }
        }
    }

    fun scanTree(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(discovering = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    WhatsAppFinder.scanTree(getApplication(), uri)
                }
                showDiscovered(
                    result.files,
                    result.truncated,
                    emptyMessage = "That folder has no images, PDFs, Office files, or text Shelf can store.",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(discovering = false) }
                report(error.message ?: "Couldn't read that folder.")
            }
        }
    }

    fun toggleDiscovered(uri: String) {
        _state.update { state ->
            val selected = state.discoveredSelected.toMutableSet()
            if (!selected.add(uri)) selected.remove(uri)
            state.copy(discoveredSelected = selected)
        }
    }

    fun setDiscoveredSelected(all: Boolean) {
        _state.update { state ->
            state.copy(
                discoveredSelected = if (all) state.discovered.map { it.uri }.toSet() else emptySet(),
            )
        }
    }

    fun addDiscoveredToStaged() {
        _state.update { state ->
            val chosen = state.discovered.filter { it.uri in state.discoveredSelected }
            val known = state.staged.map { it.uri }.toSet()
            state.copy(
                staged = state.staged + chosen.filter { it.uri !in known },
                discovered = emptyList(),
                discoveredSelected = emptySet(),
            )
        }
    }

    fun clearDiscovered() {
        _state.update { it.copy(discovered = emptyList(), discoveredSelected = emptySet()) }
    }

    fun uploadStaged() {
        val snapshot = _state.value
        if (snapshot.uploading) return
        if (snapshot.staged.isEmpty()) return
        if (!snapshot.connected) {
            report("Connect Firebase in Settings before uploading.")
            _state.update { it.copy(tab = ShelfTab.SETTINGS) }
            return
        }
        val batch = snapshot.staged
        viewModelScope.launch {
            _state.update {
                it.copy(
                    uploading = true,
                    uploads = batch.map { file -> UploadStatus(file.uri, file.name, 0) },
                )
            }
            var succeeded = 0
            var failed = 0
            for (file in batch) {
                try {
                    vault.upload(file) { percent ->
                        _state.update { state ->
                            state.copy(
                                uploads = state.uploads.map { status ->
                                    if (status.uri == file.uri) status.copy(percent = percent) else status
                                },
                            )
                        }
                    }
                    succeeded++
                    _state.update { state ->
                        state.copy(
                            staged = state.staged.filterNot { it.uri == file.uri },
                            uploads = state.uploads.map { status ->
                                if (status.uri == file.uri) status.copy(percent = 100, done = true) else status
                            },
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failed++
                    val message = error.explainFirebase()
                    _state.update { state ->
                        state.copy(
                            uploads = state.uploads.map { status ->
                                if (status.uri == file.uri) status.copy(error = message) else status
                            },
                        )
                    }
                }
            }
            _state.update { it.copy(uploading = false) }
            val summary = buildString {
                append("Uploaded $succeeded file")
                if (succeeded != 1) append('s')
                if (failed > 0) append(". $failed failed")
                append('.')
            }
            report(summary)
        }
    }

    fun dismissUploads() {
        if (_state.value.uploading) return
        _state.update { it.copy(uploads = emptyList()) }
    }

    fun deleteSelected() {
        val file = selectedFile() ?: return
        viewModelScope.launch {
            try {
                vault.delete(file)
                report("Removed ${file.name} from Firebase.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                report(error.explainFirebase())
            }
        }
    }

    fun refreshSelectedLink() {
        val file = selectedFile() ?: return
        viewModelScope.launch {
            try {
                vault.refreshUrl(file)
                report("Download link refreshed.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                report(error.explainFirebase())
            }
        }
    }

    fun importGoogleServices(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri).use { input ->
                        input?.bufferedReader()?.readText()
                            ?: error("Couldn't read that file.")
                    }
                }
                val parsed = GoogleServicesParser.parse(json, _state.value.packageName)
                val error = parsed.config.validationError()
                if (error != null) {
                    report(error)
                    return@launch
                }
                _state.update { state ->
                    state.copy(
                        imported = parsed.config,
                        importGeneration = state.importGeneration + 1,
                        importWarning = parsed.warning,
                    )
                }
                report(parsed.warning ?: "Imported Firebase settings. Tap Connect to save them.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                report(error.message ?: "Couldn't import that file.")
            }
        }
    }

    override fun onCleared() {
        vault.cancelActiveUpload()
        vault.stopListening()
    }

    private fun connect(config: FirebaseConfig) {
        viewModelScope.launch {
            connectMutex.withLock {
                _state.update { it.copy(connecting = true, libraryError = null, libraryLoading = true) }
                try {
                    val uid = vault.connect(config)
                    vault.listen(
                        onFiles = { files ->
                            _state.update { state ->
                                state.copy(
                                    files = files,
                                    libraryLoading = false,
                                    libraryError = null,
                                    connected = true,
                                    selectedId = state.selectedId?.takeIf { id -> files.any { it.id == id } },
                                )
                            }
                        },
                        onError = { message ->
                            _state.update { it.copy(libraryLoading = false, libraryError = message) }
                            report(message)
                        },
                    )
                    _state.update { it.copy(connecting = false, connected = true, uid = uid) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _state.update {
                        it.copy(connecting = false, connected = false, libraryLoading = false)
                    }
                    report(error.explainFirebase())
                }
            }
        }
    }

    private fun showDiscovered(files: List<StagedFile>, truncated: Boolean, emptyMessage: String) {
        _state.update {
            it.copy(
                discovering = false,
                discovered = files,
                discoveredSelected = files.map { file -> file.uri }.toSet(),
            )
        }
        when {
            files.isEmpty() -> report(emptyMessage)
            truncated -> report("Showing the first ${WhatsAppFinder.LIMIT} files from that scan.")
        }
    }

    private fun selectedFile(): StoredFile? {
        val id = _state.value.selectedId ?: return null
        return _state.value.files.firstOrNull { it.id == id }
    }

    private companion object {
        const val GALLERY_EMPTY =
            "No WhatsApp images are in the gallery. Choose the WhatsApp folder. On most phones it is Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images."
    }
}
