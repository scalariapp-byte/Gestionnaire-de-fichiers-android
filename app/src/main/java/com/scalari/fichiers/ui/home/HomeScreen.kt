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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

data class Shortcut(val label: String, val icon: ImageVector, val path: String, val color: Color = Color(0xFF3E7BFA))

fun defaultShortcuts(): List<Shortcut> {
    val base = Environment.getExternalStorageDirectory()
    return listOf(
        Shortcut("Téléchargements", Icons.Filled.Download, File(base, Environment.DIRECTORY_DOWNLOADS).absolutePath, Color(0xFF3E7BFA)),
        Shortcut("Appareil photo", Icons.Filled.PhotoCamera, File(base, Environment.DIRECTORY_DCIM).absolutePath, Color(0xFF0E9AA7)),
        Shortcut("Documents", Icons.Filled.Description, File(base, Environment.DIRECTORY_DOCUMENTS).absolutePath, Color(0xFF6A4FD8)),
        Shortcut("Images", Icons.Filled.PhotoLibrary, File(base, Environment.DIRECTORY_PICTURES).absolutePath, Color(0xFF2E9E5B)),
        Shortcut("Musique", Icons.Filled.LibraryMusic, File(base, Environment.DIRECTORY_MUSIC).absolutePath, Color(0xFF8E4EC6)),
        Shortcut("Films", Icons.Filled.VideoLibrary, File(base, Environment.DIRECTORY_MOVIES).absolutePath, Color(0xFFE5484D)),
    )
}

class HomeActions(
    val onSearch: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onOpenPath: (String) -> Unit = {},
    val onCategory: (Category) -> Unit = {},
    val onRemoveFavorite: (String) -> Unit = {},
    val onVault: () -> Unit = {},
    val onTrash: () -> Unit = {},
)

@Composable
fun HomeScreen(navigator: AppNavigator) {
    val vm: HomeViewModel = viewModel()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
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
        onVault = navigator::vault,
        onTrash = navigator::trash,
    )

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        HomeContent(
            roots = roots,
            stats = stats,
            shortcuts = remember { defaultShortcuts() },
            favorites = settings.favorites,
            counts = counts,
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
    counts: Map<String, Int>,
    actions: HomeActions,
    contentPadding: PaddingValues = PaddingValues(),
) {
    // Conçu pour tenir sur un écran ; le défilement ne sert que sur les très petits écrans.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp),
    ) {
        Header(actions)

        val primary = roots.firstOrNull { it.isPrimary } ?: roots.firstOrNull()
        if (primary != null) StorageCompact(primary, others = roots - primary, onOpen = actions.onOpenPath)

        SectionTitle("Catégories")
        Column(Modifier.padding(horizontal = 12.dp)) {
            HOME_CATEGORIES.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { c -> CategoryTile(c, stats[c], Modifier.weight(1f)) { actions.onCategory(c) } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SectionTitle("Accès rapide")
        val tiles = favorites.map { path ->
            Shortcut(File(path).name.ifEmpty { path }, Icons.Filled.Star, path, Color(0xFFFFA000)) to true
        } + shortcuts.map { it to false }
        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (s, isFavorite) ->
                        ShortcutTile(
                            shortcut = s,
                            count = counts[s.path],
                            modifier = Modifier.weight(1f),
                            onClick = { actions.onOpenPath(s.path) },
                            onLongClick = if (isFavorite) ({ actions.onRemoveFavorite(s.path) }) else null,
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
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
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(32.dp))
        Text(
            "Fichiers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        )
        IconButton(onClick = actions.onSearch) { Icon(Icons.Filled.Search, "Rechercher") }
        IconButton(onClick = actions.onTrash) { Icon(Icons.Filled.Delete, "Corbeille") }
        IconButton(onClick = actions.onSettings) { Icon(Icons.Filled.Settings, "Paramètres") }
    }
}

@Composable
private fun StorageCompact(root: StorageRoot, others: List<StorageRoot>, onOpen: (String) -> Unit) {
    val fraction = percent(root)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Color(0xFF3F7DFF), Color(0xFF6A4FD8)))),
    ) {
        Row(
            Modifier
                .clickable { onOpen(root.path.absolutePath) }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                UsageRing(
                    fraction,
                    track = Color.White.copy(alpha = 0.22f),
                    progress = if (fraction > 0.9f) Color(0xFFFFB4AB) else Color.White,
                    modifier = Modifier.size(58.dp),
                )
                Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            Column(
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(root.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "${formatSize(root.used)} sur ${formatSize(root.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                Text(
                    "${formatSize(root.free)} libres",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        others.forEach { other ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(other.path.absolutePath) }
                    .background(Color.Black.copy(alpha = 0.12f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (other.isRemovable) Icons.Filled.SdCard else Icons.Filled.Usb, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(
                    other.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                )
                Text("${formatSize(other.free)} libres", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
            }
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
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(style.color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(24.dp))
        }
        Text(
            category.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            stat?.let { if (category == Category.LARGE) formatSize(it.bytes) else String.format(Locale.FRANCE, "%,d", it.count) } ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShortcutTile(
    shortcut: Shortcut,
    count: Int?,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(shortcut.color.copy(alpha = 0.16f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(shortcut.icon, null, tint = shortcut.color, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                shortcut.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (count) {
                    null -> " "
                    0 -> "Vide"
                    1 -> "1 élément"
                    else -> "$count éléments"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
