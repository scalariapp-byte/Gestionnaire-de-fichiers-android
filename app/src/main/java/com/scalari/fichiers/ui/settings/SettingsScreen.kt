@file:OptIn(ExperimentalMaterial3Api::class)

package com.scalari.fichiers.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scalari.fichiers.BuildConfig
import com.scalari.fichiers.FileManagerApp
import com.scalari.fichiers.data.StoragePermission
import com.scalari.fichiers.data.ThemeMode
import com.scalari.fichiers.ui.components.RadioRow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings by FileManagerApp.instance.settingsState.collectAsStateWithLifecycle()
    val repo = FileManagerApp.instance.settings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }

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
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { themeDialog = true },
                headlineContent = { Text("Thème") },
                supportingContent = { Text(settings.themeMode.label) },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchItem("Couleurs dynamiques", "Couleurs tirées du fond d'écran", settings.dynamicColor) {
                    scope.launch { repo.setDynamicColor(it) }
                }
            }
            SwitchItem("Affichage en grille", "Vignettes au lieu de la liste", settings.gridView) {
                scope.launch { repo.setGridView(it) }
            }

            }
            Group("Fichiers") {
            SwitchItem("Afficher les fichiers cachés", "Fichiers et dossiers commençant par « . »", settings.showHidden) {
                scope.launch { repo.setShowHidden(it) }
            }
            SwitchItem("Dossiers en premier", null, settings.foldersFirst) {
                scope.launch { repo.setFoldersFirst(it) }
            }
            SwitchItem("Utiliser la corbeille", "Les éléments supprimés restent restaurables", settings.useTrash) {
                scope.launch { repo.setUseTrash(it) }
            }
            SwitchItem("Confirmer avant suppression", null, settings.confirmDelete) {
                scope.launch { repo.setConfirmDelete(it) }
            }
            SwitchItem("Captures d'écran dans le coffre", "Autorise captures et enregistrement d'écran du coffre-fort", settings.vaultScreenshots) {
                scope.launch { repo.setVaultScreenshots(it) }
            }

            }
            Group("Partager l'application") {
                var qr by remember { mutableStateOf(false) }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { ShareApp.share(context) },
                    headlineContent = { Text("Envoyer le lien de téléchargement") },
                    supportingContent = { Text(APP_DOWNLOAD_URL.removePrefix("https://"), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        ShareApp.copy(context)
                        android.widget.Toast.makeText(context, "Lien copié", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    headlineContent = { Text("Copier le lien") },
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { qr = true },
                    headlineContent = { Text("Afficher le QR code") },
                    supportingContent = { Text("À scanner avec un autre téléphone") },
                )
                if (qr) QrCodeDialog(onDismiss = { qr = false })
            }
            Group("Mises à jour") {
                val update by com.scalari.fichiers.update.UpdateManager.state.collectAsStateWithLifecycle()
                var auto by remember { mutableStateOf(com.scalari.fichiers.update.UpdateManager.isAutoCheckEnabled(context)) }
                SwitchItem("Vérifier automatiquement", "Une fois par jour, sur github.com", auto) {
                    auto = it
                    com.scalari.fichiers.update.UpdateManager.setAutoCheck(context, it)
                }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        scope.launch { com.scalari.fichiers.update.UpdateManager.check(context, force = true, manual = true) }
                    },
                    headlineContent = { Text("Rechercher une mise à jour") },
                    supportingContent = {
                        Text(
                            when (val s = update) {
                                com.scalari.fichiers.update.UpdateState.Checking -> "Vérification…"
                                com.scalari.fichiers.update.UpdateState.UpToDate -> "Vous avez la dernière version (${BuildConfig.VERSION_NAME})"
                                is com.scalari.fichiers.update.UpdateState.Available -> "Version ${s.release.version} disponible : voir l'accueil"
                                is com.scalari.fichiers.update.UpdateState.Downloading -> "Téléchargement… ${(s.progress * 100).toInt()} %"
                                is com.scalari.fichiers.update.UpdateState.Error -> s.message
                                else -> "Version installée : ${BuildConfig.VERSION_NAME}"
                            }
                        )
                    },
                )
            }
            Group("Système") {
            if (StoragePermission.needsAllFilesAccess()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { StoragePermission.openAllFilesSettings(context) },
                    headlineContent = { Text("Autorisation d'accès aux fichiers") },
                    supportingContent = { Text("Ouvrir les réglages Android") },
                )
            }
            // Raccourci vers Isolateur (appli liée : blocage du démarrage auto, applis isolées).
            val isoIntent = remember { context.packageManager.getLaunchIntentForPackage("com.isolapp.manager") }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    val i = isoIntent ?: android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/scalariapp-byte/App-isol-/releases/download/latest/Isolateur.apk"),
                    )
                    runCatching { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                headlineContent = { Text("Isolateur") },
                supportingContent = {
                    Text(if (isoIntent != null) "Ouvrir Isolateur (espace isolé, démarrage auto)" else "Non installé · Télécharger")
                },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Version") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
            }
            Spacer(Modifier.height(24.dp))
        }
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
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 8.dp),
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun SwitchItem(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
