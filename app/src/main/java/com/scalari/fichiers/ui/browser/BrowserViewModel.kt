package com.scalari.fichiers.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.FileClipboard
import com.scalari.fichiers.data.FileItem
import com.scalari.fichiers.data.FileOps
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.StorageRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BrowserUiState(
    val currentDir: File,
    val rootDir: File,
    val rootName: String,
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val selected: Set<String> = emptySet(),
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<FileItem>? = null,
    val searching: Boolean = false,
    /** Vrai s'il y a un dossier précédent dans l'historique (le bouton Retour y revient). */
    val canGoBack: Boolean = false,
    /** Stockages disponibles (interne, carte SD, clé USB) pour changer de volume. */
    val volumes: List<com.scalari.fichiers.data.StorageRoot> = emptyList(),
) {
    val visibleItems: List<FileItem> get() = searchResults ?: items
    val selectionMode: Boolean get() = selected.isNotEmpty()
    val atRoot: Boolean get() = currentDir.absolutePath == rootDir.absolutePath
}

class BrowserViewModel(app: Application, startPath: String?, openSearch: Boolean) : AndroidViewModel(app) {

    private val settings = FileManagerApp.instance.settingsState

    private val _state: MutableStateFlow<BrowserUiState>
    val state: StateFlow<BrowserUiState>

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        val start = startPath?.let(::File)?.takeIf { it.isDirectory } ?: StorageRoots.primary()
        val root = StorageRoots.rootOf(app, start)
        _state = MutableStateFlow(
            BrowserUiState(
                currentDir = start,
                rootDir = root?.path ?: start,
                rootName = root?.name ?: start.name.ifEmpty { "/" },
                searchActive = openSearch,
            )
        )
        state = _state.asStateFlow()
        load()
        refreshVolumes()
        viewModelScope.launch {
            settings.map { listOf(it.showHidden, it.sortBy, it.ascending, it.foldersFirst) }
                .distinctUntilChanged()
                .drop(1)
                .collect { load() }
        }
        viewModelScope.launch { OperationManager.changes.collect { load() } }
    }

    fun load(dir: File = _state.value.currentDir, userRefresh: Boolean = false) {
        loadJob?.cancel()
        if (userRefresh) _state.update { it.copy(refreshing = true) }
        loadJob = viewModelScope.launch {
            val s = settings.value
            // Le tri par taille a besoin du nombre d'éléments des dossiers dès le départ.
            val needCounts = s.sortBy == com.scalari.fichiers.data.SortBy.SIZE
            val items = withContext(Dispatchers.IO) { FileOps.list(dir, s, countChildren = needCounts) }
            val readable = dir.canRead()
            _state.update { st ->
                val paths = items.mapTo(HashSet()) { it.path }
                st.copy(
                    currentDir = dir,
                    items = items,
                    loading = false,
                    refreshing = false,
                    error = if (!readable) "Ce dossier n'est pas accessible" else null,
                    selected = if (st.searchResults != null) st.selected else st.selected.filterTo(HashSet()) { it in paths },
                )
            }
            if (_state.value.searchActive && _state.value.searchQuery.isNotBlank()) startSearch(_state.value.searchQuery, 0)
            if (!needCounts && items.any { it.isDirectory }) {
                val counted = withContext(Dispatchers.IO) { FileOps.withChildCounts(items) }
                _state.update { st -> if (st.currentDir == dir && st.items === items) st.copy(items = counted) else st }
            }
        }
    }

    fun refreshVolumes() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { StorageRoots.list(getApplication<Application>()) }
            _state.update { it.copy(volumes = list) }
        }
    }

    /** Passe sur un autre stockage (carte SD, clé USB…), à sa racine. */
    fun switchVolume(root: com.scalari.fichiers.data.StorageRoot) {
        _state.update { it.copy(rootDir = root.path, rootName = root.name) }
        navigateTo(root.path)
    }

    /** Dossiers visités avant le dossier courant, comme l'historique d'un navigateur web. */
    private val history = ArrayDeque<File>()

    fun navigateTo(dir: File, addToHistory: Boolean = true) {
        searchJob?.cancel()
        val current = _state.value.currentDir
        if (addToHistory && current.absolutePath != dir.absolutePath) history.addLast(current)
        _state.update {
            it.copy(
                currentDir = dir, items = emptyList(), loading = true, error = null,
                selected = emptySet(), searchActive = false, searchQuery = "", searchResults = null, searching = false,
                canGoBack = history.isNotEmpty(),
            )
        }
        load(dir)
    }

    /** Revient au dossier précédent de l'historique ; false s'il n'y en a pas (on quitte l'écran). */
    fun goBack(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        navigateTo(previous, addToHistory = false)
        return true
    }

    // ------------------------------------------------------------ Sélection

    fun toggleSelection(item: FileItem) = _state.update {
        it.copy(selected = if (item.path in it.selected) it.selected - item.path else it.selected + item.path)
    }

    fun selectAll() = _state.update { st -> st.copy(selected = st.visibleItems.mapTo(HashSet()) { it.path }) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectedFiles(): List<File> = _state.value.selected.map(::File)
    fun selectedItems(): List<FileItem> = _state.value.visibleItems.filter { it.path in _state.value.selected }

    // ------------------------------------------------------------ Recherche

    fun setSearchActive(active: Boolean) {
        if (!active) searchJob?.cancel()
        _state.update {
            if (active) it.copy(searchActive = true, selected = emptySet())
            else it.copy(searchActive = false, searchQuery = "", searchResults = null, searching = false, selected = emptySet())
        }
    }

    fun onSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        startSearch(query, 300)
    }

    private fun startSearch(query: String, debounce: Long) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(debounce)
            _state.update { it.copy(searching = true) }
            FileOps.search(_state.value.currentDir, query, settings.value.showHidden).collect { results ->
                _state.update { it.copy(searchResults = results) }
            }
            _state.update { it.copy(searching = false) }
        }
    }

    // ------------------------------------------------------------ Actions

    private fun io(block: suspend () -> String?) {
        viewModelScope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                e.message ?: "Erreur"
            }
            msg?.let { _messages.tryEmit(it) }
            load()
        }
    }

    fun createFolder(name: String) = io {
        FileOps.createFolder(_state.value.currentDir, name)
        "Dossier « $name » créé"
    }

    fun createFile(name: String) = io {
        FileOps.createFile(_state.value.currentDir, name)
        "Fichier « $name » créé"
    }

    fun rename(item: FileItem, newName: String) = io {
        val target = FileOps.rename(item.file, newName)
        clearSelection()
        android.media.MediaScannerConnection.scanFile(
            getApplication<Application>(), arrayOf(item.path, target.absolutePath), null, null
        )
        null
    }

    fun copySelection(cut: Boolean) {
        val files = selectedFiles()
        FileClipboard.set(files, cut)
        clearSelection()
        _messages.tryEmit("${files.size} élément(s) ${if (cut) "coupé(s)" else "copié(s)"} — ouvrez la destination puis « Coller »")
    }

    fun paste() {
        val clip = FileClipboard.clip.value ?: return
        OperationManager.paste(clip.files, _state.value.currentDir, move = clip.cut)
        if (clip.cut) FileClipboard.clear()
    }

    fun delete(files: List<File>) {
        OperationManager.delete(files, settings.value.useTrash)
        clearSelection()
    }

    fun compress(files: List<File>, name: String) {
        val zipName = if (name.endsWith(".zip", ignoreCase = true)) name else "$name.zip"
        val target = FileOps.uniqueFile(_state.value.currentDir, zipName)
        OperationManager.compress(files, target)
        clearSelection()
    }

    fun extract(item: FileItem) = OperationManager.extract(item.file, _state.value.currentDir)

    fun toggleFavorite(path: String) {
        viewModelScope.launch {
            val wasFavorite = path in settings.value.favorites
            FileManagerApp.instance.settings.toggleFavorite(path)
            _messages.tryEmit(if (wasFavorite) "Retiré des favoris" else "Ajouté aux favoris")
        }
    }
}
