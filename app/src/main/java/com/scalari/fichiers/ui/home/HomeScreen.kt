@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.scalari.fichiers.ui.home

import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.Category
import com.scalari.fichiers.data.FileItem
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.StorageRoot
import com.scalari.fichiers.data.formatSize
import com.scalari.fichiers.ui.AppNavigator
import com.scalari.fichiers.ui.FileOpener
import com.scalari.fichiers.ui.LocalSnackbarHostState
import com.scalari.fichiers.ui.components.FileThumbnail
import kotlinx.coroutines.launch
import java.io.File

private data class CategoryEntry(val category: Category, val icon: ImageVector, val color: Color)

private val categories = listOf(
    CategoryEntry(Category.IMAGES, Icons.Filled.PhotoLibrary, Color(0xFF43A047)),
    CategoryEntry(Category.VIDEOS, Icons.Filled.VideoLibrary, Color(0xFFE53935)),
    CategoryEntry(Category.AUDIO, Icons.Filled.LibraryMusic, Color(0xFF8E24AA)),
    CategoryEntry(Category.DOCUMENTS, Icons.Filled.Description, Color(0xFF1E88E5)),
    CategoryEntry(Category.ARCHIVES, Icons.Filled.FolderZip, Color(0xFF8D6E63)),
    CategoryEntry(Category.APKS, Icons.Filled.Apps, Color(0xFF7CB342)),
    CategoryEntry(Category.LARGE, Icons.Filled.Storage, Color(0xFFEF6C00)),
)

@Composable
fun HomeScreen(navigator: AppNavigator) {
    val vm: HomeViewModel = viewModel()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    fun openRecent(item: FileItem) {
        when {
            item.type == FileType.IMAGE -> navigator.image(item.path)
            FileTypes.isTextEditable(item) -> navigator.text(item.path)
            !FileOpener.openExternal(context, item.file) ->
                scope.launch { snackbar.showSnackbar("Aucune application pour ouvrir ce fichier") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fichiers") },
                actions = {
                    IconButton(onClick = { navigator.browser(null, search = true) }) {
                        Icon(Icons.Filled.Search, "Rechercher")
                    }
                    IconButton(onClick = navigator::settings) { Icon(Icons.Filled.Settings, "Paramètres") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionTitle("Stockage") }
            items(roots, key = { it.path.absolutePath }) { root ->
                StorageCard(root) { navigator.browser(root.path.absolutePath) }
            }

            item { SectionTitle("Catégories") }
            item {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    categories.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { entry ->
                                CategoryButton(entry, Modifier.weight(1f)) { navigator.category(entry.category) }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            item { SectionTitle("Raccourcis") }
            item {
                val base = Environment.getExternalStorageDirectory()
                val shortcuts = listOf(
                    Triple("Téléchargements", Icons.Filled.Download, Environment.DIRECTORY_DOWNLOADS),
                    Triple("Appareil photo", Icons.Filled.PhotoCamera, Environment.DIRECTORY_DCIM),
                    Triple("Documents", Icons.Filled.Description, Environment.DIRECTORY_DOCUMENTS),
                    Triple("Images", Icons.Filled.PhotoLibrary, Environment.DIRECTORY_PICTURES),
                    Triple("Musique", Icons.Filled.LibraryMusic, Environment.DIRECTORY_MUSIC),
                    Triple("Films", Icons.Filled.VideoLibrary, Environment.DIRECTORY_MOVIES),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shortcuts) { (label, icon, dir) ->
                        ShortcutChip(label, icon) { navigator.browser(File(base, dir).absolutePath) }
                    }
                }
            }

            if (settings.favorites.isNotEmpty()) {
                item { SectionTitle("Favoris") }
                items(settings.favorites, key = { "fav:$it" }) { path ->
                    val f = File(path)
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = { navigator.browser(path) },
                            onLongClick = {
                                scope.launch {
                                    FileManagerApp.instance.settings.removeFavorite(path)
                                    snackbar.showSnackbar("Retiré des favoris")
                                }
                            },
                        ),
                        leadingContent = { Icon(Icons.Filled.Star, null, tint = Color(0xFFFFB300)) },
                        headlineContent = { Text(f.name.ifEmpty { path }) },
                        supportingContent = { Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            if (recent.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Fichiers récents", Modifier.weight(1f))
                        Text(
                            "Tout voir",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clickable { navigator.category(Category.RECENT) }
                                .padding(16.dp),
                        )
                    }
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recent, key = { it.path }) { item ->
                            Column(
                                Modifier
                                    .width(96.dp)
                                    .clickable { openRecent(item) },
                            ) {
                                FileThumbnail(item, Modifier.size(96.dp), iconSize = 36)
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Outils") }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = navigator::trash),
                    leadingContent = { Icon(Icons.Filled.Delete, null) },
                    headlineContent = { Text("Corbeille") },
                    supportingContent = { Text("Restaurer ou supprimer définitivement") },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    )
}

@Composable
private fun StorageCard(root: StorageRoot, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (root.isPrimary) Icons.Filled.Smartphone else Icons.Filled.SdCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Column(
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(root.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                val fraction = if (root.total > 0) root.used.toFloat() / root.total else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (fraction > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${formatSize(root.used)} utilisés sur ${formatSize(root.total)} · ${formatSize(root.free)} libres",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryButton(entry: CategoryEntry, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = entry.color.copy(alpha = 0.15f),
                contentColor = entry.color,
            ),
        ) {
            Icon(entry.icon, contentDescription = null)
        }
        Text(
            entry.category.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ShortcutChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

