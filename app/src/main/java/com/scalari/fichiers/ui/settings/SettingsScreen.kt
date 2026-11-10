@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scalari.fichiers.BuildConfig
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.StoragePermission
import com.scalari.fichiers.data.ThemeMode
import com.scalari.fichiers.data.TrashRetention
import com.scalari.fichiers.data.VaultLockDelay
import com.scalari.fichiers.ui.components.RadioRow
import com.scalari.fichiers.update.UpdateManager
import com.scalari.fichiers.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    val repo = FileManagerApp.instance.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }
    var qrDialog by remember { mutableStateOf(false) }
    var lockDialog by remember { mutableStateOf(false) }
    var trashDialog by remember { mutableStateOf(false) }
    val update by UpdateManager.state.collectAsStateWithLifecycle()
    var autoUpdate by remember { mutableStateOf(UpdateManager.isAutoCheckEnabled(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                title = { Text("Paramètres") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Group("Apparence") {
                Item(Icons.Outlined.Palette, "Thème", settings.themeMode.label, first = true) { themeDialog = true }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchItem(Icons.Outlined.ColorLens, "Couleurs dynamiques", "Couleurs tirées du fond d'écran", settings.dynamicColor) {
                        scope.launch { repo.setDynamicColor(it) }
                    }
                }
            }

            Group("Affichage des fichiers") {
                SwitchItem(Icons.Outlined.GridView, "Affichage en grille", "Vignettes au lieu de la liste", settings.gridView, first = true) {
                    scope.launch { repo.setGridView(it) }
                }
                SwitchItem(Icons.Outlined.CreateNewFolder, "Dossiers en premier", "Avant les fichiers dans chaque dossier", settings.foldersFirst) {
                    scope.launch { repo.setFoldersFirst(it) }
                }
                SwitchItem(Icons.Outlined.Visibility, "Fichiers cachés", "Afficher ceux qui commencent par « . »", settings.showHidden) {
                    scope.launch { repo.setShowHidden(it) }
                }
                val custom = settings.folderViews.size
                Item(
                    Icons.AutoMirrored.Outlined.Sort,
                    "Tri par dossier",
                    if (custom == 0) "Trier → « Seulement pour ce dossier » dans l'explorateur"
                    else "$custom dossier(s) avec leur propre tri · toucher pour tout réinitialiser",
                    onClick = if (custom == 0) null else ({
                        scope.launch { repo.clearFolderViews() }
                        Toast.makeText(context, "Tous les dossiers suivent le tri général", Toast.LENGTH_SHORT).show()
                    }),
                )
            }

            Group("Suppression") {
                SwitchItem(Icons.Outlined.Delete, "Corbeille", "Les éléments supprimés restent restaurables", settings.useTrash, first = true) {
                    scope.launch { repo.setUseTrash(it) }
                }
                Item(Icons.Outlined.AutoDelete, "Vider automatiquement la corbeille", settings.trashRetention.let {
                    if (it.days == 0) it.label else "Éléments supprimés après ${it.label}"
                }) { trashDialog = true }
                SwitchItem(Icons.Outlined.CheckCircle, "Demander confirmation", "Avant chaque suppression", settings.confirmDelete) {
                    scope.launch { repo.setConfirmDelete(it) }
                }
            }

            Group("Coffre-fort") {
                Item(Icons.Outlined.Lock, "Verrouillage automatique", "Quand l'appli passe en arrière-plan : ${settings.vaultLockDelay.label.lowercase()}", first = true) {
                    lockDialog = true
                }
                SwitchItem(Icons.Outlined.Screenshot, "Captures d'écran", "Autoriser captures et enregistrement d'écran", settings.vaultScreenshots) {
                    scope.launch { repo.setVaultScreenshots(it) }
                }
            }

            Group("Mises à jour") {
                SwitchItem(Icons.Outlined.Autorenew, "Vérifier automatiquement", "Une fois par jour, sur GitHub", autoUpdate, first = true) {
                    autoUpdate = it
                    UpdateManager.setAutoCheck(context, it)
                }
                Item(
                    Icons.Outlined.SystemUpdate,
                    "Rechercher une mise à jour",
                    when (val s = update) {
                        UpdateState.Checking -> "Vérification…"
                        UpdateState.UpToDate -> "Vous avez la dernière version"
                        is UpdateState.Available -> "Version ${s.release.version} disponible : voir l'accueil"
                        is UpdateState.Downloading -> "Téléchargement… ${(s.progress * 100).toInt()} %"
                        is UpdateState.Error -> s.message
                        else -> "Dernière vérification au démarrage de l'appli"
                    },
                ) { scope.launch { UpdateManager.check(context, force = true, manual = true) } }
            }

            Group("Partager l'application") {
                Item(Icons.Outlined.Share, "Envoyer le lien de téléchargement", APP_DOWNLOAD_URL.removePrefix("https://"), first = true) {
                    ShareApp.share(context)
                }
                Item(Icons.Outlined.ContentCopy, "Copier le lien", null) {
                    ShareApp.copy(context)
                    Toast.makeText(context, "Lien copié", Toast.LENGTH_SHORT).show()
                }
                Item(Icons.Outlined.QrCode2, "Afficher le QR code", "À scanner avec un autre téléphone") { qrDialog = true }
            }

            Group("À propos") {
                val needsAccess = StoragePermission.needsAllFilesAccess()
                if (needsAccess) {
                    Item(Icons.Outlined.FolderOpen, "Accès à tous les fichiers", "Ouvrir les réglages Android", first = true) {
                        StoragePermission.openAllFilesSettings(context)
                    }
                }
                Item(Icons.Outlined.NewReleases, "Nouveautés", "Notes de version sur GitHub", first = !needsAccess) {
                    UpdateManager.openReleasePage(context, null)
                }
                Item(Icons.Outlined.Info, "Mes fichiers", "Version ${BuildConfig.VERSION_NAME}", onClick = null)
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (qrDialog) QrCodeDialog(onDismiss = { qrDialog = false })
    if (lockDialog) {
        ChoiceDialog(
            title = "Verrouiller le coffre",
            text = "Quand vous quittez l'appli (écran d'accueil, autre appli, écran éteint).",
            options = VaultLockDelay.entries,
            label = { it.label },
            selected = settings.vaultLockDelay,
            onDismiss = { lockDialog = false },
        ) { scope.launch { repo.setVaultLockDelay(it) } }
    }
    if (trashDialog) {
        ChoiceDialog(
            title = "Vider automatiquement",
            text = "Durée de conservation dans la corbeille avant suppression définitive.",
            options = TrashRetention.entries,
            label = { it.label },
            selected = settings.trashRetention,
            onDismiss = { trashDialog = false },
        ) { scope.launch { repo.setTrashRetention(it) } }
    }
    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Thème") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(mode.label, settings.themeMode == mode) {
                            themeDialog = false
                            scope.launch { repo.setThemeMode(mode) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    text: String,
    options: List<T>,
    label: (T) -> String,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                options.forEach { option ->
                    RadioRow(label(option), option == selected) {
                        onDismiss()
                        onSelect(option)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Carte de réglages : titre en petites capitales ; les éléments (sauf le premier) ont un trait fin au-dessus. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, top = 22.dp, bottom = 8.dp),
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun Separator(first: Boolean) {
    if (!first) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String?, first: Boolean = false, onClick: (() -> Unit)?) {
    Separator(first)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
    )
}

@Composable
private fun SwitchItem(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, first: Boolean = false, onChange: (Boolean) -> Unit) {
    Separator(first)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
        leadingContent = { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
