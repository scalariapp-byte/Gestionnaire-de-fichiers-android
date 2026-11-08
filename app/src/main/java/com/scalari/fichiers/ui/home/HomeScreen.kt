@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.home

import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.R
import com.scalari.fichiers.data.Category
import com.scalari.fichiers.data.FileItem
import com.scalari.fichiers.data.FileType
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.MediaQueries
import com.scalari.fichiers.data.StorageRoot
import com.scalari.fichiers.data.formatSize
import com.scalari.fichiers.ui.AppNavigator
import com.scalari.fichiers.ui.FileOpener
import com.scalari.fichiers.ui.LocalSnackbarHostState
import com.scalari.fichiers.ui.components.FileThumbnail
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private data class CategoryStyle(val icon: ImageVector, val color: Color)

private fun Category.style(): CategoryStyle = when (this) {
    Category.IMAGES -> CategoryStyle(Icons.Filled.PhotoLibrary, Color(0xFF2E9E5B))
    Category.VIDEOS -> CategoryStyle(Icons.Filled.VideoLibrary, Color(0xFFE5484D))
    Category.AUDIO -> CategoryStyle(Icons.Filled.LibraryMusic, Color(0xFF8E4EC6))
    Category.DOCUMENTS -> CategoryStyle(Icons.Filled.Description, Color(0xFF3E7BFA))
    Category.ARCHIVES -> CategoryStyle(Icons.Filled.FolderZip, Color(0xFFB0793F))
    Category.APKS -> CategoryStyle(Icons.Filled.Apps, Color(0xFF5BA33B))
    Category.LARGE -> CategoryStyle(Icons.Filled.Storage, Color(0xFFF07A1A))
    Category.RECENT -> CategoryStyle(Icons.Filled.History, Color(0xFF0E9AA7))
}

data class Shortcut(val label: String, val icon: ImageVector, val path: String)

fun defaultShortcuts(): List<Shortcut> {
    val base = Environment.getExternalStorageDirectory()
    return listOf(
        Shortcut("Téléchargements", Icons.Filled.Download, File(base, Environment.DIRECTORY_DOWNLOADS).absolutePath),
        Shortcut("Appareil photo", Icons.Filled.PhotoCamera, File(base, Environment.DIRECTORY_DCIM).absolutePath),
        Shortcut("Documents", Icons.Filled.Description, File(base, Environment.DIRECTORY_DOCUMENTS).absolutePath),
        Shortcut("Images", Icons.Filled.PhotoLibrary, File(base, Environment.DIRECTORY_PICTURES).absolutePath),
        Shortcut("Musique", Icons.Filled.LibraryMusic, File(base, Environment.DIRECTORY_MUSIC).absolutePath),
        Shortcut("Films", Icons.Filled.VideoLibrary, File(base, Environment.DIRECTORY_MOVIES).absolutePath),
    )
}

class HomeActions(
    val onSearch: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onOpenPath: (String) -> Unit = {},
    val onCategory: (Category) -> Unit = {},
    val onRemoveFavorite: (String) -> Unit = {},
    val onRecent: (FileItem) -> Unit = {},
    val onVault: () -> Unit = {},
    val onTrash: () -> Unit = {},
)

@Composable
fun HomeScreen(navigator: AppNavigator) {
    val vm: HomeViewModel = viewModel()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val actions = HomeActions(
        onSearch = { navigator.browser(null, search = true) },
        onSettings = navigator::settings,
        onOpenPath = { navigator.browser(it) },
        onCategory = navigator::category,
        onRemoveFavorite = { path ->
            scope.launch {
                FileManagerApp.instance.settings.removeFavorite(path)
                snackbar.showSnackbar("Retiré des favoris")
            }
        },
        onRecent = { item ->
            when {
                item.type == FileType.IMAGE -> navigator.image(item.path)
                FileTypes.isTextEditable(item) -> navigator.text(item.path)
                !FileOpener.openExternal(context, item.file) ->
                    scope.launch { snackbar.showSnackbar("Aucune application pour ouvrir ce fichier") }
            }
        },
        onVault = navigator::vault,
        onTrash = navigator::trash,
    )

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        HomeContent(
            roots = roots,
            stats = stats,
            shortcuts = remember { defaultShortcuts() },
            favorites = settings.favorites,
            recent = recent,
            actions = actions,
            contentPadding = padding,
        )
    }
}

