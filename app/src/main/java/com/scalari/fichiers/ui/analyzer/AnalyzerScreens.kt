@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.analyzer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.Category
import com.scalari.fichiers.data.DuplicateGroup
import com.scalari.fichiers.data.FileTypes
import com.scalari.fichiers.data.MediaQueries
import com.scalari.fichiers.data.OperationManager
import com.scalari.fichiers.data.SizedEntry
import com.scalari.fichiers.data.StorageAnalyzer
import com.scalari.fichiers.data.StorageRoot
import com.scalari.fichiers.data.StorageRoots
import com.scalari.fichiers.data.formatDate
import com.scalari.fichiers.data.formatSize
import com.scalari.fichiers.ui.AppNavigator
import com.scalari.fichiers.ui.LocalSnackbarHostState
import com.scalari.fichiers.ui.components.ConfirmDialog
import com.scalari.fichiers.ui.components.icon
import com.scalari.fichiers.ui.components.tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ======================================================================= Vue d'ensemble

data class UsageSlice(val label: String, val bytes: Long, val color: Color)

@Composable
private fun AnalyzerScaffold(title: String, subtitle: String? = null, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val snackbar = LocalSnackbarHostState.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
fun AnalyzerScreen(navigator: AppNavigator) {
    val context = LocalContext.current
    val showHidden = FileManagerApp.instance.settingsState.collectAsStateWithLifecycle().value.showHidden
    var root by remember { mutableStateOf<StorageRoot?>(null) }
    var slices by remember { mutableStateOf<List<UsageSlice>>(emptyList()) }
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { StorageRoots.list(context).firstOrNull { it.isPrimary } }
        root = r
        val cats = listOf(
            Triple(Category.IMAGES, "Images", Color(0xFF2E9E5B)),
            Triple(Category.VIDEOS, "Vidéos", Color(0xFFE5484D)),
            Triple(Category.AUDIO, "Audio", Color(0xFF8E4EC6)),
            Triple(Category.DOCUMENTS, "Documents", Color(0xFF3E7BFA)),
            Triple(Category.APKS, "APK", Color(0xFF5BA33B)),
            Triple(Category.ARCHIVES, "Archives", Color(0xFFB0793F)),
        )
        val list = cats.map { (c, label, color) -> UsageSlice(label, MediaQueries.stat(context, c, showHidden).bytes, color) }
        val known = list.sumOf { it.bytes }
        slices = list + UsageSlice("Système et autres", ((r?.used ?: 0) - known).coerceAtLeast(0), Color(0xFF9AA0AE))
    }
    AnalyzerScaffold("Analyse du stockage", onBack = navigator::back) { padding ->
        AnalyzerContent(
            root = root,
            slices = slices,
            modifier = Modifier.padding(padding),
            onFolders = { navigator.analyzerFolders(root?.path?.absolutePath) },
            onDuplicates = navigator::analyzerDuplicates,
            onApks = navigator::analyzerApks,
            onEmpty = navigator::analyzerEmpty,
            onLarge = { navigator.category(Category.LARGE) },
            onBootApps = navigator::bootApps,
        )
    }
}

