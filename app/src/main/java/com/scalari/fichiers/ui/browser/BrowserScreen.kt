@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.Clip
import com.scalari.fichiers.data.FileClipboard
import com.scalari.fichiers.data.FileItem
import com.scalari.fichiers.data.FileOps
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.vault.VaultManager
import com.scalari.fichiers.data.vault.VaultState
import com.scalari.fichiers.ui.AppNavigator
import com.scalari.fichiers.ui.FileOpener
import com.scalari.fichiers.ui.LocalSnackbarHostState
import com.scalari.fichiers.ui.components.ConfirmDialog
import com.scalari.fichiers.ui.components.FileGridItem
import com.scalari.fichiers.ui.components.FileRow
import com.scalari.fichiers.ui.components.NameInputDialog
import com.scalari.fichiers.ui.components.PropertiesDialog
import com.scalari.fichiers.ui.components.SortDialog
import kotlinx.coroutines.launch
import java.io.File

private sealed interface BrowserDialog {
    data object NewFolder : BrowserDialog
    data object NewFile : BrowserDialog
    data object Sort : BrowserDialog
    data class Rename(val item: FileItem) : BrowserDialog
    data class Delete(val files: List<File>) : BrowserDialog
    data class Properties(val files: List<File>) : BrowserDialog
    data class Compress(val files: List<File>) : BrowserDialog
    data class Archive(val item: FileItem) : BrowserDialog
    data class Vault(val files: List<File>) : BrowserDialog
}

