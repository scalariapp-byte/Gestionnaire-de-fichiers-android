@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.trash

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.TrashEntry
import com.scalari.fichiers.data.TrashManager
import com.scalari.fichiers.data.formatDate
import com.scalari.fichiers.data.formatSize
import com.scalari.fichiers.ui.LocalSnackbarHostState
import com.scalari.fichiers.ui.components.ConfirmDialog
import com.scalari.fichiers.ui.components.icon
import com.scalari.fichiers.ui.components.tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrashViewModel(app: Application) : AndroidViewModel(app) {
    private val _entries = MutableStateFlow<List<TrashEntry>?>(null)
    val entries = _entries.asStateFlow()

    init {
        load()
        viewModelScope.launch { OperationManager.changes.collect { load() } }
    }

    fun load() {
        viewModelScope.launch {
            _entries.value = withContext(Dispatchers.IO) { TrashManager.list(getApplication()) }
        }
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val vm: TrashViewModel = viewModel()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    var selected by remember { mutableStateOf<TrashEntry?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = {
                    Column {
                        Text("Corbeille")
                        entries?.let { list ->
                            Text(
                                "${list.size} élément(s) · ${formatSize(list.sumOf { it.size })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (!entries.isNullOrEmpty()) {
                        IconButton(onClick = { OperationManager.restoreFromTrash(entries.orEmpty()) }) {
                            Icon(Icons.Filled.RestoreFromTrash, "Tout restaurer")
                        }
                        IconButton(onClick = { confirmEmpty = true }) { Icon(Icons.Filled.DeleteForever, "Vider la corbeille") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = entries
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.DeleteOutline, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("La corbeille est vide", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.file.absolutePath }) { entry ->
                        val type = FileTypes.of(entry.displayName, entry.isDirectory)
                        ListItem(
                            modifier = Modifier.clickable { selected = entry },
                            leadingContent = { Icon(type.icon(), null, tint = type.tint()) },
                            headlineContent = { Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    "${formatSize(entry.size)} · supprimé le ${formatDate(entry.deletedAt)}\n${entry.originalPath}",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    selected?.let { entry ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(entry.displayName) },
            text = { Text("Emplacement d'origine :\n${entry.originalPath}") },
            confirmButton = {
                TextButton(onClick = { selected = null; OperationManager.restoreFromTrash(listOf(entry)) }) { Text("Restaurer") }
            },
            dismissButton = {
                TextButton(onClick = { selected = null; OperationManager.deleteFromTrash(listOf(entry)) }) {
                    Text("Supprimer définitivement", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = "Vider la corbeille ?",
            text = "Tous les éléments seront supprimés définitivement. Cette action est irréversible.",
            confirmLabel = "Vider",
            destructive = true,
            onDismiss = { confirmEmpty = false },
            onConfirm = { confirmEmpty = false; OperationManager.deleteFromTrash(entries.orEmpty()) },
        )
    }
}
