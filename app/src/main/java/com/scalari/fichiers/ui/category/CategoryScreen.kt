@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.category

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.Category
import com.scalari.fichiers.data.FileClipboard
import com.scalari.fichiers.data.FileItem
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.MediaQueries
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.formatSize
import com.scalari.fichiers.ui.AppNavigator
import com.scalari.fichiers.ui.FileOpener
import com.scalari.fichiers.ui.LocalSnackbarHostState
import com.scalari.fichiers.ui.components.ConfirmDialog
import com.scalari.fichiers.ui.components.FileGridItem
import com.scalari.fichiers.ui.components.FileRow
import com.scalari.fichiers.ui.components.PropertiesDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CategoryUiState(
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val selected: Set<String> = emptySet(),
)

class CategoryViewModel(app: Application, private val category: Category) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(CategoryUiState())
    val state: StateFlow<CategoryUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch { OperationManager.changes.collect { load() } }
    }

    fun load(userRefresh: Boolean = false) {
        if (userRefresh) _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val showHidden = FileManagerApp.instance.settingsState.value.showHidden
            val items = MediaQueries.query(getApplication<Application>(), category, showHidden)
            _state.update { st ->
                val paths = items.mapTo(HashSet()) { it.path }
                st.copy(items = items, loading = false, refreshing = false, selected = st.selected.filterTo(HashSet()) { it in paths })
            }
        }
    }

    fun toggle(item: FileItem) = _state.update {
        it.copy(selected = if (item.path in it.selected) it.selected - item.path else it.selected + item.path)
    }

    fun selectAll() = _state.update { st -> st.copy(selected = st.items.mapTo(HashSet()) { it.path }) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }
    fun selectedFiles(): List<File> = _state.value.selected.map(::File)
}

@Suppress("DEPRECATION")
@Composable
fun CategoryScreen(category: Category, navigator: AppNavigator) {
    val vm: CategoryViewModel = viewModel { CategoryViewModel(FileManagerApp.instance, category) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf<List<File>?>(null) }
    var properties by remember { mutableStateOf<List<File>?>(null) }
    val selectionMode = state.selected.isNotEmpty()
    val useGrid = category == Category.IMAGES || category == Category.VIDEOS

    BackHandler(enabled = selectionMode) { vm.clearSelection() }

    fun open(item: FileItem) {
        when {
            item.type == FileType.IMAGE -> navigator.image(item.path)
            FileTypes.isTextEditable(item) -> navigator.text(item.path)
            !FileOpener.openExternal(context, item.file) ->
                scope.launch { snackbar.showSnackbar("Aucune application pour ouvrir ce fichier") }
        }
    }

    fun click(item: FileItem) = if (selectionMode) vm.toggle(item) else open(item)

    fun copy(cut: Boolean) {
        val files = vm.selectedFiles()
        FileClipboard.set(files, cut)
        vm.clearSelection()
        scope.launch { snackbar.showSnackbar("${files.size} élément(s) dans le presse-papiers — ouvrez un dossier pour coller") }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Annuler") } },
                    title = { Text("${state.selected.size} sélectionné(s)") },
                    actions = {
                        IconButton(onClick = { FileOpener.share(context, vm.selectedFiles()) }) { Icon(Icons.Filled.Share, "Partager") }
                        IconButton(onClick = {
                            val files = vm.selectedFiles()
                            if (settings.confirmDelete) confirmDelete = files
                            else { OperationManager.delete(files, settings.useTrash); vm.clearSelection() }
                        }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Copier") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                onClick = { menu = false; copy(false) })
                            DropdownMenuItem(text = { Text("Couper") }, leadingIcon = { Icon(Icons.Filled.ContentCut, null) },
                                onClick = { menu = false; copy(true) })
                            if (state.selected.size == 1) {
                                DropdownMenuItem(text = { Text("Afficher dans le dossier") }, leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                                    onClick = {
                                        menu = false
                                        val parent = vm.selectedFiles().first().parent
                                        vm.clearSelection()
                                        navigator.browser(parent)
                                    })
                            }
                            DropdownMenuItem(text = { Text("Tout sélectionner") }, leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
                                onClick = { menu = false; vm.selectAll() })
                            DropdownMenuItem(text = { Text("Propriétés") }, leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { menu = false; properties = vm.selectedFiles() })
                        }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                    title = {
                        Column {
                            Text(category.label)
                            if (!state.loading) {
                                Text(
                                    "${state.items.size} fichier(s) · ${formatSize(state.items.sumOf { it.size })}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.load(userRefresh = true) },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.items.isEmpty() -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.InsertDriveFile, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Aucun fichier", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                useGrid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.path }) { item ->
                        FileGridItem(item, item.path in state.selected, onClick = { click(item) }, onLongClick = { vm.toggle(item) })
                    }
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.path }) { item ->
                        FileRow(
                            item, item.path in state.selected,
                            onClick = { click(item) },
                            onLongClick = { vm.toggle(item) },
                            supporting = "${formatSize(item.size)} · ${item.file.parentFile?.name ?: ""}",
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { files ->
        ConfirmDialog(
            title = if (settings.useTrash) "Mettre à la corbeille ?" else "Supprimer définitivement ?",
            text = "${files.size} fichier(s) " + if (settings.useTrash) "seront déplacés dans la corbeille." else "seront supprimés. Action irréversible.",
            confirmLabel = if (settings.useTrash) "Corbeille" else "Supprimer",
            destructive = !settings.useTrash,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                OperationManager.delete(files, settings.useTrash)
                vm.clearSelection()
            },
        )
    }
    properties?.let { PropertiesDialog(it, onDismiss = { properties = null }) }
}