@Suppress("DEPRECATION")
@Composable
fun BrowserScreen(startPath: String?, openSearch: Boolean, navigator: AppNavigator) {
    val vm: BrowserViewModel = viewModel { BrowserViewModel(FileManagerApp.instance, startPath, openSearch) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    val clip by FileClipboard.clip.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = FileManagerApp.instance.settings

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }

    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }

    // Position de défilement mémorisée par dossier (restaurée en remontant).
    val positions = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    val dirKey = state.currentDir.absolutePath
    val listState = remember(dirKey) { positions[dirKey].let { LazyListState(it?.first ?: 0, it?.second ?: 0) } }
    val gridState = remember(dirKey) { positions[dirKey].let { LazyGridState(it?.first ?: 0, it?.second ?: 0) } }
    fun savePosition() {
        positions[dirKey] = if (settings.gridView) gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        else listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
    fun goTo(dir: File) {
        savePosition()
        // En descendant dans un sous-dossier, repartir du haut.
        if (FileOps.isInside(dir, state.currentDir)) positions.remove(dir.absolutePath)
        vm.navigateTo(dir)
    }
    fun goUp() {
        savePosition()
        vm.navigateUp()
    }

    BackHandler(enabled = state.selectionMode || state.searchActive || !state.atRoot) {
        when {
            state.selectionMode -> vm.clearSelection()
            state.searchActive -> vm.setSearchActive(false)
            else -> goUp()
        }
    }

    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }

    fun open(item: FileItem) {
        when {
            item.isDirectory -> goTo(item.file)
            item.type == FileType.IMAGE -> navigator.image(item.path)
            FileTypes.isTextEditable(item) -> navigator.text(item.path)
            item.extension == "zip" -> dialog = BrowserDialog.Archive(item)
            !FileOpener.openExternal(context, item.file) -> message("Aucune application pour ouvrir ce fichier")
        }
    }

    fun onItemClick(item: FileItem) = if (state.selectionMode) vm.toggleSelection(item) else open(item)
    fun onItemLongClick(item: FileItem) = vm.toggleSelection(item)

    fun requestDelete(files: List<File>) {
        if (settings.confirmDelete) dialog = BrowserDialog.Delete(files) else vm.delete(files)
    }

    Scaffold(
        topBar = {
            when {
                state.selectionMode -> SelectionTopBar(
                    count = state.selected.size,
                    single = vm.selectedItems().singleOrNull(),
                    isFavorite = { it in settings.favorites },
                    onClose = vm::clearSelection,
                    onCopy = { vm.copySelection(cut = false) },
                    onCut = { vm.copySelection(cut = true) },
                    onDelete = { requestDelete(vm.selectedFiles()) },
                    onShare = { FileOpener.share(context, vm.selectedFiles()) },
                    onRename = { item -> dialog = BrowserDialog.Rename(item) },
                    onCompress = { dialog = BrowserDialog.Compress(vm.selectedFiles()) },
                    onProperties = { dialog = BrowserDialog.Properties(vm.selectedFiles()) },
                    onSelectAll = vm::selectAll,
                    onOpenWith = { item ->
                        if (!FileOpener.openExternal(context, item.file, forceChooser = true, mime = "*/*")) message("Aucune application disponible")
                    },
                    onToggleFavorite = { item -> vm.toggleFavorite(item.path); vm.clearSelection() },
                    onVault = { dialog = BrowserDialog.Vault(vm.selectedFiles()) },
                )

                state.searchActive -> SearchTopBar(
                    query = state.searchQuery,
                    folderName = if (state.atRoot) state.rootName else state.currentDir.name,
                    onQuery = vm::onSearchQuery,
                    onClose = { vm.setSearchActive(false) },
                )

                else -> TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (state.atRoot) state.rootName else state.currentDir.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${state.items.size} élément${if (state.items.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = navigator::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                    },
                    actions = {
                        IconButton(onClick = { vm.setSearchActive(true) }) { Icon(Icons.Filled.Search, "Rechercher") }
                        IconButton(onClick = { scope.launch { repo.setGridView(!settings.gridView) } }) {
                            Icon(if (settings.gridView) Icons.Filled.ViewList else Icons.Filled.GridView, "Changer d'affichage")
                        }
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val isFav = state.currentDir.absolutePath in settings.favorites
                            MenuItem("Nouveau dossier", Icons.Filled.CreateNewFolder) { menu = false; dialog = BrowserDialog.NewFolder }
                            MenuItem("Nouveau fichier", Icons.Filled.NoteAdd) { menu = false; dialog = BrowserDialog.NewFile }
                            MenuItem("Trier", Icons.Filled.Sort) { menu = false; dialog = BrowserDialog.Sort }
                            MenuItem(
                                if (settings.showHidden) "Masquer les fichiers cachés" else "Afficher les fichiers cachés",
                                if (settings.showHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            ) { menu = false; scope.launch { repo.setShowHidden(!settings.showHidden) } }
                            MenuItem(
                                if (isFav) "Retirer des favoris" else "Ajouter aux favoris",
                                if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                            ) { menu = false; vm.toggleFavorite(state.currentDir.absolutePath) }
                            MenuItem("Tout sélectionner", Icons.Filled.SelectAll) { menu = false; vm.selectAll() }
                            MenuItem("Propriétés du dossier", Icons.Filled.Info) {
                                menu = false; dialog = BrowserDialog.Properties(listOf(state.currentDir))
                            }
                            MenuItem("Paramètres", Icons.Filled.Settings) { menu = false; navigator.settings() }
                        }
                    },
                )
            }
        },
        bottomBar = {
            val c = clip
            if (c != null && !state.selectionMode) {
                PasteBar(c, onPaste = vm::paste, onCancel = FileClipboard::clear)
            }
        },
        floatingActionButton = {
            if (!state.selectionMode && !state.searchActive && clip == null) {
                FloatingActionButton(onClick = { dialog = BrowserDialog.NewFolder }) {
                    Icon(Icons.Filled.CreateNewFolder, "Nouveau dossier")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!state.searchActive) {
                Breadcrumbs(state.rootDir, state.rootName, state.currentDir, onClick = ::goTo)
            }
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.load(userRefresh = true) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val visible = state.visibleItems
                val showParentInSubtitle = state.searchResults != null
                when {
                    state.loading && visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    visible.isEmpty() -> EmptyState(
                        when {
                            state.searchActive && state.searchQuery.isBlank() -> "Tapez un nom pour rechercher dans ce dossier et ses sous-dossiers"
                            state.searchActive && state.searching -> "Recherche…"
                            state.searchActive -> "Aucun résultat"
                            state.error != null -> state.error!!
                            else -> "Ce dossier est vide"
                        },
                        searching = state.searchActive,
                    )

                    settings.gridView -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(108.dp),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 88.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.path }, contentType = { if (it.isDirectory) 0 else 1 }) { item ->
                            FileGridItem(
                                modifier = Modifier.animateItem(),
                                item = item,
                                selected = item.path in state.selected,
                                onClick = { onItemClick(item) },
                                onLongClick = { onItemLongClick(item) },
                            )
                        }
                    }

                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 88.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.path }, contentType = { if (it.isDirectory) 0 else 1 }) { item ->
                            if (showParentInSubtitle) {
                                FileRow(
                                    modifier = Modifier.animateItem(),
                                    item = item,
                                    selected = item.path in state.selected,
                                    onClick = { onItemClick(item) },
                                    onLongClick = { onItemLongClick(item) },
                                    supporting = item.file.parentFile?.toRelativeString(state.currentDir)?.ifEmpty { "." } ?: "",
                                )
                            } else {
                                FileRow(
                                    modifier = Modifier.animateItem(),
                                    item = item,
                                    selected = item.path in state.selected,
                                    onClick = { onItemClick(item) },
                                    onLongClick = { onItemLongClick(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ Dialogues
    when (val d = dialog) {
        null -> Unit
        BrowserDialog.NewFolder -> NameInputDialog(
            title = "Nouveau dossier", confirmLabel = "Créer", selectBaseName = false,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.createFolder(it) },
        )

        BrowserDialog.NewFile -> NameInputDialog(
            title = "Nouveau fichier", initial = "nouveau.txt", confirmLabel = "Créer",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.createFile(it) },
        )

        BrowserDialog.Sort -> SortDialog(
            sortBy = settings.sortBy, ascending = settings.ascending, foldersFirst = settings.foldersFirst,
            onDismiss = { dialog = null },
            onApply = { by, asc, folders ->
                dialog = null
                scope.launch {
                    repo.setSort(by, asc)
                    repo.setFoldersFirst(folders)
                }
            },
        )

        is BrowserDialog.Rename -> NameInputDialog(
            title = "Renommer", initial = d.item.name, confirmLabel = "Renommer", selectBaseName = !d.item.isDirectory,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.rename(d.item, it) },
        )

        is BrowserDialog.Delete -> {
            val toTrash = settings.useTrash
            val label = d.files.singleOrNull()?.name?.let { "« $it »" } ?: "${d.files.size} éléments"
            ConfirmDialog(
                title = if (toTrash) "Mettre à la corbeille ?" else "Supprimer définitivement ?",
                text = if (toTrash) "$label sera déplacé dans la corbeille. Vous pourrez le restaurer."
                else "$label sera supprimé définitivement. Cette action est irréversible.",
                confirmLabel = if (toTrash) "Corbeille" else "Supprimer",
                destructive = !toTrash,
                onDismiss = { dialog = null },
                onConfirm = { dialog = null; vm.delete(d.files) },
            )
        }

        is BrowserDialog.Properties -> PropertiesDialog(d.files, onDismiss = { dialog = null })

        is BrowserDialog.Compress -> NameInputDialog(
            title = "Compresser en ZIP",
            initial = (d.files.singleOrNull()?.nameWithoutExtension ?: state.currentDir.name.ifEmpty { "archive" }) + ".zip",
            confirmLabel = "Compresser",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.compress(d.files, it) },
        )

        is BrowserDialog.Vault -> ConfirmDialog(
            title = "Mettre au coffre-fort ?",
            text = (d.files.singleOrNull()?.name?.let { "« $it »" } ?: "${d.files.size} éléments") +
                " sera chiffré puis retiré de cet emplacement. Il ne sera visible que dans le coffre, après saisie du code.",
            confirmLabel = "Chiffrer",
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                vm.clearSelection()
                if (VaultManager.state.value is VaultState.Unlocked) OperationManager.vaultAdd(d.files)
                else {
                    VaultManager.setPending(d.files)
                    navigator.vault()
                }
            },
        )

        is BrowserDialog.Archive -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Filled.Archive, null) },
            title = { Text(d.item.name) },
            text = { Text("Que voulez-vous faire de cette archive ?") },
            confirmButton = {
                TextButton(onClick = { dialog = null; vm.extract(d.item) }) { Text("Extraire ici") }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialog = null
                    if (!FileOpener.openExternal(context, d.item.file)) message("Aucune application pour ouvrir ce fichier")
                }) { Text("Ouvrir avec…") }
            },
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Suppress("DEPRECATION")
@Composable
private fun SelectionTopBar(
    count: Int,
    single: FileItem?,
    isFavorite: (String) -> Boolean,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onRename: (FileItem) -> Unit,
    onCompress: () -> Unit,
    onProperties: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenWith: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onVault: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Annuler la sélection") } },
        title = { Text("$count sélectionné${if (count > 1) "s" else ""}") },
        actions = {
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Copier") }
            IconButton(onClick = onCut) { Icon(Icons.Filled.ContentCut, "Couper") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Supprimer") }
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem("Partager", Icons.Filled.Share) { menu = false; onShare() }
                if (single != null) {
                    MenuItem("Renommer", Icons.Filled.Edit) { menu = false; onRename(single) }
                    if (!single.isDirectory) {
                        MenuItem("Ouvrir avec…", Icons.Filled.OpenInNew) { menu = false; onOpenWith(single) }
                    } else {
                        val fav = isFavorite(single.path)
                        MenuItem(
                            if (fav) "Retirer des favoris" else "Ajouter aux favoris",
                            if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                        ) { menu = false; onToggleFavorite(single) }
                    }
                }
                MenuItem("Mettre au coffre-fort", Icons.Filled.Lock) { menu = false; onVault() }
                MenuItem("Compresser (ZIP)", Icons.Filled.Archive) { menu = false; onCompress() }
                if (single?.extension == "zip") {
                    MenuItem("Ouvrir l'archive", Icons.Filled.Unarchive) { menu = false; onOpenWith(single) }
                }
                MenuItem("Tout sélectionner", Icons.Filled.SelectAll) { menu = false; onSelectAll() }
                MenuItem("Propriétés", Icons.Filled.Info) { menu = false; onProperties() }
            }
        },
    )
}

