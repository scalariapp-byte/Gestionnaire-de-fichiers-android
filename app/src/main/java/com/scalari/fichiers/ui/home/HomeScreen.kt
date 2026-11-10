@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.home

import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.ui.unit.sp
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
import androidx.compose.material.icons.filled.PieChart
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
    val onAnalyze: () -> Unit = {},
    val onAnalyzeRoot: (String) -> Unit = {},
    val onInstallUpdate: (com.scalari.fichiers.update.Release) -> Unit = {},
    val onDismissUpdate: () -> Unit = {},
    val onOpenUpdatePage: (com.scalari.fichiers.update.Release?) -> Unit = {},
    val onDriveImport: () -> Unit = {},
    val onOpenDrive: () -> Unit = {},
)

@Composable
fun HomeScreen(navigator: AppNavigator) {
    val vm: HomeViewModel = viewModel()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val update by com.scalari.fichiers.update.UpdateManager.state.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refresh()
        scope.launch { com.scalari.fichiers.update.UpdateManager.check(context) }
    }

    val downloads = remember { File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS) }
    val driveImport = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            downloads.mkdirs()
            com.scalari.fichiers.data.OperationManager.importUris(uris, downloads)
        }
    }
    val actions = HomeActions(
        onDriveImport = { runCatching { driveImport.launch(arrayOf("*/*")) } },
        onOpenDrive = {
            val launch = context.packageManager.getLaunchIntentForPackage(com.scalari.fichiers.ui.GoogleDrive.PACKAGE)
            if (launch != null) context.startActivity(launch) else com.scalari.fichiers.ui.GoogleDrive.openPlayStore(context)
        },
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
        onAnalyze = { navigator.analyzer() },
        onAnalyzeRoot = { navigator.analyzer(it) },
        onInstallUpdate = { r -> scope.launch { com.scalari.fichiers.update.UpdateManager.downloadAndInstall(context, r) } },
        onDismissUpdate = { com.scalari.fichiers.update.UpdateManager.dismiss(context) },
        onOpenUpdatePage = { com.scalari.fichiers.update.UpdateManager.openReleasePage(context, it) },
    )

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        HomeContent(
            roots = roots,
            stats = stats,
            shortcuts = remember { defaultShortcuts() },
            favorites = settings.favorites,
            counts = counts,
            update = update,
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
    update: com.scalari.fichiers.update.UpdateState = com.scalari.fichiers.update.UpdateState.Idle,
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
        UpdateBanner(update, actions)

        val primary = roots.firstOrNull { it.isPrimary } ?: roots.firstOrNull()
        if (primary != null) StorageCompact(primary, others = roots - primary, onOpen = actions.onOpenPath, onAnalyze = actions.onAnalyze, onAnalyzeRoot = actions.onAnalyzeRoot)

        SectionTitle("Catégories")
        Column(Modifier.padding(horizontal = 12.dp)) {
            HOME_CATEGORIES.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { c -> CategoryTile(c, stats[c], Modifier.weight(1f)) { actions.onCategory(c) } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        SectionTitle("Google Drive")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShortcutTile(
                Shortcut("Importer", Icons.Filled.CloudDownload, "", Color(0xFF1A73E8)),
                count = null, subtitle = "Vers Téléchargements",
                modifier = Modifier.weight(1f), onClick = actions.onDriveImport, onLongClick = null,
            )
            ShortcutTile(
                Shortcut("Ouvrir Drive", Icons.Filled.Cloud, "", Color(0xFF0F9D58)),
                count = null, subtitle = "Appli Google Drive",
                modifier = Modifier.weight(1f), onClick = actions.onOpenDrive, onLongClick = null,
            )
        }
        Text(
            "Pour envoyer : dans l'explorateur, sélectionnez des fichiers → ⋮ → Envoyer vers Google Drive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Header(actions: HomeActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(30.dp))
        Text(
            "Mes fichiers",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        IconButton(onClick = actions.onSearch) { Icon(Icons.Outlined.Search, "Rechercher") }
        IconButton(onClick = actions.onTrash) { Icon(Icons.Outlined.Delete, "Corbeille") }
        IconButton(onClick = actions.onSettings) { Icon(Icons.Outlined.Settings, "Paramètres") }
    }
}
@Composable
private fun StorageCompact(
    root: StorageRoot,
    others: List<StorageRoot>,
    onOpen: (String) -> Unit,
    onAnalyze: () -> Unit,
    onAnalyzeRoot: (String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val fraction = percent(root)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column {
            Row(
                Modifier
                    .clickable { onOpen(root.path.absolutePath) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UsageRing(
                        fraction,
                        track = scheme.surfaceContainerHigh,
                        progress = if (fraction > 0.9f) scheme.error else scheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                    Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(root.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatSize(root.used)} sur ${formatSize(root.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        "${formatSize(root.free)} libres",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onAnalyze,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Outlined.PieChart, null, modifier = Modifier.size(16.dp))
                    Text("Analyser", modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            others.forEach { other ->
                androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(other.path.absolutePath) }
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (other.isRemovable) Icons.Outlined.SdCard else Icons.Outlined.Usb, null,
                        tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
                    )
                    Column(
                        Modifier
                            .padding(start = 14.dp)
                            .weight(1f)
                    ) {
                        Text(other.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatSize(other.free)} libres sur ${formatSize(other.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onAnalyzeRoot(other.path.absolutePath) }) {
                        Icon(Icons.Outlined.PieChart, "Analyser ${other.name}", tint = scheme.onSurfaceVariant)
                    }
                }
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
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
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
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(style.color.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(22.dp))
        }
        Text(
            category.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
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
    subtitle: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(shortcut.color.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(shortcut.icon, null, tint = shortcut.color, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(shortcut.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle ?: when (count) {
                    null -> " "
                    0 -> "Vide"
                    1 -> "1 élément"
                    else -> "$count éléments"
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
@Composable
private fun UpdateBanner(state: com.scalari.fichiers.update.UpdateState, actions: HomeActions) {
    val (release, progress, error) = when (state) {
        is com.scalari.fichiers.update.UpdateState.Available -> Triple(state.release, null, null)
        is com.scalari.fichiers.update.UpdateState.Downloading -> Triple(state.release, state.progress, null)
        is com.scalari.fichiers.update.UpdateState.Error -> Triple(state.release ?: return, null, state.message)
        else -> return
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "Mise à jour disponible : ${release.version}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        error ?: if (progress != null) "Téléchargement… ${(progress * 100).toInt()} %" else "S'installe par-dessus la version actuelle",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (progress == null) {
                    if (error != null) {
                        androidx.compose.material3.TextButton(onClick = { actions.onOpenUpdatePage(release) }) { Text("GitHub") }
                        androidx.compose.material3.Button(onClick = { actions.onInstallUpdate(release) }) { Text("Réessayer") }
                    } else {
                        androidx.compose.material3.TextButton(onClick = actions.onDismissUpdate) { Text("Plus tard") }
                        androidx.compose.material3.Button(onClick = { actions.onInstallUpdate(release) }) { Text("Installer") }
                    }
                }
            }
            if (progress != null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, end = 8.dp),
                )
            }
        }
    }
}