@Composable
fun HomeContent(
    roots: List<StorageRoot>,
    stats: Map<Category, MediaQueries.Stat>,
    shortcuts: List<Shortcut>,
    favorites: List<String>,
    recent: List<FileItem>,
    actions: HomeActions,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 32.dp),
    ) {
        item(key = "header") { Header(actions) }
        item(key = "search") { SearchPill(actions.onSearch) }

        items(roots, key = { "root:" + it.path.absolutePath }) { root ->
            if (root.isPrimary) StorageHero(root) { actions.onOpenPath(root.path.absolutePath) }
            else StorageSmall(root) { actions.onOpenPath(root.path.absolutePath) }
        }

        item(key = "catTitle") { SectionTitle("Catégories") }
        item(key = "cats") {
            Column(Modifier.padding(horizontal = 12.dp)) {
                HOME_CATEGORIES.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { c ->
                            CategoryTile(c, stats[c], Modifier.weight(1f)) { actions.onCategory(c) }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        item(key = "shortTitle") { SectionTitle("Accès rapide") }
        item(key = "shorts") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shortcuts, key = { it.path }) { s -> ShortcutChip(s) { actions.onOpenPath(s.path) } }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recentTitle") {
                SectionTitle("Récents", action = "Tout voir") { actions.onCategory(Category.RECENT) }
            }
            item(key = "recent") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recent, key = { it.path }) { item -> RecentCard(item) { actions.onRecent(item) } }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            item(key = "favTitle") { SectionTitle("Favoris") }
            items(favorites, key = { "fav:$it" }) { path ->
                FavoriteRow(path, onClick = { actions.onOpenPath(path) }, onLongClick = { actions.onRemoveFavorite(path) })
            }
        }

        item(key = "toolsTitle") { SectionTitle("Outils") }
        item(key = "tools") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolCard(
                    title = "Coffre-fort",
                    subtitle = "Fichiers chiffrés",
                    icon = Icons.Filled.Shield,
                    brush = Brush.linearGradient(listOf(Color(0xFF4F8BFF), Color(0xFF5B3DF5))),
                    content = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = actions.onVault,
                )
                ToolCard(
                    title = "Corbeille",
                    subtitle = "Restaurer",
                    icon = Icons.Filled.Delete,
                    brush = Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)
                    ),
                    content = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = actions.onTrash,
                )
            }
        }
    }
}

@Composable
private fun Header(actions: HomeActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(36.dp))
        Text(
            "Fichiers",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        IconButton(onClick = actions.onSettings) { Icon(Icons.Filled.Settings, "Paramètres") }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(56.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Rechercher des fichiers",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

@Composable
private fun UsageRing(fraction: Float, track: Color, progress: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            progress, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

private fun percent(root: StorageRoot): Float = if (root.total > 0) root.used.toFloat() / root.total else 0f

@Composable
private fun StorageHero(root: StorageRoot, onClick: () -> Unit) {
    val fraction = percent(root)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(Color(0xFF3F7DFF), Color(0xFF6A4FD8))))
            .clickable(onClick = onClick)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(root.name, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(10.dp))
                Text(
                    formatSize(root.used),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                Text(
                    "utilisés sur ${formatSize(root.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(14.dp))
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                    Text(
                        "${formatSize(root.free)} libres",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Box(contentAlignment = Alignment.Center) {
                UsageRing(
                    fraction,
                    track = Color.White.copy(alpha = 0.22f),
                    progress = if (fraction > 0.9f) Color(0xFFFFB4AB) else Color.White,
                    modifier = Modifier.size(104.dp),
                )
                Text(
                    String.format(Locale.FRANCE, "%d %%", (fraction * 100).toInt()),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun StorageSmall(root: StorageRoot, onClick: () -> Unit) {
    val fraction = percent(root)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                UsageRing(
                    fraction,
                    track = MaterialTheme.colorScheme.surfaceContainerHighest,
                    progress = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Icon(
                    if (root.isRemovable) Icons.Filled.SdCard else Icons.Filled.Usb,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(root.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatSize(root.used)} / ${formatSize(root.total)} · ${formatSize(root.free)} libres",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CategoryTile(category: Category, stat: MediaQueries.Stat?, modifier: Modifier, onClick: () -> Unit) {
    val style = category.style()
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(style.color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(28.dp))
        }
        Text(
            category.label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stat?.let { if (category == Category.LARGE) formatSize(it.bytes) else String.format(Locale.FRANCE, "%,d", it.count) } ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShortcutChip(shortcut: Shortcut, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(shortcut.icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(shortcut.label, modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RecentCard(item: FileItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(112.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        FileThumbnail(item, Modifier.size(112.dp), iconSize = 40)
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            formatSize(item.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun FavoriteRow(path: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(Color(0xFFFFB300).copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Star, null, tint = Color(0xFFFFA000)) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(File(path).name.ifEmpty { path }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    brush: Brush,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(brush)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = content)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.75f))
    }
}