/** Vue d'ensemble, sans état (testable en capture). */
@Composable
fun AnalyzerContent(
    root: StorageRoot?,
    slices: List<UsageSlice>,
    modifier: Modifier = Modifier,
    onFolders: () -> Unit = {},
    onDuplicates: () -> Unit = {},
    onApks: () -> Unit = {},
    onEmpty: () -> Unit = {},
    onLarge: () -> Unit = {},
    onBootApps: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(18.dp)) {
                if (root == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text(root.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatSize(root.used)} utilisés sur ${formatSize(root.total)} · ${formatSize(root.free)} libres",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    // Barre empilée : répartition de l'espace occupé, puis l'espace libre.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        val total = root.total.coerceAtLeast(1).toFloat()
                        slices.filter { it.bytes > 0 }.forEach { s ->
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .weight((s.bytes / total).coerceAtLeast(0.004f))
                                    .background(s.color)
                            )
                        }
                        val free = (root.free / total).coerceAtLeast(0.004f)
                        Spacer(Modifier.weight(free))
                    }
                    Spacer(Modifier.height(14.dp))
                    slices.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            row.forEach { s ->
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(s.color, CircleShape))
                                    Text(
                                        "${s.label}  ",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(start = 6.dp),
                                        maxLines = 1,
                                    )
                                    Text(formatSize(s.bytes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Text("Faire de la place", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        ToolRow(Icons.Filled.PieChart, Color(0xFF6A4CFF), "Dossiers les plus lourds", "Ce qui occupe le plus d'espace", onFolders)
        ToolRow(Icons.Filled.ContentCopy, Color(0xFFE5484D), "Fichiers en double", "Copies identiques, octet pour octet", onDuplicates)
        ToolRow(Icons.Filled.Storage, Color(0xFFF07A1A), "Gros fichiers", "Fichiers de plus de 50 Mo", onLarge)
        ToolRow(Icons.Filled.Android, Color(0xFF5BA33B), "APK déjà installés", "Fichiers d'installation devenus inutiles", onApks)
        ToolRow(Icons.Filled.FolderOff, Color(0xFF607D8B), "Dossiers vides", "Dossiers sans aucun contenu", onEmpty)
        Text("Système", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        ToolRow(Icons.Filled.PowerSettingsNew, Color(0xFF0E9AA7), "Démarrage automatique", "Applis lancées au démarrage du téléphone", onBootApps)
    }
}

@Composable
private fun ToolRow(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

// ======================================================================= Dossiers les plus lourds

@Composable
fun FolderSizesScreen(path: String?, navigator: AppNavigator) {
    val dir = remember(path) { path?.let(::File) ?: StorageRoots.primary() }
    val showHidden = FileManagerApp.instance.settingsState.collectAsStateWithLifecycle().value.showHidden
    var entries by remember(path) { mutableStateOf<List<SizedEntry>>(emptyList()) }
    var done by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        StorageAnalyzer.childrenSizes(dir, showHidden) { entries = it }
        done = true
    }
    val total = entries.sumOf { it.size }
    AnalyzerScaffold(dir.name.ifEmpty { "/" }, if (done) formatSize(total) else "Calcul en cours…", navigator::back) { padding ->
        Column(Modifier.padding(padding)) {
            if (!done) LinearProgressIndicator(Modifier.fillMaxWidth())
            FolderSizesList(
                entries = entries,
                onOpen = { e -> if (e.isDirectory) navigator.analyzerFolders(e.file.absolutePath) else navigator.browser(e.file.parent) },
                onShowInBrowser = { e -> navigator.browser(if (e.isDirectory) e.file.absolutePath else e.file.parent) },
            )
        }
    }
}

@Composable
fun FolderSizesList(entries: List<SizedEntry>, onOpen: (SizedEntry) -> Unit, onShowInBrowser: (SizedEntry) -> Unit) {
    val max = entries.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(entries, key = { it.file.absolutePath }) { e ->
            val type = FileTypes.of(e.file.name, e.isDirectory)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(e) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(type.tint().copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(type.icon(), null, tint = type.tint(), modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text(formatSize(e.size), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((e.size.toFloat() / max).coerceIn(0.01f, 1f))
                                .background(type.tint(), CircleShape)
                        )
                    }
                    if (e.isDirectory) {
                        Text(
                            "${e.files} fichier${if (e.files > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                IconButton(onClick = { onShowInBrowser(e) }) {
                    Icon(Icons.Filled.FolderOpen, "Ouvrir dans l'explorateur", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ======================================================================= Listes de nettoyage

data class CleanupItem(val file: File, val title: String, val subtitle: String, val size: Long, val group: Int = -1, val groupLabel: String? = null)

/**
 * Liste à cocher + bouton de suppression (doublons, APK, dossiers vides).
 * Les éléments [preselected] sont cochés au départ.
 */
@Composable
private fun CleanupScreen(
    title: String,
    navigator: AppNavigator,
    load: suspend (onProgress: (String) -> Unit) -> Pair<List<CleanupItem>, Set<String>>,
    emptyText: String,
    /** Après une suppression : retire de la liste ce qui n'existe plus, sans tout réanalyser. */
    prune: (List<CleanupItem>) -> List<CleanupItem> = { list -> list.filter { it.file.exists() } },
) {
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    var items by remember { mutableStateOf<List<CleanupItem>?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var progress by remember { mutableStateOf("Analyse en cours…") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val (list, pre) = load { progress = it }
        items = list
        selected = pre
        OperationManager.changes.collect {
            val current = items ?: return@collect
            val remaining = withContext(Dispatchers.IO) { prune(current) }
            val paths = remaining.mapTo(HashSet()) { it.file.absolutePath }
            items = remaining
            selected = selected.filterTo(HashSet()) { it in paths }
        }
    }
    val list = items
    val selectedBytes = list.orEmpty().filter { it.file.absolutePath in selected }.sumOf { it.size }

    AnalyzerScaffold(
        title,
        list?.let { if (it.isEmpty()) null else "${it.size} élément(s)" },
        navigator::back,
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                list == null -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text(progress, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                list.isEmpty() -> Text(
                    emptyText,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Column(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                        var lastGroup = Int.MIN_VALUE
                        list.forEach { item ->
                            if (item.group != -1 && item.group != lastGroup) {
                                lastGroup = item.group
                                item(key = "g${item.group}") {
                                    Text(
                                        item.groupLabel.orEmpty(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                                    )
                                }
                            }
                            item(key = item.file.absolutePath) {
                                val path = item.file.absolutePath
                                val type = FileTypes.of(item.file.name, item.file.isDirectory)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { selected = if (path in selected) selected - path else selected + path }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = path in selected, onCheckedChange = { selected = if (it) selected + path else selected - path })
                                    Icon(type.icon(), null, tint = type.tint(), modifier = Modifier.size(22.dp))
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            item.subtitle, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Button(
                            onClick = { confirm = true },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(16.dp),
                        ) {
                            Text(
                                if (selected.isEmpty()) "Rien de sélectionné"
                                else "Supprimer ${selected.size} élément(s) · ${formatSize(selectedBytes)}"
                            )
                        }
                    }
                }
            }
        }
    }
    if (confirm) {
        val trash = settings.useTrash
        ConfirmDialog(
            title = if (trash) "Mettre à la corbeille ?" else "Supprimer définitivement ?",
            text = "${selected.size} élément(s), ${formatSize(selectedBytes)}." +
                if (trash) " Vous pourrez les restaurer depuis la corbeille." else " Action irréversible.",
            confirmLabel = if (trash) "Corbeille" else "Supprimer",
            destructive = !trash,
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                OperationManager.delete(selected.map(::File), trash)
            },
        )
    }
}

@Composable
fun DuplicatesScreen(navigator: AppNavigator) {
    val context = LocalContext.current
    val showHidden = FileManagerApp.instance.settingsState.value.showHidden
    CleanupScreen(
        title = "Fichiers en double",
        navigator = navigator,
        emptyText = "Aucun doublon trouvé (fichiers de plus de 100 Ko).",
        load = { onProgress ->
            val groups: List<DuplicateGroup> = StorageAnalyzer.duplicates(context, StorageRoots.primary(), showHidden) { step, done, total ->
                onProgress(if (total > 0) "$step… $done / $total" else if (done > 0) "$step… $done fichiers" else "$step…")
            }
            val items = groups.flatMapIndexed { gi, g ->
                g.files.mapIndexed { i, f ->
                    CleanupItem(
                        file = f,
                        title = f.name,
                        subtitle = (if (i == 0) "Le plus ancien · " else "") + "${f.parent} · ${formatDate(f.lastModified())}",
                        size = g.size,
                        group = gi,
                        groupLabel = "${g.files.size} copies de ${formatSize(g.size)} · ${formatSize(g.wasted)} récupérables",
                    )
                }
            }
            // Pré-sélection : toutes les copies sauf la plus ancienne de chaque groupe.
            val pre = groups.flatMap { it.files.drop(1) }.mapTo(HashSet()) { it.absolutePath }
            items to pre
        },
        // Après suppression : un groupe réduit à un seul fichier n'est plus un doublon.
        prune = { list -> list.filter { it.file.exists() }.groupBy { it.group }.values.filter { it.size > 1 }.flatten() },
    )
}

@Composable
fun InstalledApksScreen(navigator: AppNavigator) {
    val context = LocalContext.current
    val showHidden = FileManagerApp.instance.settingsState.value.showHidden
    CleanupScreen(
        title = "APK déjà installés",
        navigator = navigator,
        emptyText = "Aucun APK inutile : aucune application correspondante n'est installée dans la même version.",
        load = { onProgress ->
            onProgress("Vérification des APK…")
            val apks = StorageAnalyzer.installedApks(context, showHidden)
            val items = apks.map { CleanupItem(it.file, it.label, "${formatSize(it.size)} · ${it.file.parent}", it.size) }
            items to items.mapTo(HashSet()) { it.file.absolutePath }
        },
    )
}

@Composable
fun EmptyFoldersScreen(navigator: AppNavigator) {
    val showHidden = FileManagerApp.instance.settingsState.value.showHidden
    CleanupScreen(
        title = "Dossiers vides",
        navigator = navigator,
        emptyText = "Aucun dossier vide.",
        load = { onProgress ->
            onProgress("Recherche des dossiers vides…")
            val dirs = StorageAnalyzer.emptyFolders(StorageRoots.primary(), showHidden)
            val root = StorageRoots.primary().absolutePath
            val items = dirs.map { CleanupItem(it, it.name, it.parent?.removePrefix(root)?.ifEmpty { "/" } ?: "", 0) }
            items to items.mapTo(HashSet()) { it.file.absolutePath }
        },
    )
}