@Composable
private fun SearchTopBar(query: String, folderName: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer la recherche") } },
        title = {
            TextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("Rechercher dans $folderName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        actions = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Close, "Effacer") }
        },
    )
}

@Suppress("DEPRECATION")
@Composable
private fun Breadcrumbs(root: File, rootName: String, current: File, onClick: (File) -> Unit) {
    val segments = remember(root, current) {
        val list = ArrayList<File>()
        var f: File? = current
        while (f != null) {
            list += f
            if (f.absolutePath == root.absolutePath) break
            f = f.parentFile
        }
        list.reversed()
    }
    val rowState = rememberLazyListState()
    LaunchedEffect(segments.size) { if (segments.isNotEmpty()) rowState.scrollToItem(segments.lastIndex) }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(segments, key = { _, f -> f.absolutePath }) { index, f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Icon(
                        Icons.Filled.ChevronRight, null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val last = index == segments.lastIndex
                Text(
                    if (index == 0) rootName else f.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                    color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(enabled = !last) { onClick(f) }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PasteBar(clip: Clip, onPaste: () -> Unit, onCancel: () -> Unit) {
    BottomAppBar {
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp)
        ) {
            Text(
                "${clip.files.size} élément${if (clip.files.size > 1) "s" else ""} ${if (clip.cut) "à déplacer" else "à copier"}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                clip.files.first().name + if (clip.files.size > 1) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) { Text("Annuler") }
        Button(onClick = onPaste, modifier = Modifier.padding(end = 8.dp)) {
            Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
            Text(if (clip.cut) " Déplacer ici" else " Coller ici")
        }
    }
}

@Composable
private fun EmptyState(text: String, searching: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (searching) Icons.Filled.SearchOff else Icons.Filled.FolderOff,
            null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}
